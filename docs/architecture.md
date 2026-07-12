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
:data:schedule / auth / grade / exam / campuscard / portal / payment / calendar
```

### Feature

```text
:feature:login / schedule / home / grade / exam / payment
:feature:portal / calendar / tools / settings / weather / classroom
```

### App 装配

- Hilt bindings（`AppDataBindingsModule`）
- 非 Hilt 访问：`AppDataAccess` + `DataEntryPoint`（微件、TokenAuthenticator）
- 仍留在 app：Settings 主壳、Debug、Splash/Setup、资料库、crawler 实现

## Phase 5 说明

- **已删除** `AHURepository` object 门面。
- 微件课表：`AppDataAccess.scheduleRepository()`
- 会话重登：`AppDataAccess.authRepository().login(...)`
- `initializeDataSource` 已移除（字段从未被读取；Mock 由 `AHUCache.getMockData()` + 各源自行分支）。

## 可选后续

1. 拆 Settings 主壳 / 资料库 / Debug  
2. 进一步下沉 crawler（JwxtApi、AdwmhApi）出 app  
3. 收紧 `internal` 可见性与 public API  
