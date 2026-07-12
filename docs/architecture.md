# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | **基本完成** | 七大 data 域已落地 |
| Phase 4 | 未开始 | Feature 模块 |
| Phase 5 | 未开始 | 删除门面、收紧可见性 |

## 已建 data 模块

```text
:data:schedule
:data:auth
:data:grade
:data:exam
:data:campuscard
:data:portal
:data:payment      # 浴室/校园卡充值下单
```

## Phase 3 进度

| 域 | 模块 | UI |
|----|------|-----|
| 课表 | schedule | ScheduleViewModel |
| 登录 | auth | LoginViewModel |
| 成绩 | grade | GradeViewModel |
| 考试 | exam | ExamViewModel |
| 校园卡查询 | campuscard | DiscoveryViewModel |
| 失物招领 | portal | LostFoundViewModel |
| 充值支付 | payment | Bathroom/CardBalanceDepositViewModel |

`AHURepository` 仍残留：**校历**（`getSchoolCalendar`）、Mock 数据源初始化。

电费缴费目前直接打 YcardApi（未走 Repository），可后续并入 `data:payment`。

## 下一步

1. 校历小模块或并入 schedule  
2. Phase 4：`feature:home` / `feature:schedule` …  
3. 逐步删除 `AHURepository` 门面  
