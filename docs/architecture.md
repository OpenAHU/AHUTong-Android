# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 完成 | 八大 data 域 + 校历 |
| Phase 4 | **进行中** | Feature 模块（主要业务 feature 已落地） |
| Phase 5 | 未开始 | 删除门面、收紧可见性 |

## 已建 data 模块

```text
:data:schedule
:data:auth
:data:grade
:data:exam
:data:campuscard
:data:portal
:data:payment
:data:calendar
```

## 已建 feature 模块

```text
:feature:login
:feature:schedule
:feature:home
:feature:grade
:feature:exam
:feature:payment
:feature:portal     # 失物招领
:feature:calendar   # 校历
```

## Phase 3 一览

| 域 | 模块 | UI |
|----|------|-----|
| 课表 | schedule | ScheduleViewModel（feature:schedule） |
| 登录 | auth | LoginViewModel（feature:login） |
| 成绩 | grade | GradeViewModel（feature:grade） |
| 考试 | exam | ExamViewModel（feature:exam） |
| 校园卡查询 | campuscard | DiscoveryViewModel（feature:home） |
| 失物招领 | portal | LostFoundViewModel（feature:portal） |
| 充值支付 | payment | Bathroom/CardBalance/ElectricityDepositViewModel（feature:payment） |
| 校历 | calendar | SchoolCalendarViewModel（feature:calendar） |

`AHURepository` 现主要为兼容门面（委托各 Repository）+ Mock 数据源初始化。

电费已并入 `data:payment`（`postFeeItemThirdData` / `postPayForm` + `PaymentLocalStore`）。

## Phase 4 进度

| Feature | 状态 | 说明 |
|---------|------|------|
| login | **完成** | AuthSessionStore + AuthRuntimeReset |
| schedule | **完成** | ScheduleWeekResolver + ScheduleReminderCoordinator |
| home | **完成** | HomePreferences；Mock 刷新由 app 注入 |
| grade | **完成** | GradeLocalStore |
| exam | **完成** | ExamLocalStore |
| payment | **完成** | 电费 HTTP/缓存经 payment 仓库 |
| portal | **完成** | LostFoundLocalStore + 失物招领 UI |
| calendar | **完成** | 校历 UI；FileUtils 下沉 core:common |

## 下一步

1. 其余 feature（tools / settings / free classroom / weather 全页 …）按需继续拆分  
2. 逐步删除 `AHURepository` object（Phase 5）  
