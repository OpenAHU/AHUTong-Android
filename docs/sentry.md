# Sentry 崩溃上报

应用通过 [Sentry Android SDK](https://docs.sentry.io/platforms/android/) 上报崩溃与性能数据，与现有的 Bugly 并存。

## 配置项

| 配置 | 位置 | 说明 |
| --- | --- | --- |
| DSN | `app/build.gradle.kts` → `BuildConfig.SENTRY_DSN` | 客户端公开标识，默认值已随仓库提供；可用 `-Psentry.dsn=...` 或环境变量 `SENTRY_DSN` 覆盖 |
| organization / project | `app/build.gradle.kts` 的 `sentry { }` | `openahu` / `ahutong-android` |
| 认证令牌 | 环境变量 `SENTRY_AUTH_TOKEN` | 仅构建期使用，不写入仓库 |

## 需要提供的凭据

- `SENTRY_AUTH_TOKEN`：用于上传 Release 包的混淆映射，建议在 Sentry 的 Settings → Auth Tokens 中创建，作用域包含 `org:read` 与 `project:releases`。
  未提供时构建正常进行，插件会跳过上传，Release 崩溃堆栈缺少符号化信息。
- 自建 Sentry 时还需要服务端地址：`sentry { url.set("https://sentry.example.com") }`。

## 运行时行为

- `AndroidManifest.xml` 中关闭了 `io.sentry.auto-init`，SDK 在 `AHUApplication#onCreate` 中手动初始化。
- Debug 包上报到 `debug` 环境，Release 包上报到 `release`；性能采样率分别为 `1.0` 与 `0.1`。
- 主界面每次回到前台时按本地日期检查；每天首次恢复时发送一条 `AHUTong DAU` 消息，携带 `dau_id` tag。可在 Discover 过滤该消息并用 `count_unique(tags[dau_id])` 按日统计。
- `dau_id` 是本地保存的随机安装标识，不含账号信息。同一安装跨天保持不变；清除应用数据后会重新生成。此统计按安装去重，同一人使用多个安装会被分别计数。

## 验证方式

1. `./gradlew :app:assembleDebug` 确认集成可通过编译。
2. 安装 Debug 包后临时调用 `Sentry.captureException(new Exception("test"))`，确认 Sentry 项目中收到 `debug` 环境的事件。
