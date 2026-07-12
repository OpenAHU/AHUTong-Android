# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0–2 | 完成 | Core + SDK 倒置 |
| Phase 3 | 完成 | 八大 data 域 + 校历 |
| Phase 4 | **进行中** | Feature 模块（login / schedule / home / grade / exam 已落地） |
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
:feature:schedule  # 课表页 + CourseCard* + ScheduleViewModel
:feature:home      # 首页 + 卡片/小组件 + DiscoveryViewModel + 天气 API
:feature:grade     # 成绩单 + GradeViewModel
:feature:exam      # 考场查询 + ExamViewModel
```

## Phase 3 一览

| 域 | 模块 | UI |
|----|------|-----|
| 课表 | schedule | ScheduleViewModel（feature:schedule） |
| 登录 | auth | LoginViewModel（feature:login） |
| 成绩 | grade | GradeViewModel（feature:grade） |
| 考试 | exam | ExamViewModel（feature:exam） |
| 校园卡查询 | campuscard | DiscoveryViewModel（feature:home） |
| 失物招领 | portal | LostFoundViewModel |
| 充值支付 | payment | Bathroom/CardBalanceDepositViewModel |
| 校历 | calendar | SchoolCalendarViewModel |

`AHURepository` 现主要为兼容门面（委托各 Repository）+ Mock 数据源初始化。

电费缴费仍直连 YcardApi，可后续并入 payment。

## Phase 4 进度

| Feature | 状态 | 说明 |
|---------|------|------|
| login | **完成** | UI/VM/资源迁入；会话清理经 `AuthSessionStore` + `AuthRuntimeReset` |
| schedule | **完成** | UI/VM 迁入；周次/提醒经 `ScheduleWeekResolver` + `ScheduleReminderCoordinator` |
| home | **完成** | 首页与卡片；布局偏好 `HomePreferences`；Mock 刷新由 app 注入 |
| grade | **完成** | 成绩单 + GradeViewModel；经 `GradeLocalStore` 访问缓存/学年 |
| exam | **完成** | 考场查询 + ExamViewModel；经 `ExamLocalStore` 访问缓存/用户 |

## 下一步

1. 其余 feature（tools / settings / payment UI …）按需继续拆分  
2. 逐步删除 `AHURepository` object  
3. 电费并入 `data:payment`  
