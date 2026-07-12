# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 完成 | 八大 data 域 + 校历 |
| Phase 4 | 完成 | 主要 feature 模块 |
| Phase 5 | **完成** | 删除 `AHURepository` 门面 |

## 模块一览

### Data

```text
:data:schedule / auth / grade / exam / campuscard / portal / payment / calendar / crawler
```

### Feature

```text
:feature:login / schedule / home / grade / exam / payment
:feature:portal / calendar / tools / settings / weather / classroom / repository
```

### App 装配

- Hilt bindings（`AppDataBindingsModule`）
- 非 Hilt 访问：`AppDataAccess` + `DataEntryPoint`（微件、TokenAuthenticator）
- 仍留在 app：Debug、域 sink 装配（`Crawler*Source` 等）、AHUCache / 微件 / 通知

## Phase 5 说明

- **已删除** `AHURepository` object 门面。
- 微件课表：`AppDataAccess.scheduleRepository()`
- 会话重登：`AppDataAccess.authRepository().login(...)`
- `initializeDataSource` 已移除（字段从未被读取；Mock 由 `AHUCache.getMockData()` + 各源自行分支）。

## 可选后续

1. Debug 页面（强依赖 app mock source set / 灰度 / AHUCache）  
2. 收紧 `internal` 可见性与 public API  
3. 域 sink（`Crawler*Source`）可再下沉到对应 `:data:*` 模块（需处理 AHUCache 依赖）  

## data:crawler 说明

- 下沉：`JwxtApi` / `AdwmhApi` / `YcardApi`、`CookieManager`、`TokenManager`、拦截器、`AHUCookieJar`、剩余 crawler models。
- 会话标记：`AppSessionState`（`:core:common`），替代 `AHUApplication.sessionExpired`。
- 重登：`TokenAuthenticator` 通过 `CrawlerAuthHooks`；app 在 `Application.onCreate` 调用 `CrawlerAuthInstaller.install`。
- 域 sink（app 装配，已内联爬虫逻辑）：
  - `CrawlerScheduleSource` / `CrawlerExamSource` / `CrawlerGradeSource` / `CrawlerCampusCardSource`
  - portal/payment 直连 Adwmh/Ycard API
- **已删除** 门面：`CrawlerDataSource` / `SdkDataSource` / `BaseDataSource` / `MockDataSource`（mock 走 `AHUCache.getMockData()` + 场景控制器）。

## feature:repository 说明

- 学习资料 GitHub 浏览器：`GitHubApi` / `RepositoryManager` / `Repository` UI + downloads。
- `RepositoryManager` 使用 `AppContextHolder`（不再依赖 `AHUApplication`）。
- FileProvider 仍由 app manifest 提供（`${packageName}.fileprovider`）。

## feature:settings 主壳说明

- `Settings` 已迁入 `:feature:settings`。
- App 通过回调注入：检查更新、清除会话、更新日志、当前用户展示。
- 应用图标用 `PackageManager.getApplicationIcon`，避免 feature 依赖 app 资源。
- **Debug** 仍留 app：绑定 debug/release `MockScenarioController`、`GrayReleaseManager`、`CookieManager`/`TokenManager`、`CourseReminderScheduler` 等。

## Splash / Setup 说明

- 协议门闸 `Splash`、品牌 `setup.Splash`、`Setup` 壳 → `:feature:login`（同意/登录状态由 app 回调注入）。
- 完善信息 `Info` → `:feature:schedule`（依赖 `ScheduleViewModel`）。
- `:feature:login` 对 `:feature:schedule` 为 `api` 依赖（Setup 嵌套 Info）。
