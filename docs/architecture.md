# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App 宿主** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 下沉 |
| Phase 3 | 完成 | 按域 data 层 |
| Phase 4 | 完成 | 主要 feature 模块 |
| Phase 5 | 完成 | 删除 `AHURepository` 门面 |
| Phase 6 | 完成 | sink 下沉 + 删除 DataSource 门面 |
| Phase 7 | 完成 | 适配器下沉进 `:data/*` / `:feature/*` |
| Phase 8 | 完成 | 拆掉错误的「shell 大杂烩」：宿主回 app，可拆部分独立模块 |
| Phase 9 | 完成 | APK 更新 / Debug 从 app 下沉为 feature 模块 |

## 模块一览

### Core

```text
:core:common / model / designsystem / datastore / network / sdk-api / sdk
```

- `AHUCache`、`DebugClock`、`PreferencesManager` → `:core:datastore`
- `AppSessionState` / `AppContextHolder` / `AppVersion` / `AppLaunchIntents` → `:core:common`
- **`:core:designsystem` = 统一 UI 库**（见下文）

### UI 库契约（`:core:designsystem`）

Feature / App 的 Compose UI 栈只应依赖本模块，**不要**在 feature 的 `build.gradle.kts` 里再直接声明：

- `androidx.compose.*` / `material3` / `navigation-compose` / `hilt-navigation-compose`
- `monet` / `kyant0.capsule` / `kyant0.backdrop` / `coil-compose`

本模块用 `api(...)` 透出上述依赖，并提供统一组件：

| 包 | 内容 |
|----|------|
| `ui.theme` | `AhuDimens` / `AhuColors` / `AhuTheme` |
| `ui.components` | `AhuScreen` / `AhuPageHeader` / `AhuCard` / `AhuList*` / `AhuPrimaryButton` / Liquid* … |
| `ui.shape` | `SmoothRoundedCornerShape` |
| `utils` | `animatedComposable` |

Feature 仍需启用 Compose 编译器（`kotlin.compose` 插件 + `buildFeatures.compose = true`），但库依赖只写：

```kotlin
api(project(":core:designsystem"))
```

页面代码可继续使用 Compose 布局原语（`Modifier` / `Row` / `LazyColumn`），视觉元素优先用 `Ahu*` 组件与 token，避免各模块各自抄一份圆角 / 间距 / 色板。

特例：

- `:feature:widget` 额外依赖 Glance（桌面微件）
- `:feature:notification` 无 Compose UI

### Data

```text
:data:schedule / auth / grade / exam / campuscard / portal / payment / calendar / crawler
```

- 各域 Repository + LocalStore/CrawlerSource 在对应 data 模块 Hilt 装配
- `:data:auth`：`CrawlerAuthInstaller` + `AuthEntryPoint`（爬虫鉴权接线）
- `:data:schedule`：`CourseTimetable`、`ScheduleEntryPoint`、`ScheduleReminderCoordinator` 接口

### Feature

```text
:feature:login / schedule / home / grade / exam / payment
:feature:portal / calendar / tools / settings / weather / classroom / repository
:feature:widget          ← 课表微件 + 资源 / Manifest receivers
:feature:notification    ← 课前提醒 + 调度绑定
:feature:update          ← 应用内 APK 检查 / 分片下载 / 更新对话框
:feature:debug           ← Debug 屏（Mock / 灰度 / Cookie / 课前提醒调试）
```

- `:feature:classroom`：`AppFreeClassroomSource` 绑定 + debug/release Mock 场景数据
- `:feature:notification`：绑定 `ScheduleReminderCoordinator` / `CourseReminderActions`
- `:feature:widget`：Glance / 自适应微件；经 `ScheduleEntryPoint` 取课表，经 `AppLaunchIntents` 打开宿主
- `:feature:update`：`ApkUpdateViewModel` + `ApkUpdateDialog`；宿主只负责安装权限与 FileProvider 安装
- `:feature:debug`：Debug UI；Mock 数据仍在 classroom，Debug 依赖 schedule/home/notification 做联调

### App 宿主（真正的产品入口）

`:app` 负责**可安装包与组合根**，不是业务域模块：

| 保留 | 说明 |
|------|------|
| `AHUApplication` | `@HiltAndroidApp` + 进程初始化 |
| `MainActivity` | 启动 Activity + APK 安装权限 / FileProvider |
| `Main` / `BottomNavBar` | 导航组合根 |
| `AHUTheme` | 应用主题 |
| 打包资源 | 启动图标、主题、`network_security_config`、jniLibs |

微件 / 通知的组件 Manifest 由 library merge 进 APK。

## 可选后续

1. `:feature:debug` 改为 debug-only source set / product flavor（Release 不打入）  
2. 收紧 `internal` API  
3. 将 `CourseReminderActions` 接口从 settings 挪到 notification，去掉反向依赖  
4. `AhuTong` / `ApkUpdatePolicy` 从 crawler 挪到更合适的 data/update 域
