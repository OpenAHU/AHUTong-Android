# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构，用 Gradle 依赖图强制分层边界。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0 | 完成 | 模块脚手架、依赖规则文档 |
| Phase 1 | 完成 | `core:*` 抽离 |
| Phase 2 | 完成 | SDK api/impl 倒置 |
| Phase 3 | 进行中 | `data:schedule` / `auth` / `grade` / `exam` 已落地 |
| Phase 4 | 未开始 | `feature:*` / `platform:*` |
| Phase 5 | 未开始 | 删除兼容门面、收紧可见性 |

## 已建模块

```text
:app
:core:common / model / designsystem / datastore / network / sdk-api / sdk
:data:schedule   # 课表
:data:auth       # 登录会话
:data:grade      # 成绩 + GPA 排名
:data:exam       # 考试安排
```

## 依赖规则

1. `feature` → data 公开 API + core
2. `feature` 互不依赖
3. `data` → core；禁止依赖 feature/app
4. 业务优先 Repository / `CampusNativeGateway`

## Phase 3 进度

| 域 | 模块 | ViewModel |
|----|------|-----------|
| 课表 | `data:schedule` | `ScheduleViewModel` 注入 |
| 登录 | `data:auth` | `LoginViewModel` 注入 |
| 成绩 | `data:grade` | `GradeViewModel` 注入 |
| 考试 | `data:exam` | `ExamViewModel` 注入 |

`AHURepository` 对以上四域委托 Repository；其余能力仍在门面内。

后续可拆：`data:campuscard` / `data:portal` / `data:update` …

## 包名策略

迁移期允许模块搬家、package 暂不 rename。
