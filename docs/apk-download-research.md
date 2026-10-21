# APK 镜像下载与进度调查

日期：2026-10-20。代码调查基线：`upstream/develop` 的 `8370f531`。本文区分代码已经证明的问题、公开端点的一次现场观察，以及仍需要 Android 真机测量的问题。

## 结论与本次改动方向

1. APK 应直接请求 `https://openahu.muxyang.com`：先校验更新元数据里的下载 URL，再替换 `openahu.org` 的主机名，保留原始路径和查询参数。废弃旧镜像域名及“先主站，5 秒后建议切镜像”的流程。这是本次产品要求；更新检查 API 仍使用自己的原有域名。
2. 进度总大小优先使用响应头 `X-File-Size`，含义必须是**最终写入 APK 文件的字节数**。客户端读取透明解压后的正文时，不能拿压缩传输长度作分母；缺少长度时显示不确定进度，仍以 SHA-256 检查最终产物。OkHttp 透明 gzip 的确会删除 `Content-Length`，但缺少该字段本身不能证明服务端进行了压缩。[OkHttp 5.1.0 BridgeInterceptor](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/BridgeInterceptor.kt)
3. 当前实际 APK 路径已经使用 `@Streaming`，未发现成功响应被 Retrofit 整包缓存到内存、强制 HTTP/1.1、每个数据块都新建客户端或人为限速。不能把“Retrofit / suspend / GET 很慢”作为已确认根因。[Retrofit 2.11.0 内置转换器](https://raw.githubusercontent.com/square/retrofit/2.11.0/retrofit/src/main/java/retrofit2/BuiltInConverters.java)、[OkHttp 5.1.0 默认客户端](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt)

## 实际调用链与可证实的问题

版本目录 `gradle/libs.versions.toml` 声明 OkHttp / MockWebServer `5.1.0`、Retrofit `2.11.0`，因此以下库行为均核对这些精确版本的官方源码。

| 位置 | 基线代码行为 | 对问题的影响 |
| --- | --- | --- |
| `app/.../data/server/AhuTong.kt` | `GET /api/check_apk_update` 获取元数据 | 检查更新与 APK 传输是两条请求；不应把全部业务请求域名替换成镜像 |
| `data/update/.../ApkDownloadApi.kt` | `@Streaming @GET suspend fun downloadByUrl(...): Response<ResponseBody>`，单例下载客户端，独立 Dispatcher，禁止自动跳转 | 正常 APK 以流读取；下载器负责逐跳校验重定向 |
| `data/update/.../ApkDownloader.kt` | 单个 GET，64 KiB 读写缓冲，`BufferedOutputStream`，流完成后计算 SHA-256 | 当前没有并行分段、Range 或断点续传；`.part` 每次开始都会删除，重试会重下已传内容 |
| 同上 | 总大小只读 `body.contentLength()`；5 秒后若进度未知或不足 30% 就建议切换 | 缺少长度会始终触发“慢”的提示，即使正文正高速传输；这是判据错误，不能据此推断真实吞吐量 |
| 同上及 `ApkUpdatePolicy.kt` | 首次下载主站，用户确认后取消再改为旧镜像域名 | 切换会产生额外往返并丢弃已有部分；只改域名常量仍会保留不符合新要求的流程 |
| `core/network/.../AhuHttp.kt` / `AliyunDns.kt` | 共享 DoH 解析器；公共域名先 DoH，失败再系统解析 | DoH 故障可能增加建连前等待，属于首包延迟候选；没有现场 DNS 事件就不能判定它导致持续下载慢 |

上述是仓库基线代码事实，既有 `AhuTong.downloadFile()` 虽未声明 `@Streaming`，但当前 APK 下载器走的是 `ApkDownloadApi`，不能把前者直接当成当前 APK 的整包缓冲证据。Retrofit 的 `@Streaming` 转换器原样返回 `ResponseBody`；`suspend` 使用异步 `enqueue` 等待回调，并不会额外把成功正文读完。[Retrofit BuiltInConverters](https://raw.githubusercontent.com/square/retrofit/2.11.0/retrofit/src/main/java/retrofit2/BuiltInConverters.java)、[Retrofit KotlinExtensions](https://raw.githubusercontent.com/square/retrofit/2.11.0/retrofit/src/main/java/retrofit2/KotlinExtensions.kt)

一个独立的边界是**非 2xx 正文**：Retrofit 2.11.0 的 `OkHttpCall.parseResponse()` 即使面对 `@Streaming` 方法也会缓存完整的非 2xx 正文，包括由下载器手动处理的 3xx。服务器若返回巨大或迟迟结束的错误页 / 跳转正文，调用方可能要等它读完才能处理状态码。这不影响正常 200 APK；本次公开镜像响应也未出现该问题，不能将其写成现场根因。[Retrofit 2.11.0 OkHttpCall](https://raw.githubusercontent.com/square/retrofit/2.11.0/retrofit/src/main/java/retrofit2/OkHttpCall.java)

## `X-File-Size` 与压缩

OkHttp 未看到显式 `Accept-Encoding` 或 `Range` 时默认添加 `Accept-Encoding: gzip`。收到 gzip 后自动解压，移除 `Content-Encoding`、`Content-Length`，并将应用层 `ResponseBody.contentLength()` 设为 `-1`。所以分子应是实际写入文件的字节数，分母使用同一表示的 `X-File-Size`，而不是网络层压缩字节数。不要手动添加 `Accept-Encoding: gzip, br, zstd`；这样会关闭这里的自动 gzip 协商逻辑，而 APK 客户端当前也没有配置对应的其他解码器。[OkHttp 5.1.0 BridgeInterceptor](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/BridgeInterceptor.kt)

`X-File-Size` 是本项目与源站之间的约定，不是由 HTTP 标准保证的 APK 原始大小。应只接受合理范围内的正整数，保留下载最大体积限制，在流结束时验证实际文件长度与声明长度一致，并继续验证更新元数据中的 SHA-256；非法或矛盾的长度不能变成大于 100% 或伪造完成进度。缺少该自定义头但应用层正文长度已知时，可以回退到应用层 `contentLength()`；两者都未知时继续流式下载。

阿里 ESA 官方文档说明了基于 `Accept-Encoding` 协商压缩、已有 `Content-Encoding` 时不重复压缩等条件；当前文档列出的默认可压缩类型不含 `application/vnd.android.package-archive`，并写明 gzip / Brotli 文件大小范围为 1 KiB 至 10 MiB。因此无法仅凭“ESA + 无 Content-Length”推断用户给出的约 16.7 MB APK 当时已实际 gzip；自定义规则、源站编码和不同边缘路径都要另行确认。[ESA 官方资源压缩文档](https://help.aliyun.com/en/edge-security-acceleration/esa/user-guide/content-compression)

APK 本身采用 ZIP 文件格式，但这也不意味着 HTTP 再压缩必然无效：包中未压缩条目的比例会影响收益。是否保留 APK 的边缘压缩应比较同版本、同路径下编码方式、传输字节数、解压后的文件字节数与摘要、首包和总耗时，不应先行认定关闭压缩一定更快。[Android 官方 APK Analyzer](https://developer.android.com/studio/debug/apk-analyzer)

## 切换与取消

OkHttp 的异步 Dispatcher 在 `onResponse` 回调返回后就会移除该 Call，即使应用仍在读取流式正文。因此只调用 `dispatcher.cancelAll()` 不能覆盖已经交出正文的流；基线已经用 `EventListener.callStart/callEnd/callFailed` 跟踪 Call，并额外调用 `Call.cancel()`。这个机制应保留，不应在本次修复中误删。HTTP/2 已建好的流取消通常仅取消该流，不是必然关闭共享连接的整个 socket。[Dispatcher 5.1.0](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt)、[RealCall 5.1.0](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt)

当下载 API 的 `suspend` 调用已经返回、协程正在阻塞式读取 `InputStream` 时，不能把之前等待响应阶段的取消处理当成整个流读取阶段的取消保障。取消旧 Call、等待旧任务退出，再开始新的文件写入，能避免旧任务继续占带宽或同时写同一 `.part` 文件。该结论由 Retrofit 的回调恢复位置和 OkHttp 的 Call 生命周期支持；需要真实流式阻塞测试覆盖。[Retrofit awaitResponse](https://raw.githubusercontent.com/square/retrofit/2.11.0/retrofit/src/main/java/retrofit2/KotlinExtensions.kt)、[OkHttp RealCall](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt)

只有一个合法镜像时，应删除源选择提示及无效“继续主站 / 切镜像”动作。失败后由用户重试同一镜像即可；不要静默回退到被弃用的旧镜像或主站 APK。

## HTTP 方法、协议与速度判断

- **GET / 流式读取**：GET 是读取资源的正常方法，Range 也定义在 GET 上。没有证据表明换成 POST、同步 `execute()` 或另一个客户端框架会提高当前 APK 的传输速度。当前成功正文已经流式落盘。[HTTP Semantics §9.3.1 / §14.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.1)
- **HTTP/2 / HTTP/1.1**：客户端没有覆盖协议列表，OkHttp 默认是 HTTP/2、HTTP/1.1。HTTP/2 多路复用和头压缩不保证单个大文件在每个运营商链路上都比 HTTP/1.1 快；当前没有足够证据强制降级。HTTP/2 使用 DATA 帧传输正文，`Content-Length` 可以缺省，缺少它不会让正文无法正常结束。[OkHttp 默认协议与客户端配置](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt)、[HTTP/2 §8.1](https://httpwg.org/specs/rfc9113.html#rfc.section.8.1)
- **超时 / 并发**：基线连接 20 秒、单次读取 120 秒、整条 Call 900 秒，是等待上限，不是传输间隔或速率上限。独立 Dispatcher 默认每主机最多 5 个异步请求不会把一个流式 GET 限成五分之一带宽。增大线程数不是已被证实的加速方案。[OkHttp 超时定义](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt)、[Dispatcher 并发定义](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Dispatcher.kt)
- **续传 / 多段**：当前每次从头下载可能让重试总体更慢，但直接增加 `Range` 不安全。HTTP byte range 以编码后的表示为坐标，不能把已解压文件长度当作 gzip 字节偏移。将来实现应统一 `identity` 表示、用强 ETag / `If-Range` 验证版本、严格检查 `206` 和 `Content-Range` 起点，处理返回 `200` / `416`，最后验证整包摘要；不应直接追加任意响应。[HTTP Semantics §13.1.5 / §14.1.2 / §14.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-14.1.2)

## 公开端点现场观察

以下为 2026-10-20 本次开发机上的只读请求，APK 正文写入 Windows `NUL`，只打印白名单响应头及 curl 指标，不保存 Cookie 或 APK。公开检查接口 `https://openahu.org/api/check_apk_update` 返回 `stable`、`versionName=3.4.2`、`versionCode=304003`、下载地址 `https://openahu.org/download/ahutong.apk`；将域名替换后请求新镜像。这里没有核对丢弃后的 APK 摘要，因此不将这两次请求计为应用安装包完整性验证。

| 测量项 | curl `--compressed` | `Accept-Encoding: identity` |
| --- | ---: | ---: |
| 状态 / 实际协议 | 200 / HTTP/1.1 | 200 / HTTP/1.1 |
| Server / 缓存标记 | ESA / HIT | ESA / HIT |
| `Content-Encoding` | 未出现 | 未出现 |
| `Content-Length` | 未出现 | 未出现 |
| `X-File-Size` | 16,705,342 | 16,705,342 |
| curl `size_download` | 16,705,342 字节 | 16,705,342 字节 |
| DNS | 0.469 秒 | 0.021 秒 |
| 首字节 `time_starttransfer` | 2.047 秒 | 0.915 秒 |
| 总耗时 | 2.954 秒 | 1.767 秒 |
| 平均正文速率 `speed_download` | 5,655,558 字节/秒 | 9,451,459 字节/秒 |

identity 响应另观察到 `Transfer-Encoding: chunked`；无 `Content-Length` 在这里没有阻碍完整正文传输。本次两种协商都没有实际压缩编码，传输正文长度与自定义文件大小相同；用户提供的另一份响应中 `X-File-Size=16734523` 与本次观察值不同，应按**当前具体响应**读取大小，不硬编码样例。

两次请求顺序执行、DNS 和边缘缓存条件不同，样本也只有各一次，因此上表不能证明 identity 比 gzip 快。本机 curl 8.21.0 的 Features 不含 HTTP2，故这里没有执行 HTTP/2 对照实验，也不能推断 Android 的实际协议或速度。初次沙箱内 curl 报 `SEC_E_NO_CREDENTIALS`，移到允许联网的执行环境后成功；该错误属于本次开发机 TLS 环境，不能作为源站不可用证据。

## 验证与后续测量

本次代码回归至少覆盖：主站 URL 保留编码路径 / 查询并映射到新镜像、已经是新镜像时幂等、旧镜像拒绝、逐跳重定向仍只允许目标下载域名、没有 `Content-Length` 时用 `X-File-Size` 单调更新进度、gzip 透明解压后按文件字节计数、非法 / 矛盾 / 超限大小、正文截断、取消阻塞读取及快速重试不会同时写入文件。保留 SHA-256 作为最终内容校验。

若 Android 真机仍慢，应在用户受影响的网络记录不含凭证的 OkHttp 事件：DNS、TCP / TLS、实际 `response.protocol`、请求到响应头耗时、正文读取耗时和字节数、边缘缓存标记及重定向跳数。`EventListener` 支持分离这些阶段，才能区分解析等待、回源 / TTFB、持续吞吐和本地写盘问题。[OkHttp 5.1.0 EventListener](https://raw.githubusercontent.com/square/okhttp/parent-5.1.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/EventListener.kt)

协议或压缩 A/B 只应在同一设备、同一网络、同版本 APK、同一 URL、尽量相近缓存条件下重复执行，并分别记录网络正文大小、解码后文件大小及摘要。本文没有确认现场网速根因，也没有以开发机 curl 替代 Android 真机运行验证。

## 本次实现与验证记录

- 工作分支：`p/MuxYang/fix/apk-mirror-download`，从实时拉取的 `upstream/develop` `8370f5315854588e2dd344490bdb511ea921f28c` 开始。
- 更新元数据在校验时转换为新镜像 URL；下载器入口再次归一化。保留原始编码路径、查询参数和显式端口；已经是新镜像时幂等。下载重定向只允许新镜像，不回退到主站或旧镜像。
- 移除五秒切源计时器、切源事件和界面。取消 / 重试仍关闭正在读正文的 Call，并等待旧任务退出；已取消任务不能继续发送进度或失败事件。
- `X-File-Size` 优先于应用层正文长度；缺少时回退到已知正文长度，两者未知则显示不确定进度。非法 / 超限大小、实际长度不符均失败，最终仍必须通过 SHA-256 校验。隐私政策同步更新下载域名与行为。
- 先运行新增 ESA 回归测试，旧实现失败：预期有确定进度，实际仅收到 `[null]`。修复后运行下列命令通过，共 **46 项单元测试，0 失败 / 0 错误**，Android Lint 通过。包括本地 HTTP 服务器发送真实 gzip、无 `Content-Length` 的解压后大小与摘要验证，以及 Retrofit 回调已退出后的阻塞流取消 / 重试。

```powershell
adb devices
$env:JAVA_HOME = 'D:\Tools\zulu\zulu-21'
.\gradlew.bat :data:update:testDebugUnitTest :data:update:lintDebug --console=plain --no-configuration-cache
```

设备列表为空，因此未安装或启动应用。完整应用验证尝试如下：

```powershell
adb devices
$env:JAVA_HOME = 'D:\Tools\zulu\zulu-21'
.\gradlew.bat :data:update:testDebugUnitTest :app:assembleDebug :app:testDebugUnitTest --console=plain --no-configuration-cache
```

该组合任务在 `:app:checkDebugAarMetadata` 被已有锁定依赖缺失阻断：`com.github.franmontiel:PersistentCookieJar:v1.0.1` 和 `com.github.Kyant0:Monet:0.1.0-alpha03` 无法解析。没有更改依赖版本或锁文件来绕过；随后独立执行上面的更新模块测试与 Lint 成功。**完整 Debug APK 构建、应用级单元测试、Android 真机更新 / 安装及受影响网络的速度仍未验证。**
