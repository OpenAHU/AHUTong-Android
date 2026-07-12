# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App 壳** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 完成 | 八大 data 域 + 校历 |
| Phase 4 | 完成 | 主要 feature 模块 |
| Phase 5 | 完成 | 删除 `AHURepository` 门面 |
| Phase 6 | **完成** | 域 sink 下沉 + 删除 DataSource 门面 |
| Phase 7 | **完成** | 适配器下沉进 `:data/*` / `:feature/*`，app 变薄壳 |

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

- 各域 **Repository 实现 + LocalStore/CrawlerSource 绑定** 均在对应 data 模块 Hilt Module 内完成。
- `:data:crawler`：Jwxt/Adwmh/Ycard API、Cookie/Token、AhuTong 服务端、灰度。

### Feature

```text
:feature:login / schedule / home / grade / exam / payment
:feature:portal / calendar / tools / settings / weather / classroom / repository
```

- Home 偏好绑定：`feature:home` 的 `HomeDataBindingsModule`

### App 壳（应只保留装配）

- `AHUApplication` / `MainActivity` / `Main` 导航 / `BottomNavBar` / `AHUTheme`
- 微件、通知、APK 更新 UI/VM
- Debug + mock 源集
- 仍依赖 app 的薄适配：
  - `AppScheduleReminderCoordinator`（通知调度）
  - `AppCourseReminderActions`
  - `AppFreeClassroomSource`（debug `MockCampusData`）
  - `CrawlerAuthInstaller` / `AppDataAccess` / `DataEntryPoint`

## 可选后续

1. 通知/微件抽成独立模块，进一步清空 app 适配  
2. Debug 下沉或拆成 debug-only feature  
3. 收紧 `internal` API  
