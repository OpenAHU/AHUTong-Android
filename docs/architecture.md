# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构，用 Gradle 依赖图强制分层边界。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0 | 完成 | 模块脚手架、依赖规则文档 |
| Phase 1 | 完成 | `core:*` 抽离（common/model/designsystem/datastore/network/sdk-api） |
| Phase 2 | 完成 | SDK api/impl 倒置，迁入 `RustSDK` / `LocalServiceClient` |
| Phase 3 | 进行中 | 首批 `data:schedule` / `data:auth`；其余域仍在 app 门面 |
| Phase 4 | 未开始 | `feature:*` / `platform:*` |
| Phase 5 | 未开始 | 删除兼容门面、收紧可见性 |

## 已建模块

```text
:app
:core:common       # AppResult、AppContextHolder、ext、DES
:core:model        # 领域模型 + SDK 共享 DTO
:core:designsystem # Liquid 组件、Shape、导航动画工具
:core:datastore    # PreferencesManager（DataStore）
:core:network      # 网络默认配置（薄壳）
:core:sdk-api      # CampusNativeGateway 接口
:core:sdk          # RustSDK / LocalServiceClient / RustCampusNativeGateway
:data:schedule     # ScheduleRepository（Gateway + cache + crawler fallback）
:data:auth         # AuthRepository（Gateway + session + crawler fallback）
```

## 依赖规则

1. `feature` → 只能依赖 data 公开 API + core（designsystem/common/model）
2. `feature` 之间禁止互依；跨页由 `app` NavHost 中介
3. `data` → core；禁止依赖 feature/app
4. `core` 禁止依赖 data/feature/app
5. 业务代码优先依赖 Repository / `CampusNativeGateway`

## Phase 3 说明

- `ScheduleViewModel` / `LoginViewModel` 已注入 `ScheduleRepository` / `AuthRepository`
- `AHURepository` 对课表/登录改为委托上述 Repository（widget 等旧调用点兼容）
- App 侧适配：`AhuCache*Store`、`Crawler*Source`、`NativeCookieSyncer`
- 后续可继续拆：`data:grade` / `data:exam` / `data:campuscard` / `data:portal` …

## 包名策略

迁移期允许 **模块物理位置变更、Java/Kotlin package 暂不改**。
