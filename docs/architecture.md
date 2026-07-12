# AHUTong 模块化架构

## 目标

将单体 `:app` 拆为 **Core + Data(按域) + Feature + App** 的混合模块结构，用 Gradle 依赖图强制分层边界。

## 当前进度

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 0 | 进行中 | 模块脚手架、依赖规则文档 |
| Phase 1 | 进行中 | `core:*` 抽离（common/model/designsystem/datastore/network/sdk-api） |
| Phase 2 | 未开始 | SDK api/impl 倒置，迁入 `RustSDK` |
| Phase 3 | 未开始 | `data:*` 按业务域拆分 |
| Phase 4 | 未开始 | `feature:*` / `platform:*` |
| Phase 5 | 未开始 | 删除兼容门面、收紧可见性 |

## 已建模块

```text
:app
:core:common       # AppResult、AppContextHolder、ext、DES
:core:model        # 领域模型（暂保持包名 com.ahu.ahutong.data.model）
:core:designsystem # Liquid 组件、Shape、导航动画工具
:core:datastore    # PreferencesManager（DataStore）
:core:network      # 网络默认配置（薄壳）
:core:sdk-api      # CampusNativeGateway 接口
```

## 依赖规则

1. `feature` → 只能依赖 data 公开 API + core（designsystem/common/model）
2. `feature` 之间禁止互依；跨页由 `app` NavHost 中介
3. `data` → core；禁止依赖 feature/app
4. `core` 禁止依赖 data/feature/app
5. 业务代码禁止直接依赖 `RustSDK` / `LocalServiceClient` 实现（应走 `CampusNativeGateway`）

## 包名策略

迁移期允许 **模块物理位置变更、Java/Kotlin package 暂不改**，减少无意义 import 风暴。后续 Phase 再统一为：

- `com.ahu.ahutong.core.*`
- `com.ahu.ahutong.data.*`
- `com.ahu.ahutong.feature.*`
