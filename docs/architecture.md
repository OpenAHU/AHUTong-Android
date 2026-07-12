# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 完成 | 八大 data 域 + 校历 |
| Phase 4 | **基本完成** | 主要 feature 模块已落地 |
| Phase 5 | 未开始 | 删除门面、收紧可见性 |

## 已建 data 模块

```text
:data:schedule / auth / grade / exam / campuscard / portal / payment / calendar
```

## 已建 feature 模块

```text
:feature:login
:feature:schedule
:feature:home          # 依赖 :feature:weather / :feature:schedule
:feature:grade
:feature:exam
:feature:payment
:feature:portal
:feature:calendar
:feature:tools         # 小工具 + 电话本
:feature:settings      # Preferences / License / Contributors（Settings 壳仍在 app）
:feature:weather       # 天气全页 + WeatherApi
:feature:classroom     # 空闲教室
```

## 仍在 `:app` 的页面/装配

- `Settings` 主页（登出、清数据、版本等强耦合逻辑）
- `Debug`、`Splash` / `Setup` / `Info`
- 资料库 `Repository` / `RepositoryDownloads`
- 导航装配、`AHURepository` 门面、crawler/API 绑定

## Phase 4 进度

| Feature | 状态 |
|---------|------|
| login / schedule / home / grade / exam / payment | **完成** |
| portal / calendar | **完成** |
| tools / settings / weather / classroom | **完成** |

## 下一步

1. **Phase 5**：收缩/删除 `AHURepository`，收紧模块可见性  
2. 可选：拆 `Settings` 主页、资料库、Debug  
