# 登录保持、功能与界面审查（2026-10-22）

初次审查基线为 `upstream/develop`：`7f7d9c53a0acbdb806ba3aa04e507768afbd6381`（3.4.5 / 304009）。用户要求交付后，再次联网拉取并接到最新 `b6961a3aee85da67cfc5300dd8ed829da2a123a2`，保留上游食堂、隐私版本等更新。工作分支：`p/MuxYang/fix/session-persistence-review`，隔离工作区：`build/codex-session-review`。原检出分支及内容保留。

本轮由四路并行检查认证与存储、电控、设置与成绩、课表与主页，再交叉检查插件、后台提醒和更新。覆盖主要数据流与错误路径，并执行全模块单元测试；不将这类审查等同于每个文件、每种设备和所有服务场景均无问题。

## 登录保持与各服务恢复

| 问题 | 证据与修改 | 验证边界 |
| --- | --- | --- |
| 解密失败会删除持久身份/密码，且空身份被进程缓存 | `SecureStorage.getString()` 原先对所有异常删除密文；`SessionStore` 随后缓存 null。改为 Value / Missing / Unavailable，暂时不可读不删、不迁移旧副本、不缓存匿名；冷启可重试，恢复后补齐首页加载 | 故障恢复、禁止回退、坏身份回归；没有现场 Keystore 日志，不能断言是用户全部自动退出的唯一根因 |
| 暂时读不到凭据会被判为凭据拒绝 | 自动恢复专用 `wisdomPasswordForRefresh()` / `currentUserForRefresh()` 区分暂时故障；会话和校园卡恢复将其归为 TRANSIENT，保留登录状态与后续重试资格 | 会话契约覆盖读取身份/凭据失败，不冒充真实密码错误 |
| 快速账号列表读取失败后覆盖旧列表 | 修改前 nullable read 可把不可用当空列表，随后 save/forget 覆盖原记录。不可用时阻止修改；UI 读取仍安全 | 回归覆盖故障、禁止覆盖/删除、恢复后重新读取 |
| 邮箱 CAS 刷新成功仍报失败 | 原代码推进 CENTRAL_CAS，却用默认 ACADEMIC 检查/提交代号。`PortalSessionStamp` 全程使用 CENTRAL_CAS，并校验身份；可复用校园卡已完成的 CAS 恢复 | 覆盖独立教务刷新、成功提交、并发 CAS 复用与退出后的旧请求 |
| 学习通重启及重登回滚丢 Cookie 属性 | 原持久化仅保存 name/domain/value，恢复时丢 path/secure/hostOnly/expiry 等；重登失败也从扁平 header 重建。v2 保留完整属性，按真实 Cookie 身份合并、删除、过期与排序；失败从完整快照回滚 | 13 项 codec / jar 回归；旧格式已丢失的属性无法凭空恢复；未使用提供的校方账号尝试学习通密码登录 |

Android Keystore 本身区分暂时故障与永久故障，不能把所有读取异常视为丢失数据，参考 [Android KeyStoreException 文档](https://developer.android.com/reference/android/security/KeyStoreException)。本次仍对损坏或不可解密数据 fail-closed，不降级明文。

详见 [安全会话存储](2026-10-22-secure-session-restore-review.md)、[学习通会话](2026-10-22-chaoxing-session-review.md)。

## 业务与 UI

| 范围 | 已修改行为 |
| --- | --- |
| 电控加载 | HTTP 错误不再展示 Retrofit 的 `Response.error()` 固定文案；401 刷新失败返回的原响应体保持可读；取消不再被显示为网络错误，旧请求不能清掉新请求加载状态 |
| 电量不足 | 已知余额为零或负数时，无须等待历史接口成功即可生成提醒快照；正余额仍要求完整历史，不拿失败或残缺历史作预测 |
| 电控 UI | 电控入口、校区、楼栋、楼层和房间选项成为同一卡片内的平面行，去除各行独立描边；`AppSelectField.grouped` 默认 false，仅电控启用，完整传递各主题与组件槽，保留级联、加载与重试语义 |
| 课程表 / 首页课程 | 研究生使用研究生课表来源；明日课程遵循精确排课周次，并处理周日跨周；旧账号、清理前或旧 revision 的结果不能覆盖新课表状态 |
| 成绩 | 补齐汇总和状态卡横向边距，缩小列表及卡内间距；手动和预设切换专业均清除旧排名、取消旧请求并加载新排名；区分加载、错误与空数据，总绩点直接从排名数据计算 |
| 设置 | 删除切换账号和抢先体验的副标题；加入状态使用右箭头左侧的灰色次要文字；切换账号与实验入口分别使用切换/实验主题图标。Miuix 图标已按实际 0.7.2 AAR 核对，未新增图标依赖 |

详见 [电控](2026-10-22-recharge-review.md)、[课表与主页](2026-10-22-schedule-main-review.md)、[设置与成绩](2026-10-22-settings-grade-review.md)。

主页切换未发现“必须等待网络成功才导航”的直接逻辑；一级动画固定约 220 ms。主线程磁盘读取、玻璃采样与设备帧耗时仍需实际性能追踪，不能根据静态代码宣称已消除卡顿。跨日刷新已存在 `onResume/checkDayRollover` 与前台守望，复核后保留原有实现。

### 追加：“暂无”的实际原因

测试账号的排名 HTTP 200，未被重定向至登录页；HTML 中存在排名模型且解析成功。同页稳定后五项汇总均有值，所以此次截图不能解释为接口没有数据。成绩列表与排名分别异步加载，旧 UI 将汇总的初始状态直接显示为“暂无”；总绩点还经过独立 `snapshotFlow` 再赋值，有额外派生状态传播延迟。

新增可控延迟回归先捕获了“排名已返回，总绩点仍为暂无”。保持严格断言，将总绩点改为从排名数据直接计算的 getter 后通过。初次加载显示进度和“加载中”，失败显示具体错误并保留已有同专业排名；不修改真实接口或 GPA 算法。

## Sentry 实查

通过用户根目录的 Auth 调用只读 API，查询 `openahu/ahutong-android`，环境 `release`、窗口 `14d`、`is:unresolved`，最多 50 条。过滤 DAU 统计事件，事件仅提取版本、时间与所需栈帧；未保存用户身份、Cookie、票据或完整原始事件。

- [AHUTONG-ANDROID-1K](https://openahu.sentry.io/issues/7805067037/)：3.4.4+304008，2026-10-22 的 `PublicSuffixDatabase.list` 资源加载失败，栈经过 `AliyunDns` / `DnsOverHttps`。应用已自己区分内网和公开域名，生产 DoH builder 现在关闭重复的 PSL 分类依赖，保留系统 DNS 回退与内网规则。测试使用同一个生产 builder；实现依据为锁定的 [OkHttp 5.1.0 官方源码](https://github.com/square/okhttp/blob/parent-5.1.0/okhttp-dnsoverhttps/src/main/kotlin/okhttp3/dnsoverhttps/DnsOverHttps.kt)。
- [AHUTONG-ANDROID-1J](https://openahu.sentry.io/issues/7804914594/)：3.4.5+304009 的 calc 插件找不到 `androidx.compose.ui.unit.TextUnit`。补两条精确 R8 keep，真实 Compose 字节码测试重现基线失败并验证修复成功。
- [AHUTONG-ANDROID-7](https://openahu.sentry.io/issues/7746169732/)：最近 3.4.5+304009 后台 ANR 的主线程停在 `nativePollOnce/epoll_pwait`，仅凭此事件无法归因主页切换。
- [AHUTONG-ANDROID-17](https://openahu.sentry.io/issues/7798567090/) 的旧版本网络超时栈尚有混淆；没有把它强行对应为电控或自动退出的根因。

另修复插件 `foo` 卸载误删 `foobar` 偏好的前缀匹配问题。详见 [插件与交叉审查](2026-10-22-plugin-background-cross-review.md)。完整 Release 及真实 calc 插件渲染仍与独立 R8 测试分开验证。

## 邮箱 HAR

实际文件为 `D:/Coding/AHUTong-Evolved/captured-har/mail-login.har`。321 条请求，305 条经过 WebVPN；记录从短信验证页开始。确认 `/login/mobileLogin → /redirect → Sirius jump → /js6/s` 的后半段 SID 连续性。没有初始 CAS、Cookie 或 Set-Cookie 证据。

现有源码的直接 302 / SID 提取与后半段协议形状相容，但并未实现这份 HAR 中的短信验证提交和邮箱阶段 WebVPN 代理。不会据此声称完整 HAR 流程或 Cookie 冷启保持已经验证。详见 [脱敏 HAR 对照](2026-10-22-mail-har-review.md)。

## 初次审查验证记录

每次 Gradle 验证前均运行 `adb devices -l`，可用设备为 `emulator-5554`（x86_64，支持 arm64-v8a）。JDK 使用 Android Studio 自带 JBR，SDK 为本机 Android SDK。

本机默认 Gradle 依赖解析未找到两个既有 JitPack 锁定依赖。验证时直接从原 JitPack 路径下载相同版本的 POM/AAR/metadata 到忽略的 `build/review-maven`；Monet AAR SHA-256 与其发布 metadata 一致。用 `build/gradle-review-home` 隔离本机全局 init，复用缓存，并通过仅存在于 build 的 init 使用本机镜像和临时 Maven 缓存。项目仓库配置、依赖版本及锁文件未因此修改。

统一命令：

```powershell
adb devices -l
./gradlew.bat -g build/gradle-review-home -I build/review-maven.init.gradle testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --continue --no-configuration-cache --console=plain --no-daemon --max-workers=2
```

登录存储新增测试在旧应用编译结果上先出现两项失败（凭据故障被忽略、身份读取异常向外抛出），修复后重新编译并执行。插件 R8 验证已实际得到：

```text
baseline: reproduced ClassNotFoundException: androidx.compose.ui.unit.TextUnit
fixed: TextUnit plugin ABI OK
```

最终结果：

| 验证 | 结果 |
| --- | --- |
| `testDebugUnitTest` | 17 个有测试的模块，共 1,083 项；0 failures / 0 errors / 0 skipped。包含模块边界、登录状态、加密迁移、邮箱 CAS、学习通、课表、排名、缴费与后台等测试 |
| `:app:assembleDebug` / `:app:assembleDebugAndroidTest` | 成功；最终统一命令 `BUILD SUCCESSFUL` |
| 真实校方登录及加密持久化 | 测试账号通过；不保存或展示账号、密码、成绩与排名值 |
| 强制结束进程后的身份及密码恢复 | 最终 APK 在新进程中通过 |
| 后台返回 / Activity 重建 | 最终 APK 通过，账号保持、未被置为 Expired |
| Android Keystore 非破坏性读取 | 真正 Android 密文读写：损坏测试记录后读取返回不可用，密文记录仍保留；还原原密文后成功解密；测试结束清理专用 fixture |
| 真实 Compose / R8 ABI | 基线重现 TextUnit 缺类，修复后按原名装箱 / 拆箱与 Companion 调用通过 |
| UI 目视 | 已查看当前 Radiant 主题：设置副标题移除、灰色状态位置与图标、成绩卡片边距 / 密度、电控无独立行边框的分组。Material / Miuix 调用与编译通过，未逐主题做完整视觉遍历 |
| 范围与凭据检查 | 原工作区无变化；未修改项目依赖版本、仓库设置或锁文件；改动及新文件无测试账号 / 密码明文，`git diff --check` 与新文件尾随空格检查通过 |

设备命令使用 `adb -s emulator-5554 install -r`、`install -r -t`、`shell am start -W`，实际安装和启动成功。设备测试用 `shell am instrument -w -r` 分别选择 `SessionPersistenceDeviceTest` 的方法：`signInAndPersistLiveAccount`、`coldProcessRestoresPersistedAccount`、`backgroundReturnAndActivityRecreationKeepTheAccount`、`unreadableCiphertextIsPreservedAndCanBeReadAfterRecovery`。首次真实登录仅在测试开始时执行一次；最终包重新安装后，后三项再次执行，结果 `OK (3 tests)`。测试完成后已结束 Debug 应用进程。

产物：`app/build/outputs/apk/debug/app-debug.apk`，48,409,501 bytes，SHA-256：`CBAB665B946452CD22731BAF6A8CC4AB5C46C1F7EB0B430DDBFAB16BD74DCD9C`。仅为本地 Debug 测试包，未上传或推送。

设备实网 smoke 使用 `SessionPersistenceDeviceTest`，仅在显式传入 `review.live=true` 时执行，测试凭据通过 instrumentation 参数传入，不固化在源码中。测试不发起充值、付款、抢先体验加入或发送邮件。

## 最新 develop 交付与标题栏

在 `b6961a3a` 之上整合本次修复，并追加 Material / Miuix 子页面统一标题栏返回入口。四个一级标签页及首次认证流程保留原导航；页面显式传递返回回调，插件宿主继续遵循 `dispatchBack()`，不直接跳过内部返回。RadiantUI 保留原外观。详情见 [子页面返回按钮](2026-10-22-subpage-back-navigation.md) 与 [完整路由及特殊返回核对](2026-10-22-title-back-navigation-review.md)。


### 合入后最终验证

基于 `b6961a3a` 和全部本次改动（含标题单行布局），原版 Gradle 9.2.0 完整命令 `BUILD SUCCESSFUL in 11m 57s`，980 项任务执行。

| 项目 | 最终结果 |
| --- | --- |
| 全模块单元测试 | 17 个有测试模块，共 1,084 项；0 failures / 0 errors / 0 skipped，XML 来自本次独立产物目录，未复用旧报告 |
| Debug / AndroidTest APK | 构建成功，已安装到 `emulator-5554` 并冷启动成功 |
| Release 源码 | `:app:compileReleaseKotlin` 成功；没有生成或发布生产 Release |
| 登录保持设备回归 | 最终 APK 再次运行冷进程身份/密码恢复、后台返回和 Activity 重建、Keystore 密文不可用后还原恢复，`OK (3 tests)`；未重新提交测试密码 |
| Material / Miuix UI | 均实际点击偏好设置和校园通知标题返回，确认回上层；已查看全部小工具截图与最终 Material 通知标题，返回键可见、通知标题保持单行 |
| RUI 隔离 | 实际切换 RadiantUI，通知页保持原标题，未出现新增返回键；使用系统返回沿原流程退出 |
| 仓库范围 | 原检出内容保留，项目 wrapper / 依赖版本 / lock / verification metadata / 全局配置没有变化；账号字面量、Sentry 私密 token、私钥模式扫描为 0 |

最终 APK：`build/review-final-builds/app/outputs/apk/debug/app-debug.apk`，46,911,850 bytes，SHA-256：`ACCEE3D67F9E7740D855F47747457DDFF1518C15E96C821CB01CE3381D6E249B`。日志仅位于忽略的 `build/session-review-final-isolated-validation.log`，未提交截图、账号、密码或构建产物。

### 本机验证环境处理

初次使用 `build/gradle-review-home/caches` junction 复用全局缓存，在新脚本编译和测试 worker 初始化时出现异常。官方字节码与实际 File 比较确认：worker 锁被 canonical 化到 C 盘，清理枚举仍在 D 盘别名，`File.equals` 未识别同一文件，导致尝试删除自身持有的锁。直接使用真实目录后，项目同版本 Gradle 9.2.0 的脚本检查通过；没有据此改写合法的上游 Gradle 源码。

本机全局镜像 init 还会清空项目仓库再新建 exclusiveContent，却保留旧排除回调，导致两个 JitPack 依赖被排除。最终采用无全局 init、无 caches junction 的独立真实用户目录，并用 [Gradle 官方只读缓存机制](https://docs.gradle.org/current/userguide/dependency_caching.html) 读取本机已校验依赖。项目唯一的 exclusiveContent 保留，任务内 init 只将原 JitPack 仓库指向前述相同版本本地 Maven 缓存。

旧构建输出的 classes.jar 被残留编译进程持有，因此另将各模块的本次构建输出定向到忽略的 `build/review-final-builds/<module>`，并采用 Kotlin in-process 编译。所有这些路径、镜像 init 和临时工具都没有提交。仓库的 wrapper、依赖版本、锁文件、校验 metadata 和全局配置均不变。

排障时用已核对官方 ZIP 校验值的 Gradle 9.5.0 完成过 Debug、AndroidTest APK 与 Release Kotlin 编译，并安装启动进行两主题界面验收。该对照只临时增加四个已与 Maven Central 发布校验值核对的工具依赖条目，结束后已逐字节还原 metadata；没有关闭依赖校验。最终验证恢复为 wrapper 锁定的同版本原版 Gradle 9.2.0 分发，以真实路径直接执行，避免再次通过目录别名启动。

最终命令的任务与项目命令一致，`-g` 指向真实独立目录，`-I` 只做上述本机仓库与产物目录设置；带点的属性参数使用完整参数列表，避免 PowerShell 拆分：

```powershell
adb devices -l
$env:GRADLE_RO_DEP_CACHE = 'C:/Users/MuxYang/.gradle/caches'
& $gradle920 -g $reviewRealHome -I $reviewFinalInit testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseKotlin '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8' --no-configuration-cache --console=plain --no-daemon --max-workers=2
```

## 仍需验证的边界

- 本次设备为模拟器，短时后台、重建和强制结束进程已测；未覆盖不同厂商真机的长期后台、系统清理与真实 Keystore 瞬时故障。不能将故障注入和模拟器通过等同于现场所有自动退出均已消除。
- 历史迁移存在待验证路径：`RustSDK.kvGetStringSafe` 在原生库未加载时返回 null。若非常旧的安装仅在 Rust KV 保存身份、且加密库与 MMKV 都没有副本，启动早期的缺失判断仍需用该历史安装 fixture 验证。本轮测试账号走当前加密持久化路径，没有改写原生引导与历史迁移次序。
- 电控当前“每天首次检测即记为已尝试”的限额策略未改变，离线失败后当天不会自动重复请求；真实扣费与零电量房间现场提醒未执行。
- 邮箱短信验证 / 邮箱 WebVPN 后半段尚未实现，HAR 也不足以验证 Cookie 冷启保持；完整 Release 与实际 calc 插件渲染未执行。
