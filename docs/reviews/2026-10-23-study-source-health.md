# 学习资料源检测、自动备用与意外错误（2026-10-23）

基线：`upstream/develop` / `upstream/master` = `5bf00196`。工作分支：`p/MuxYang/feat/study-source-health`。

## 用户可见行为

- 普通源为 jsDelivr、GHFast、gh-proxy、GitHub 直连。按用户补充要求移除 Moeyy；旧保存的 `moeyy` 偏好回退到默认普通源，不再请求旧域名。
- 本次 App 进程首次进入学习资料时自动异步检测；再进入资料或源设置页复用本 session 的完整结果，重启 App 后重新检测。源名称右侧 4 dp 处显示绿色对勾或红色叉；未知显示中性标记，检测中显示进度，不提前判红。可点击“重新检测”，切源与返回不等待网络。
- 第五项“备用源”默认置灰且不可选。四个普通源都明确不可用时自动启用；实际文件的四源尝试全部失败也会进入备用。未知、检测中或仅部分失败都不会提前开启。
- 自动备用不覆盖用户保存的普通源偏好；手动重新检测后有普通源恢复时回到原偏好。取消检测后可重试，完整结果不按时间自动过期。
- 按用户最后确认，仅删掉页面顶部“每个源下载 1 MB……”说明；保留普通源已有说明与备用源“仅在其他四个源均不可用时自动启用”。实际检测大小不变。
- 普通下载、Markdown 预览共用回退流程；LFS 的存储服务直传仍可直接使用。失败保留 cause / suppressed，取消会停止请求并等待写入结束后清理临时目标。

## 1 MiB 检测

测试文件来自用户指定的 [MuxYang/SpeedTest](https://github.com/MuxYang/SpeedTest)，固定到提交 `8ff60f13f5c88a60a41b46fea01e316759a6011b` 的 `1MiB.bin`：

- 大小：1,048,576 字节。
- SHA-256：`f790cffe9ad224031a793c5ac906a5e40d9f86a7e386cc750f4361038066a667`。
- 四个普通源并行实际 GET；每次请求总超时 12 秒。只接受完整 HTTP 200、正确大小与哈希。截断、HTML 错误页或同大小的错误内容不能判为成功。
- 同批并发请求合并，取消恢复尚未完成项的先前状态；检测不会把取消标成源故障。

[GHFast 官方说明](https://ghfast.top/)支持给 `raw.githubusercontent.com` 文件加 `https://ghfast.top/` 前缀。本机同一文件的公开网络实测：

| 源 | HTTP / 字节及哈希 | 耗时 |
| --- | --- | --- |
| jsDelivr | 200，1,048,576 字节，哈希一致 | 4.53 秒 |
| GHFast | 200，1,048,576 字节，哈希一致 | 2.75 秒 |
| gh-proxy | 200，1,048,576 字节，哈希一致 | 1.92 秒 |
| GitHub 直连 | 200，1,048,576 字节，哈希一致 | 1.75 秒 |

这些是当前开发电脑的结果，不硬编码到手机；App 使用设备当时的网络检测。Moeyy 本次连接失败，随后按要求从列表移除。

## 备用协议及真实服务检查

对现有存储下载 URL 使用 `HttpUrl.setQueryParameter("f", "1")`。因此 `/api/download?id=<id>` 变为 `/api/download?id=<id>&f=1`；保留原参数和编码，替换已有 `f` 而不重复追加。参数只加到资料存储 API，不加到 GitHub / CDN URL。

在 DE、HK 两个节点分别读取公开索引并取同一份小 PDF，独立验证了用户截图中的服务约定：

| 请求 | 两节点结果 |
| --- | --- |
| 默认 `/api/download?id=...` | HTTP 200，`X-Download-Type=link`，JSON 含下载 URL |
| 追加 `&f=1` | HTTP 200，`X-Download-Type=file`，文件 2,595 字节，`X-File-Size` 和 `Content-Length` 都一致 |
| `Range: bytes=0-31` + force | HTTP 206，`Content-Range: bytes 0-31/2595`，32 字节等于完整文件前缀 |

两节点 PDF 的 SHA-256 均为 `ef763ad7680c1853da224ea261a40791fc637e99e844ecc090e3f64ed7cbf1bd`。这是服务端协议验证，没有给 App 新增断点续传功能；App 的完整下载路径未发送 Range，故意外 206 不作完整文件接受。

备用响应必须明确为 `X-Download-Type=file`，防止把 link/error JSON 当文件保存。合法 JSON / HTML 资料仍允许；无 file 头的镜像响应按预期文件扩展名识别明显错误网页 / envelope / LFS 指针。完整下载和 Markdown 均核对已知长度。

## 全应用意外错误

用户已确认覆盖全应用。异常出口保留原 Throwable，经分类后意外错误由三主题统一弹窗展示；可滚动选择错误与追踪，提供取消、复制和 QQ 群反馈。普通成功、校验、网络、明确业务 / 登录过期提示保留原行为。详见 [错误弹窗说明](2026-10-23-unexpected-error-dialog.md)。

## 接口及依赖影响

- `RepositoryAccelerationSource` 末尾追加默认 `isFallback=false`；新增 `RepositorySourceHealth`、状态流及异步检测接口。生产实现与 repository/settings 测试替身一起更新。
- `:data:repository-index` 用 `api` 暴露项目已有版本的 `kotlinx-coroutines-core:1.10.2`，因为公共契约包含 `StateFlow`。依赖锁由 Gradle 定向重新生成；不升级其他库，不修改仓库镜像源配置。
- 错误提示接缝 `UserNotice` 新增带默认实现的 `reportUnexpected(Throwable)`；原实现和现有普通提示仍兼容。没有新增 Android 权限或后台服务。

## 验证记录

- 编译前执行 `adb devices -l`：有可用设备 `emulator-5554`（`sdk_gphone64_x86_64`），随后构建、安装并启动应用。
- Gradle 9.2.0 执行 `testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease`；最终构建成功，用时 7 分 26 秒。17 个模块共 1,127 项 JVM 测试，失败、错误与跳过均为 0。构建使用本地缓存及临时 init 脚本解决依赖访问，未修改仓库内源配置；相同任务可通过项目 `gradlew.bat` 运行。
- 安装最终 Debug 与 AndroidTest APK，执行 `adb -s emulator-5554 shell am instrument -w -r -e class com.ahu.ahutong.ui.UnexpectedErrorDialogWindowTest com.ahu.ahutong.debug.test/androidx.test.runner.AndroidJUnitRunner`，结果 `OK (2 tests)`，涵盖真实 Host 的后台等待、前台恢复、Activity 重建、实际剪贴板脱敏，以及三主题长追踪下的三个按钮。
- 最终 Debug 包的模拟器交互验证：首次正常进入四源均绿、备用灰色不可选；新进程断网首次进入四源均红并自动选中备用；恢复联网后返回再进入仍保留本 session 红色结果；手动重新检测后四源恢复绿色、备用禁用，原 jsDelivr 偏好恢复。检测说明已删除、备用说明保留；测试结束恢复原设备网络设置。
- 完整 Release 实际执行 `:app:minifyReleaseWithR8` 及资源优化，生成约 17.6 MB 的 APK；检查最终合并配置中的精确存储响应模型 keep 规则与 `SourceFile` / `LineNumberTable`，本次无需添加宽泛 keep。mapping 已本地生成；未配置上传凭据，Sentry mapping 上传任务跳过。
- Release 包用本机调试证书另行签名并通过签名校验，安装后可冷启动并渲染登录页。该包只用于本地 R8 检查，未作生产发布；未使用真实账号登录，资料页交互与错误弹窗的设备验证使用最终 Debug 包，不将其计为 Release 登录后功能验证。

网络检查脚本、结果 JSON、二进制样本、Gradle 的本地镜像 init 与构建输出仅位于忽略的 `build/study-validation/`，不纳入提交。最终提交仅含本任务代码、必要测试、依赖锁与说明。
