# 学习通 Cookie 保持审查（2026-10-22）

基线：`upstream/develop`，`7f7d9c53`。本补充审查只修改 `data/chaoxing` 和本报告，没有修改共享 CAS 协调或扩大凭据存储架构。

## 确认的源码缺陷与修复

1. 原 `PersistentCookieJar.persist()` 只存 `name|domain=value`。冷启动恢复时所有 Cookie 都变成 `/` 路径、非 Secure、非 HttpOnly、非 hostOnly、永不过期。新 `ChaoxingCookieCodec.kt` 使用已有 `kotlinx.serialization.json` 依赖保存 version 2 数据，完整保存 name、value、domain、path、expiresAt、secure、httpOnly、hostOnly、persistent、sameSite。已通过本机缓存的 OkHttp 5.1 API 核对 `sameSite` 和 Builder 支持；没有新增依赖。
2. 原缓存按接收响应的 host 分桶，却只在桶内替换 name/domain/path。相同域 Cookie 可以在不同子域响应桶里重复；来自另一子域的删除 Cookie 无法删除旧桶条目。现在统一以 domain/path/name 为键合并，过期或删除 Cookie 移除既有条目，读请求时清理到期条目并写回；同名不同路径保留，发送时更具体路径优先。
3. 原 save/load/cache 遍历没有同步保护。现在 save、load、clear、快照和快照恢复使用同一实例锁，缓存变更与持久化串行执行，避免并发修改或旧快照写回覆盖新缓存。
4. 当前代码不存在 `restoreCookieAfterLogin`。实际的二次属性丢失路径在 `ChaoxingApi.silentRelogin()`：备份 `Store.getCookie()` 的扁平 header，清空 jar，失败后 `restoreFromString()` 再将所有 Cookie 造为 `.chaoxing.com`、根路径、永不过期。现在备份完整 Cookie 对象快照，失败/取消时按快照恢复并剔除刷新期间已经过期的 Cookie。取消继续向上传递。`cookieString()` 只用于现有 `Store` 状态标记，不再反向构建 Cookie。

这些缺陷可从源码确认，但没有将其直接定为某次生产学习通掉登录的唯一原因，也没有把 Cookie 存在当成服务端会话有效的证明。

## 兼容边界

- 原 JSON 仍可读取，首次加载重写为 version 2；原格式中值包含 `=` 的情况保持完整。
- 原格式已经丢失 path、secure、httpOnly、hostOnly、expiry，迁移无法凭空恢复。迁移只采用旧格式的 domain 与根路径兼容默认，不添加人为持久期限；未知生存期 Cookie 保持 session 标志。之后服务器发来的新 Cookie 会完整替换旧值。
- 为保持应用既有“保持登录”语义，session Cookie 仍可随 jar 持久化，但 codec 保留其 `persistent=false`，不把它改为有显式期限的 Cookie。已存在的真实到期/删除属性不会在 version 2 恢复时被延长。
- 不支持的未来版本和损坏 JSON 不作为旧 header 猜测恢复。没有更改 Cookie 的磁盘加密策略；既有私有 SharedPreferences 存储方式保持原样。

## 回归与验证状态

新增 `ChaoxingCookieCodecTest` 4 个用例、`PersistentCookieJarTest` 9 个用例，覆盖完整属性 round trip、Secure/hostOnly/path 匹配、同名不同路径、跨响应 host 覆盖和删除、精确过期、完整快照回滚、旧数据迁移、清理、未知版本，以及 4 线程并发读写保留 120 个唯一 Cookie。

本子任务按主审查安排没有独立执行 Gradle。`git diff --check -- data/chaoxing` 通过；源码搜索确认学习通 API 不再调用 `restoreFromString` 或从 `Store.getCookie()` 还原 jar。当前为 **IMPLEMENTED / UNVERIFIED**，需要主审查统一执行：

```powershell
adb devices
.\gradlew.bat :data:chaoxing:testDebugUnitTest :data:chaoxing:compileDebugKotlin
```

仍需设备上的学习通登录、冷启动、临时断网后恢复与服务端 Cookie 到期检查。实际测试结果以主审查最终验证记录为准。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
