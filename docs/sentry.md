# Sentry 崩溃上报

应用通过 [Sentry Android SDK](https://docs.sentry.io/platforms/android/) 上报崩溃与性能数据，与现有的 Bugly 并存。

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

应用启用现有 Sentry Android SDK 的 Tombstone integration。Android 12+ 在下次启动时读取系统 ApplicationExitInfo，可补充各线程、ART 和系统库符号，并与现有 NDK 事件合并；旧系统仍由 NDK 集成采集。未启用历史 Tombstone 回传或原始 protobuf 附件。

这是后续诊断信息的补齐，不能视为已解决系统 libhwui/ART 崩溃。自有 native 库仍需上传与 APK 匹配的符号。

参考：https://docs.sentry.io/platforms/android/configuration/tombstones/

手动发布 CI 在独立步骤上传一次 mapping，并在服务器发布成功后自动 finalize release、记录 deploy。普通自动检查和构建保持空 Sentry token。不要同时开启 Gradle 真上传和发布步骤的 CLI 上传。
