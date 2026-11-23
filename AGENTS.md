# AHUTong 开发与协作规范

本文件为 AHUTong 仓库唯一常驻开发规范。所有参与者及 AI Agent / Harness 修改仓库前必须遵守。

## 1. 分支与提交

1. 禁止直接在 `master` 或 `develop` 修改。编辑文件前必须从基线分支新建工作分支：
   - 功能：`p/{GitHub用户名}/feat/{小写短横线目标}`（如 `p/MuxYang/feat/schedule-week-selector`）
   - 修复：`p/{GitHub用户名}/fix/{小写短横线目标}`（禁止泛称 `feat`/`fix`）
   - 严禁在同一分支混入无关功能、大范围重构或全局重排。
2. 仅当用户明确要求合入 `master` 且合并成功、无冲突、当前不在 `master` 时，才删除已合入的本地与远端同名工作分支。
3. 提交信息遵守 Conventional Commits：`<type>(optional scope): <description>`（`feat`/`fix`/`docs`/`style`/`refactor`/`perf`/`test`/`build`/`chore`）。本地存在多个待推送提交时，push 前先 squash。
4. 禁止提交本地配置、密钥、账号、构建产物或临时文件（`local.properties`、`keystore.properties`、`*.jks`、`*.keystore`、`config.local.json`、`dist/ahup/`、`.work/`、`.codex-*/`）。

## 2. 项目架构与硬边界

代码变更必须符合以下分层与依赖方向，由 `ModuleBoundaryTest` 与 `FeatureTestOwnershipTest` 零豁免门禁强制检查：

```text
:app (组合根：Hilt 绑定、导航、遗留数据/爬虫/AI 适配)
 ├─> :feature:* (单域 UI + ViewModel) ──> :core:* + 对应 :data:* 接口
 ├─> :background (小组件/提醒/通知)   ──> 只读接口与窄视图（禁止写配置/触发登录）
 └─> :feature:circle (插件契约示范)   ──> :core:plugin-api / :core:designsystem / :core:common / :core:network
```

### 模块职责

| 模块组 | 职责 | 禁止事项 |
|---|---|---|
| `:core:model` | 领域模型、值对象、枚举 | 禁止依赖 Android 框架（`@NonNull` 除外）、IO、UI、其他业务模块 |
| `:core:common` | `AhuResult`/`AhuError`、`AppEnvironmentHolder`、`UserNoticeHolder`、诊断与时间原语 | 禁止依赖业务、UI、数据实现、SDK |
| `:core:designsystem` | `AHUTheme`、`AppComponentPack`、基础组件、动效、Haptics、Markdown | 禁止依赖数据、导航、ViewModel、存储、会话实现 |
| `:core:network` | `AhuHttp`、`AhuRetrofit`、`CampusHttp`、`NetworkLogging`、`AliyunDns`、`SessionRefreshCoordinator` | 禁止依赖业务仓储、缓存、凭据、UI |
| `:core:storage` | `SettingsStore`、`CourseReminderSettings` 等设置接口与失败策略 | 禁止存放凭据、业务实现、UI |
| `:core:auth` | `AhuSession`、`AhuSessionState`、`CredentialVault`、`SecureStorage` | 禁止依赖爬虫、缓存实现、UI |
| `:core:plugin-api` | v3 插件零第三方依赖契约（`PLUGIN_API_LEVEL = 1`、`AhuPlugin`、`PluginHostServices`） | 禁止在公开签名出现第三方库、Kotlin lambda、协程、Composable、`Result` |
| `:core:plugin-platform` | 独立插件编译期 BOM（版本须与 `gradle/libs.versions.toml` 同步） | 禁止引入未对齐宿主的库版本 |
| `:data:*` | 域端口接口、协议 DTO、纯业务策略与可测试计算 | 禁止依赖 `:app` 内部实现、`ui.*`、`reminder.*`、`notification.*`、`appwidget.*` |
| `:feature:*` | 页面状态（ViewModel）与 Compose UI，测试与 fake 同模块 | 禁止 `:feature:a` 依赖 `:feature:b`、直连爬虫/存储实现、把测试留在 `:app` |
| `:background` | Glance 小组件、课程与学习通提醒调度 | 禁止触发登录、静默续登、写设置、发起远端配置同步或网络写请求 |
| `sdk/` / `GuiXu-Rust/` | 原生 Rust 爬虫/加解密/本地 HTTP 桥接与嵌入式存储子模块 | 禁止在主仓随手改脏子模块；JNI/FFI 与 loopback 协议须保持向后兼容 |

### 强制契约

1. **错误边界**：跨模块返回统一使用 `AhuResult<T>` 与 `AhuError`；异常映射仅走 `Throwable.toAhuError()`；解析失败必须映射为 `AhuError.ProtocolChanged`；禁止跨边界暴露 `Result<T>` 或 `msg` 字符串判断分支；用户文案归展示侧。
2. **会话边界**：`AhuSession` / `AhuSessionState` 为全局登录态唯一真相；派生服务（邮箱、支付、一卡通子域）禁止改写全局 `AhuSessionState`。
3. **存储分级**：凭据走 `CredentialVault`/`SecureStorage`（AES-GCM + Keystore）；设置走 `SettingsStore`（DataStore）；缓存走按用户分箱存储（允许丢失回源，不得作登录/支付唯一依据）。
4. **网络唯一入口**：禁止在 `core/network` 之外出现 `OkHttpClient.Builder(`、`Retrofit.Builder(`、直接引用 `okhttp3.logging.*` 或自行装配 `AutoLoginInterceptor`/`TokenAuthenticator`。校内客户端统一使用 `AhuHttp`/`AhuRetrofit` 与 `CampusHttp` 扩展。
5. **代码位置**：Kotlin/Java 源码放在 `src/main/java`；`:feature:*` 的 ViewModel 测试与 fake 放在对应 `:feature:*` 模块的 `src/test/`，禁止放在 `:app`。

## 3. R8、安全与依赖约束

1. Release 构建启用 R8 full mode 与 `-repackageclasses`。任何通过 Gson/Retrofit 反射序列化、本地持久化、JNI/桥接调用的类与枚举，必须在 `app/proguard-rules.pro` 保留完整类与成员签名（`{ *; }`）。
2. 禁止在 `.kt`/`.java` 源码中硬编码 `BUGLY_APP_ID` 字面量、写入密钥（`CANTEEN_WRITE_KEY`）、私密 Keystore、Token 或个人本机路径（如 `C:\Users\...`、`/Users/...`）。
3. 禁止新增 Gradle/Python/Shell 脚本中硬编码个人路径或临时构建产物。
4. 依赖版本统一在 `gradle/libs.versions.toml` 声明。修改任何依赖版本或插件版本后，必须重新生成对应 `gradle.lockfile`（`--write-locks`）并同步维护 `gradle/verification-metadata.xml` 与 `:core:plugin-platform`。

## 4. UI 与代码风格

1. 遵循现有 Kotlin、Java、Compose 与 Gradle 写法；不为单次改动引入新框架或过度抽象。
2. UI 组件优先复用 `:core:designsystem`（`AHUTheme`、`AppComponentPack`、`AppButton`、`AppCard`、`AppDialog`、`SecondaryPageScaffold`、`AppHaptics`），ViewModel 注入统一使用 `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel`。
3. 界面指示、设置项描述等程序内文本必须为简洁、无废话的说明性文本，禁止包含冗长解释、错误处理说教、口语化或俚语。
4. 注释仅说明约束、契约或非显而易见的动机，不复述代码表面动作；修改保持局部最小化，避免无关文件重排与重复 import。

## 5. AI 开发硬性禁令

1. 禁止生成无用说明文档（如随意创建 `README.md`、`docs/*.md`、任务总结文档）。
2. 禁止为绕过 `ModuleBoundaryTest` 修改 `exceptPrefixes`、削弱规则或重新引入豁免清单；依赖越界必须通过下沉接口或适配器解决。
3. 禁止在非 `core/network` 模块新建 OkHttp/Retrofit 实例或在 release 开启明文请求日志。
4. 禁止把 `:feature:*` ViewModel 测试写回 `:app`，或在未跑通受影响测试时宣称验证通过。
5. 禁止通过本地脚本或绕过 `.github/workflows/publish.yml` 直接构建发布生产签名 APK。

## 6. 本地 Skills 路由

Session start 时扫描 `.agents/skills/*/SKILL.md` frontmatter；涉及以下任务时必须先读取对应 skill：

| Skill | 触发场景 |
|---|---|
| `ahutong-architecture-guard` | 新增/拆分模块、调整依赖、接口/适配器、会话/存储/网络/R8 规则修改 |
| `ahutong-ui-design` | 新增或重构页面、组件、主题槽位（Theme Park）、Liquid Glass、Haptics 与界面文案 |
| `ahutong-plugin-dev` | 开发/修改 `:core:plugin-api`、宿主插件加载器、内置或 `.ahup` v3 插件 |
| `ahutong-ci-verify` | 提交前全量自检、修复 CI 检查、Gradle 依赖锁定或构建脚本调整 |

## 7. 编译、测试与合并前验证

1. 准备编译运行前先执行 `adb devices`：
   - 有在线设备：构建 Debug 包、安装并启动验证（模拟 Android Studio 运行流程）；
   - 无在线设备：执行编译、检查与单元测试，不强行安装或启动。
2. 提交前本地必跑门禁（按改动范围覆盖，涉及跨模块或 CI 改动时全量执行）：
   - `python3 scripts/check_repo_hygiene.py`（Windows 可用 `python`）
   - `python3 -B -m unittest discover -s scripts -p "test_ci_*.py" -v`
   - 改动 Rust 时：`cargo test` 与 `cargo clippy`（`sdk/Cargo.toml`、`GuiXu-Rust/Cargo.toml`）
   - Gradle 门禁：`./gradlew --no-daemon testDebugUnitTest :app:compileDebugAndroidTestKotlin` 与 `./gradlew --no-daemon --no-configuration-cache :app:lintRelease :feature:circle:lintRelease`
3. 验证后在提交说明或汇报中写明：分支名、执行命令、设备状态与实际结果。
