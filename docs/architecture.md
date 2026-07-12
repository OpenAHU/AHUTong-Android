# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 完成 | 八大 data 域 + 校历 |
| Phase 4 | **进行中** | Feature 模块（login 已落地） |
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
:data:calendar     # 校历图片下载/缓存
```

## 已建 feature 模块

```text
:feature:login     # Login 页 + LoginViewModel + 登录资源
```

## Phase 3 一览

| 域 | 模块 | UI |
|----|------|-----|
| 课表 | schedule | ScheduleViewModel |
| 登录 | auth | LoginViewModel（已迁入 feature:login） |
| 成绩 | grade | GradeViewModel |
| 考试 | exam | ExamViewModel |
| 校园卡查询 | campuscard | DiscoveryViewModel |
| 失物招领 | portal | LostFoundViewModel |
| 充值支付 | payment | Bathroom/CardBalanceDepositViewModel |
| 校历 | calendar | SchoolCalendarViewModel |

`AHURepository` 现主要为兼容门面（委托各 Repository）+ Mock 数据源初始化。

电费缴费仍直连 YcardApi，可后续并入 payment。

## Phase 4 进度

| Feature | 状态 | 说明 |
|---------|------|------|
| login | **完成** | UI/VM/资源迁入；会话清理经 `AuthSessionStore` + `AuthRuntimeReset` |
| schedule | 未开始 | 课表页 + ScheduleViewModel |
| home | 未开始 | 首页与卡片组件 |

## 下一步

1. `feature:schedule` / `feature:home` …
2. 逐步删除 `AHURepository` object  
3. 电费并入 `data:payment`  
