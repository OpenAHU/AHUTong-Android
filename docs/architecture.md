# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App 壳** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 说明 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 下沉 |
| Phase 3 | 完成 | 按域 data 层 + 校验 |
| Phase 4 | 完成 | 主要 feature 模块 |
| Phase 5 | 完成 | 删除 `AHURepository` 门面 |
| Phase 6 | **完成** | 各 sink 下沉 + 删除 DataSource 门面 |
| Phase 7 | **完成** | 适配器下沉进 `:data/*` / `:feature/*` |
| Phase 8 | **完成** | 产品宿主 `:feature:shell`；`:app` 仅保留入口 |

## 模块一览

### Core

```text
:core:common / model / designsystem / datastore / network / sdk-api / sdk
```

- `AHUCache`、`DebugClock`、`PreferencesManager` → `:core:datastore`
- `AppSessionState` / `AppContextHolder` / `AppVersion` → `:core:common`

### Data

```text
:data:schedule / auth / grade / exam / campuscard / portal / payment / calendar / crawler
```

- 各域 **Repository 实现 + LocalStore/CrawlerSource 等** 在对应 data 模块 Hilt Module 中装配。
- `:data:crawler`：Jwxt/Adwmh/Ycard API、Cookie/Token、AhuTong 后端、灰度等。

### Feature

```text
:feature:login / schedule / home / grade / exam / payment
:feature:portal / calendar / tools / settings / weather / classroom / repository
:feature:shell   ← 产品宿主（导航 / 主题 / 微件 / 通知 / Debug）
```

- Home 偏好绑定：`feature:home` 的 `HomeDataBindingsModule`
- **`:feature:shell`**：MainActivity、Main 导航、BottomNavBar、AHUTheme、
  微件与课前提醒、APK 更新 UI/VM、Debug + mock、
  `CrawlerAuthInstaller` / `AppDataAccess` / `DataEntryPoint`、
  `AppScheduleReminderCoordinator` / `AppCourseReminderActions` /
  `AppFreeClassroomSource`

### App 壳（仅入口）

`:app` **只保留可安装包入口**，不再承载业务代码：

- `AHUApplication`（`@HiltAndroidApp` + 进程级初始化）
- 应用级 Manifest（权限、application 声明；组件由 shell merge）
- 启动器图标 / 启动主题 / `network_security_config`
- `applicationId`、版本号、ProGuard、jniLibs（Rust SDK）

依赖：`implementation(project(":feature:shell"))` 及 Application 初始化所需的少量 core/data。

## 可选后续

1. 通知 / 微件拆独立模块，进一步拆薄 shell  
2. Debug 下沉为 debug-only feature  
3. 收紧 `internal` API  
