# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 进行中 | 六大 data 域已落地 |
| Phase 4 | 未开始 | Feature 模块 |
| Phase 5 | 未开始 | 删除门面、收紧可见性 |

## 已建模块

```text
:core:common / model / designsystem / datastore / network / sdk-api / sdk
:data:schedule
:data:auth
:data:grade
:data:exam
:data:campuscard
:data:portal          # 失物招领
```

## Phase 3 进度

| 域 | 模块 | UI |
|----|------|-----|
| 课表 | schedule | ScheduleViewModel |
| 登录 | auth | LoginViewModel |
| 成绩 | grade | GradeViewModel |
| 考试 | exam | ExamViewModel |
| 校园卡 | campuscard | DiscoveryViewModel |
| 门户/失物 | portal | LostFoundViewModel |

仍在 `AHURepository` 门面：**缴费下单**、**校历**。

下一步建议：`data:payment`，或 Phase 4 `feature:*`。
