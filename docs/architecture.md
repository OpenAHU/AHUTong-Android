# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构，用 Gradle 依赖图强制分层边界。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0 | 完成 | 模块脚手架、依赖规则文档 |
| Phase 1 | 完成 | `core:*` 抽离 |
| Phase 2 | 完成 | SDK api/impl 倒置 |
| Phase 3 | 进行中 | schedule / auth / grade / exam / **campuscard** |
| Phase 4 | 未开始 | `feature:*` / `platform:*` |
| Phase 5 | 未开始 | 删除兼容门面、收紧可见性 |

## 已建模块

```text
:app
:core:common / model / designsystem / datastore / network / sdk-api / sdk
:data:schedule
:data:auth
:data:grade
:data:exam
:data:campuscard   # 余额 / 二维码 / 浴室状态
```

## 依赖规则

1. `feature` → data 公开 API + core  
2. `feature` 互不依赖  
3. `data` → core；禁止依赖 feature/app  
4. 业务优先 Repository / `CampusNativeGateway`

## Phase 3 进度

| 域 | 模块 | UI 注入 |
|----|------|---------|
| 课表 | `data:schedule` | ScheduleViewModel |
| 登录 | `data:auth` | LoginViewModel |
| 成绩 | `data:grade` | GradeViewModel |
| 考试 | `data:exam` | ExamViewModel |
| 校园卡 | `data:campuscard` | DiscoveryViewModel |

仍在 `AHURepository` 门面：缴费下单（ycard）、失物招领、校历等。

后续可拆：`data:payment` / `data:portal` / `data:update`，或进入 Phase 4 Feature。
