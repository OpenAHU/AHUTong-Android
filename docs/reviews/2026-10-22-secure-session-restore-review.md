# 加密身份冷启动恢复审查（2026-10-22）

基线：上游 `develop`，`7f7d9c53`。本报告记录静态代码可达的问题及修复。**未取得本次用户故障对应的设备 Keystore 异常，因此不宣称已经确认现场故障由 Keystore 引起。**

## 可证实的问题

1. `SecureStorage.getString` 捕获任意解密异常后删除对应密文；若是短暂 Keystore/Provider 故障，原本可恢复的身份、密码或会话会永久丢失。读取还调用 `getOrCreateKey`，可能在已有密文读取期间创建密钥。
2. 该异常被折叠为 `null` 后，`SecureBoxStoreCore` 无法区分“未存储”与“当前不可读”，继续查 Rust KV / 旧 MMKV，可能用旧副本覆盖原密文。
3. `SessionStore.currentUser` 又将读取失败的 `null` 设为已初始化的用户缓存，本进程余下时间都按匿名处理，即使 Keystore 已恢复也不重读。
4. `MainActivity`、Compose 路由门禁等同步路径会读取身份，因此直接让上述读取抛异常会增加启动主线程崩溃风险。Splash 原先只有已登录 / 未登录两个分支，也会把读取失败导航到登录页。
5. 自动续期若把读取不到身份或密码当作凭据被拒绝，会进一步将暂时存储问题记为会话过期或停止自动重试；需由续期调用方识别暂时存储异常。

## 修复行为

- 加入 `SecureReadResult.Value / Missing / Unavailable`。读取不删除密文、不替换或重置密钥；兼容的同步 `getString` 仍返回 nullable，不向 UI 抛异常。
- `SecureStorage.readString` 只查现有密钥。明确密钥永久失效、密钥缺失、GCM 认证失败或密文格式损坏均不可认证，并保持 fail-closed；这些情况与可重试故障区分，均不读取旧副本。原先显式写入遇到永久密钥失效的重置路径保留。
- `SecureBoxStore` 的 typed 读取传播 `Unavailable`，禁止原生和明文回退；只有 `Missing` 才允许既有迁移。兼容的同步 getter 仍安全返回 null。
- `CurrentUserRestore` 只缓存成功读取到的用户或真正缺失。不可用不负缓存；非法 JSON 或空学号不能被当作有效身份。`SessionStore.currentUser` 保持同步安全 nullable。
- Splash 在 IO 读取身份，可重试故障会延迟 150 / 350 ms 再尝试两次。仍失败时留在恢复界面显示重试按钮，不自动退出或导航登录；明确不可恢复时提供重新登录操作。
- `MainActivity.onSessionStorageReady` 在原生服务就绪后幂等执行原账户启动任务。稍后点击重试恢复成功，也会加载首页数据、课表配置和课程，不因首次启动检查跳过而永久停在空内容。
- 快速切换账号列表同样区分不可读与缺失：展示列表和读取预填密码保持同步安全；保存或移除账号时遇到不可读则终止，保留整份旧密文，防止暂时读失败被当空列表覆盖。

## 接口与集成影响

- `:core:auth` 新增 `SecureReadResult`、`SecureStorageUnavailableException : IOException` 及安全读取接口。
- `CredentialVault` 新增有默认实现的 `wisdomPasswordForRefresh()`；普通 `wisdomPassword()` 保持同步安全 nullable。生产 `ForRefresh` 对暂时不可用抛出有类型的异常，供续期层归为暂时故障，永久损坏仍不提供密码。
- `SessionStore.currentUserForRefresh()` 使用相同规则；`restoreCurrentUser()` 供冷启区分三态，`isUserRestoreUnavailable()` 供路由门禁将暂时未能恢复的受保护路由送回 Splash。
- 自动续期、路由门禁、邮箱的对应调用方由主代理同步修改；需要整体测试这些传播点，不能只验证存储 getter。
- 不引入新依赖、权限、明文凭据或敏感日志，不更改密文格式与存储键名。

## 回归与验证边界

- 新增 `CurrentUserRestoreTest`：暂时故障后成功恢复、不缓存失败为缺失、真正缺失可缓存并由显式登录替换、损坏身份 fail-closed 后仍可在存储修复时重新读取。
- 扩展 `SecureBoxStoreCoreTest`：暂不可用时保留可信副本、不读取或删除旧副本、不走明文 fallback；恢复后使用可信值并完成旧副本清理；永久损坏也禁止旧身份回退。
- 扩展 `SavedAccountStoreTest`：读取故障时 UI 不抛异常，保存 / 移除操作不能覆盖或删除原密文；存储恢复后原先多个账号和密码仍可读取。
- 静态 `git diff --check` 已通过。Gradle、ADB 和设备验证由主代理统一执行；这些测试结果和真实设备 Keystore 恢复行为尚需主代理记录，不将静态路径或 fake 回归等同于现场故障重现。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
