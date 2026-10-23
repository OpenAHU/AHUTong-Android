# ADR 0003：存储分级（凭据 / 设置 / 缓存）

- 状态：已决定（P0），P2 落地
- 日期：2026-09-12
- 关联：`docs/architecture/CONTEXT.md`

## 背景

`data/dao/AHUCache`（985 行）同时承担：凭据（`saveWisdomPassword`）、按用户分箱的业务缓存（课表、成绩、考试）、界面偏好与功能开关。`PreferencesManager` 又用 DataStore 混装设置项与个性化开关。存储介质同时存在 MMKV、SecureStorage、DataStore、Room 四种。

后果：无法回答"这份数据丢了会怎样"，因此失败策略只能是"尽力而为"；迁移与清理逻辑散落在读写路径里（`initGetStringOrMigrate`）。

## 决策

按"丢失后果"分成三类，各有明确失败策略：

| 类别 | 语义 | 失败策略 | 实现 | 对外接口 |
|---|---|---|---|---|
| 凭据 | 丢了就要重新登录 | 失败即登出并提示 | `SecureStorage`（AES-GCM + Keystore） | `CredentialVault`（`:core:auth`） |
| 设置 | 不能丢 | 读失败回默认值，写失败重试一次 | DataStore | `SettingsStore`（`:core:storage`） |
| 缓存 | 可以丢 | miss 即回源，读失败当未命中 | MMKV / Room | `ProfileCache`、`BehaviorStore` |

规则：

1. 缓存绝不允许作为"唯一数据源"参与登录、支付等关键判定。
2. 旧 MMKV 明文数据保留**一个版本周期**的迁移读取；迁移成功后立即清除明文副本，并加测试固定这一行为。
3. 按用户分箱的键名规则集中在一处，避免各调用点自行拼接。

## 后果

- 正面：每份数据的失败行为可预期、可测试；明文残留有明确的清除时点。
- 负面：`AHUCache` 需要拆成三个实现并逐个替换调用点（P2.3），期间新旧并存，需避免同一 key 双写。
