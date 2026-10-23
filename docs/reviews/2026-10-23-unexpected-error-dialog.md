# 全应用意外错误弹窗

用户确认适用于全应用的意外错误。普通成功、输入校验、网络中断、超时和明确的业务/登录失效提示保留原有呈现。

## 实现边界

- `UserNoticeHolder.showFailure` 继续接收原始异常；生产实现对意外异常显示统一 `AppDialog`，不再先转成用户文案而丢失堆栈。显式 catch 可以调用 `reportUnexpected(error)`，返回 true 时不重复显示原 Toast。
- `MainActivity`、`AccountLoginActivity` 和独立进程 `StudentMailWebActivity` 都在各自现有主题内安装 Host，支持 Miuix、Material、RUI。只有 Activity 为 RESUMED 时创建 Dialog；后台报告在内存中等待恢复，Activity 重建不会丢失待处理报告，也不持有旧 Activity。
- 文案为「出现意料外的错误，请联系开发者。以下为错误信息」。错误、cause、suppressed、源码行、版本和设备信息可滚动、选择、复制。错误不额外写盘，不自动发送给任何人。
- 三按钮为「取消」「复制」「反馈」。取消删除当前报告；复制复制脱敏报告并保留弹窗；反馈打开既有 QQ 群 1006203134；QQ/TIM 无法打开时显示群号并尝试复制群号，用户仍可取消或复制报告。
- 每进程最多等待 3 条报告，同一报告在显示期间及 60 秒内去重；追踪生成时限制 48,000 字符。取消异常静默；明确的 IO/HTTP/考勤业务异常保留普通提示。独立插件不属于主应用编译依赖，分类出口不直接引用插件异常类型。
- 脱敏在入队之前完成，对 Cookie、Authorization、密码/token/sid/ticket/账号字段、邮箱、常见学号和 URL 查询/片段与用户信息移除或替换。复制与 UI 展示使用同一份脱敏文本。异常消息中的任意无标签自然语言个人信息无法靠格式规则完全识别，用户反馈前仍可检查已展示内容。
- 接入了成绩、天气、空教室、失物招领旧 catch 中保留 Throwable 的错误出口，资料模块由本次资料改动同步接入。普通 `AhuResult.Failure` 和已有内联业务错误仍保持原样；已在底层丢弃 Throwable 的失败不会凭空生成虚假追踪。此弹窗处理可恢复错误提示，不替代系统崩溃处理或 Sentry。

## 验证

新增 JVM 测试覆盖已知失败/意外错误分类、取消（包括包装 cause）、cause/suppressed 保留、敏感字段脱敏、畸形 URL、追踪长度、队列容量、重复风暴和恢复消费。

新增 Android 测试 `UnexpectedErrorDialogWindowTest`，在 `emulator-5554` 安装最终 Debug 和测试包后执行，结果 `OK (2 tests)`：

- 使用真实 MainActivity Host：退至后台后产生带 cause 和合成敏感字段的报告，确认后台不弹窗；回前台和重建 Activity 后报告仍可见；点击复制并检查真实剪贴板含追踪及版本、敏感字段已脱敏；取消消费报告。
- 使用真实 Dialog 窗口遍历 Miuix、Material、RUI，在长追踪下验证三按钮可见与可点击；反馈按钮使用回调替身，不打开 QQ 或发送消息。QQ/TIM 的实际唤起及未安装时的群号后备提示未作端到端验证。

最终 `testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease` 全部通过，共 17 模块 1,127 项 JVM 测试（含本模块分类、报告与队列的 15 项测试）。Release 的 R8 混淆和资源优化实际执行成功，mapping 和最终配置保留源码行信息；本机调试签名的优化包可冷启动到登录页。错误弹窗设备验证在最终 Debug 包完成，未将其宣称为 Release 登录后的完整运行验证。完整命令及源检测交互验证见 [资料源验证记录](2026-10-23-study-source-health.md#验证记录)。
