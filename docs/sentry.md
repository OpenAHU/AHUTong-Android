# Sentry 崩溃上报

应用通过 [Sentry Android SDK](https://docs.sentry.io/platforms/android/) 上报崩溃与性能数据，与现有的 Bugly 并存。

## 用户反馈与加密日志

设置的“意见反馈”打开“问题反馈 / 日志导出 / QQ 反馈群”列表，前两项进入各自子页面，QQ 入口直接跳转。意外错误弹窗直接进入问题反馈页。问题描述最多 4096 字；“运行记录”和“错误信息”默认勾选，“网络详情”默认不勾选。发送按钮仅在预览内，发送使用预览时固定的日志快照。

- 采集从应用启动起常开，在各进程内存中轮转最近 10 分钟、最多 5 MiB，没有采集开关；单条网络正文最多 256 KiB。退出账号、切换账号或重新登录会清空记录并使旧请求失效。内存缓冲随进程退出释放；自动错误诊断会使用下述 Sentry 缓存和上报链路。
- Rust SDK 通过可选 JNI 回调将 `ahutong_rs` / `guixu` 的现有日志直接送入同一缓冲，保留原有 Logcat 输出；不读取系统日志、不增加日志文件或权限。Info 等归入“运行记录”，Warn / Error 归入“错误信息”，HTML、JSON 等正文仅在“网络详情”保留。旧版热更新库没有回调时仍可运行；运行记录中标注桥接是否已连接，不宣称已采集不支持该能力的原生库内部日志。
- 普通记录隐藏账号、Cookie、Token 等信息。“网络详情”包含统一 OkHttp 链路实际发送/读取的请求、响应、Cookie 和认证头，以及 Rust 已有日志的详细上下文，仅隐藏密码及密码派生登录字段。Rust 单条格式化记录超过 256 KiB 时只保留未采集标记，避免截断密码后再脱敏。JNI 调用和本地 HTTP 请求各自固定开始时的账号代次，切换账号后拒绝旧任务的后续记录。二进制、双工、未读完、截断或不支持编码的正文明确标注；不宣称覆盖 WebView 或 Rust 内部完整网络。
- Sentry 使用现有 Android SDK `Sentry.feedback().capture`，不引入 Kotlin Multiplatform 旧版 `UserFeedback` 接口。异常反馈复用已有 eventId；没有时在提交时创建脱敏错误事件并关联，失败重试复用同一编号。主动反馈无需错误事件。附件仅通过本次 `Hint` 添加，清空本次临时 scope，避免混入后续崩溃或其他反馈。
- “反馈已受理”表示 SDK 接受了事件，网络、服务端过滤和配额仍会影响最终到达；编号不是服务端接收证明。用户明确提交后，SDK 可以将待发送事件和附件缓存在应用私有目录并重试。
- 日志导出页使用相同日志类别，仅导出和分享所选日志，不包含问题反馈页的描述。反馈预览仅展示前 32,000 字，实际提交使用预览时固定的完整采集快照。手动导出仅落加密文件；自动诊断由 Sentry 管理脱敏事件、breadcrumbs 和附件的应用私有缓存。

## 统一 ERROR handler

`AppErrorHandler` 在主进程 Sentry 初始化完成后安装，并保留已有 Sentry、Bugly 和系统的
uncaught handler 调用链。Java/Kotlin 未捕获的 `Throwable` 先尽力记录脱敏追踪，再交给原有
handler 捕获和结束进程；没有二次手动 `captureException`，不重复生成致命事件。记录器自身
抛错、内存不足、前一个 handler 缺失或抛错时，仍保留原异常的委托与终止路径。

- `launchSafe` 的根协程异常进入同一错误出口；普通异常记录后保留现有错误弹窗/提示。
  已有业务 catch 传给 `reportUnexpected` 的意外异常也自动上报，并将事件 ID 关联到后续反馈。
  正常取消不采集；VM、链接及线程终止错误交回线程的致命异常处理链。
- 活跃进程的异常事件附带 `crash-runtime.txt`、`crash-errors.txt`，各自最多 96,000 字符，
  从最近记录中选取。追踪保留 cause、suppressed 和栈帧；沿用已有截断、密码及身份脱敏规则。
  自动附件不包含 NETWORK 类别的正文、Cookie 或认证头；用户手动反馈的选择逻辑保持原样。
- 普通运行/错误记录和 Rust 的脱敏摘要同步为 Sentry breadcrumbs，每条最多 2,000 字符，
  沿用 SDK 的数量上限和 scope 持久化。这些最近记录可随 NDK 崩溃和系统退出报告保留。
  切换账号时同步清空内存记录、反馈草稿和 breadcrumbs，拒绝旧代次的异步记录。
- 启用 `attachAnrThreadDump` 和 `attachRawTombstone`，由 SDK 附上系统原始诊断。
  上次进程的 ANR、Tombstone 和缓存事件不会附加本次重启的内存日志，以免误导诊断。

普通 Kotlin `catch` 无法捕获原生 SIGSEGV、系统 SIGKILL，也无法把 ANR 当成异常恢复。
原生崩溃由 NDK/Tombstone 采集，ANR 由系统退出记录采集；强制终止、极端 OOM、SDK 安装前
或未启用 Sentry 的隔离进程发生的错误，不保证采集和上报成功。此 handler 不接管主 Looper
循环，也不让已经发生致命异常的进程继续运行。

参考：[SDK 的未捕获异常处理链](https://github.com/getsentry/sentry-java/blob/8.55.0/sentry/src/main/java/io/sentry/UncaughtExceptionHandlerIntegration.java)、
[ANR 线程转储](https://docs.sentry.io/platforms/android/configuration/app-not-respond/)。

### 加密格式与私钥

软件只包含 `feedback_export_public_key.pem` 公钥。每次导出随机生成 AES-256 密钥和 12 字节 nonce，以 AES-GCM 加密正文，以 RSA-3072 / OAEP-SHA256（MGF1-SHA256）封装 AES 密钥；格式、算法和公钥指纹同时作为 GCM 附加认证数据。`.ahulog` 的 JSON 外层只保存格式、算法、公钥指纹、封装密钥、nonce 和密文。

私钥单独交付维护者，不放入仓库或 APK。保存使用系统文件选择器；分享仅将密文写入应用私有 `cache/feedback-exports`，通过受限 FileProvider 临时授权，后续分享时清理超过 24 小时的旧密文。

维护者安装 Python `cryptography` 后解密（输出文件必须不存在）：

```powershell
python scripts/decrypt_feedback_logs.py received.ahulog --private-key path/to/private-key.pem --output decoded.txt
```

公钥更换后，旧导出仍需原私钥。解密器校验公钥指纹、OAEP 和 GCM 认证标签；私钥不匹配、内容损坏或格式不受支持时拒绝写出明文。

## 配置项

| 配置 | 位置 | 说明 |
| --- | --- | --- |
| DSN | `app/build.gradle.kts` → `BuildConfig.SENTRY_DSN` | 客户端公开标识，默认值已随仓库提供；可用 `-Psentry.dsn=...` 或环境变量 `SENTRY_DSN` 覆盖 |
| organization / project | `app/build.gradle.kts` 的 `sentry { }` | `openahu` / `ahutong-android` |
| 认证令牌 | 环境变量 `SENTRY_AUTH_TOKEN` | 仅构建期使用，不写入仓库 |

## Release mapping 上传

根项目与 app 使用 `io.sentry.android.gradle:6.21.0`。JVM 插件不会注册 Android R8 mapping 任务。

手动发布 CI 的自动 release/mapping 管理见 [CI 发布](ci.md)。普通自动构建不上传，手动发布在签名校验后单独上传一次。

本地构建时如需由 Gradle 上传：

1. 创建 Organization Token（`org:ci`），或可访问项目的 PAT（`project:releases` + `org:read`）。
2. 将令牌安全配置为本地环境变量 `SENTRY_AUTH_TOKEN`，不要写入仓库。
3. 执行 `./gradlew :app:assembleRelease`（Windows 使用 `gradlew.bat`）。有令牌时自动上传到
   `openahu/ahutong-android`；无令牌时本项目明确关闭上传，但仍生成 mapping 和关联 UUID。
4. 在 Sentry 项目 Settings → Debug Files 核对 UUID；安装这次 Release 构建并发送一条测试异常，
   确认新事件带匹配的 ProGuard debug image，堆栈显示原始类名。

可用 `./gradlew :app:tasks --all` 检查任务：

| 用途 | 任务 / 产物 |
| --- | --- |
| 上传 | `:app:uploadSentryProguardMappingsRelease` |
| 生成 UUID | `:app:generateSentryProguardUuidRelease` |
| R8 mapping | `app/build/outputs/mapping/release/mapping.txt` |
| UUID 文件 | `app/build/intermediates/sentry/release/proguard-uuid/sentry-proguard-uuid.properties` |
| APK 中的 UUID | `assets/sentry-debug-meta.properties` 的 `io.sentry.ProguardUuids` |

自动构建将无签名 Release APK、mapping 与 UUID 归档为 `AHUTong-ci-unsigned-release-with-mapping`；手动发布将实际签名 APK 与同次 mapping/UUID 归档为 `AHUTong-release-<run_id>-<run_attempt>`，流程见 [CI 发布](ci.md)。补传时必须保留这三者
来自同一次构建；相同版本号重新构建的 mapping 不能替代原 mapping。

### 已有 APK 补传

从原 APK 读取 UUID，并使用原构建的 mapping：

```bash
unzip -p original.apk assets/sentry-debug-meta.properties
# 安全配置 SENTRY_AUTH_TOKEN 后，填入上一步 io.sentry.ProguardUuids 的值。
sentry-cli upload-proguard --org openahu --project ahutong-android \
  --uuid ORIGINAL_APK_PROGUARD_UUID path/to/original/mapping.txt
```

插件 6.21.0 内置 CLI 3.7.0；该版本的 `--uuid` 虽标记 deprecated，但插件和旧 APK 补传仍使用它。
旧 APK 若没有嵌入 UUID，单独上传 mapping 无法为其事件建立关联，可用原 mapping 离线 Retrace。
补传后应验证新事件；不能保证已经处理的历史事件自动重新符号化。

### Rust / native 崩溃

R8 mapping 只能还原 Java/Kotlin。`sdk/Cargo.toml` 的 Release 配置目前会 strip，且未开启 debug info。
要符号化 Rust，需要在后续构建保留同一次构建、含 DWARF 的未 strip `.so`，再执行：

```bash
sentry-cli debug-files check path/to/unstripped/libahutong_rs.so
sentry-cli debug-files upload --org openahu --project ahutong-android \
  --wait path/to/unstripped/
```

必须核对 Build ID，不能用重编后 Build ID 不同的 `.so` 补旧事件。

### 查询 issue 的凭据

构建上传令牌与读取 issue 的权限不同。查询 Issues / Events 需要 `event:read`，项目查询需要
`project:read`，组织查询需要 `org:read`。配置本地安全环境变量或通过受支持的认证连接提供，
不要把令牌贴入 issue、日志或聊天。自建 Sentry 另配置 `sentry { url.set("https://sentry.example.com") }`。

## 运行时行为

- `AndroidManifest.xml` 中关闭了 `io.sentry.auto-init`，SDK 在 `AHUApplication#onCreate` 中手动初始化。
- Debug 包上报到 `debug` 环境，Release 包上报到 `release`；普通性能采样率分别为 `1.0` 与 `0.1`。
- 主界面恢复前台时在 IO 线程按本地日期检查；每天首次恢复发送独立的 `AHUTong DAU` 根 span（`span.op=app.dau`），不再发送 message/error。该 span 显式使用 `1.0` 采样且不绑定当前 scope，避免受页面的采样决定影响，也不覆盖页面 trace。
- 在 Explore → Spans 过滤 `span.op:app.dau environment:release`，使用 `count_unique(dau_id)` 并按天分桶。span 同时带有 `dau_id` tag/attribute 和设备本地日期 `dau_date`；选择统计时区时留意设备本地日与图表日期的差异。
- 使用独立的 `dau_last_span_report_date` 去重键：升级当天旧 message 不会阻止新 span，旧安装 ID 保持不变。同一天的重复/并发恢复仅发一次；SDK 禁用时不消耗当日上报机会。
- `dau_id` 是本地保存的随机安装标识，不含账号信息。同一安装跨天保持不变；清除应用数据后会重新生成。此统计按安装去重，同一人使用多个安装会被分别计数。
- 新路径随客户端升级生效，旧版本仍上报旧 message。客户端的全量采样不保证服务端接收：网络、配额与服务端采样仍可能影响完整性，不能将本地去重标记视为服务端确认。

## 验证方式

1. `./gradlew :app:assembleDebug` 确认集成可通过编译。
2. 安装 Debug 包后临时调用 `Sentry.captureException(new Exception("test"))`，确认 Sentry 项目中收到 `debug` 环境的事件。

## 官方文档

- [Android Gradle 插件](https://docs.sentry.io/platforms/android/configuration/gradle/)
- [ProGuard / R8](https://docs.sentry.io/platforms/android/enhance-errors/proguard/)
- [CLI 符号文件与 mapping](https://docs.sentry.io/cli/dif/)
- [权限与 scope](https://docs.sentry.io/api/permissions/)
- [自定义 tracing](https://docs.sentry.io/platforms/android/tracing/instrumentation/custom-instrumentation/)
- [SDK 8.55.0 的显式采样决定](https://github.com/getsentry/sentry-java/blob/8.55.0/sentry/src/main/java/io/sentry/TransactionContext.java)

## Native 崩溃线程信息

应用启用现有 Sentry Android SDK 的 Tombstone integration。Android 12+ 在下次启动时读取系统 ApplicationExitInfo，可补充各线程、ART 和系统库符号，并与现有 NDK 事件合并；旧系统仍由 NDK 集成采集。未启用历史 Tombstone 回传；统一 ERROR handler 启用原始 protobuf 附件供进一步诊断。

这是后续诊断信息的补齐，不能视为已解决系统 libhwui/ART 崩溃。自有 native 库仍需上传与 APK 匹配的符号。

主界面在 scope 上补充 `ui.theme`、`ui.route`、`ui.wallpaper` 和
`ui.reduce_glass_effects`，用于区分 RenderThread 崩溃时的主题和采样路径。
`ui.route` 来自 Navigation 的路由模板，去除查询部分，不记录实际导航参数或账号。
这些标签随客户端升级生效，旧事件没有这些信息。

### 排查 kill 和 ANR

- `kill` / `__kill` 可能是信号处理器重新抛出致命信号后的末端调用，不能仅凭标题判断是
  内存不足或后台清理。先看崩溃线程、信号处理之前的帧，以及同次崩溃的 NDK / Tombstone
  报告；不要把两份报告简单相加为两次崩溃。
- `AHUTONG-ANDROID-22` 的 `AHUCookieJar.loadForRequest` 并发异常来自未加锁的
  `addCookie`。缓存遍历、写入和持久化操作现在共用 jar 的锁，会话 Cookie 继续持久化。
- `AHUTONG-ANDROID-1T` 的启动链会提前构造教务客户端，在主线程恢复所有加密 Cookie，
  最终等待 Keystore Binder。`SdkDataSource` 现在延迟构造协议回退客户端，由实际业务的
  IO 请求触发恢复；不更改密文格式或凭据不可读时的处理语义。
- `ApplicationExitInfo` 的后台 ANR 栈如果落在 `nativePollOnce` / `epoll`，只说明采样时
  主线程空闲；仍需检查广播异步工作、其他线程和设备负载。不能据此关闭或过滤后台 ANR。
- `RenderThread` 的系统 `libhwui` / ART 帧需要结合设备、主题、前后台切换和匹配符号
  排查。构建通过、单元测试通过及旧版本路径已修改，都不能作为现场原生崩溃已修复的证明。

回归命令：`./gradlew :core:common:testDebugUnitTest :app:testDebugUnitTest :background:testDebugUnitTest :app:assembleDebug :app:lintDebug`。
`AHUCookieJarTest` 使用真实缓存和受控线程交错验证并发插入，并保留会话 Cookie 恢复测试；
`DataSourceInitializationTest` 验证启动构造路径不需要 Application 环境或 Keystore。

参考：[Android ANR 诊断](https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs)。

参考：https://docs.sentry.io/platforms/android/configuration/tombstones/

手动发布 CI 在独立步骤上传一次 mapping，并在服务器发布成功后自动 finalize release、记录 deploy。普通自动检查和构建保持空 Sentry token。不要同时开启 Gradle 真上传和发布步骤的 CLI 上传。
