# AHUTong 架构上下文（模块职责与依赖规则）

> 本文档是"改代码前先看这里"的入口：它说明**每个模块负责什么、不负责什么、对外接口是什么**。
> 配套阅读：`docs/architecture/adr/`（关键决策）。
> 基线：`upstream/master` @ `aa40c6a4`。

## 1. 名词

| 名词 | 含义 |
|---|---|
| 模块 | 有接口与实现的整体。可以是一个类、一个包，或一个 Gradle 模块 |
| 接口 | 调用者必须知道的一切：签名、不变式、顺序约束、错误模式、配置要求 |
| 接缝 | 接口所在之处；能在"改这里之外的代码"时替换行为 |
| 适配器 | 在接缝上满足接口的具体实现 |

## 2. 依赖规则（硬约束，由 `ModuleBoundaryTest` 自动检查）

```text
:app（组合根：只做绑定与导航）
 ├─> :feature:*   ─> :core:* / :data:*
 ├─> :background  ─> 只读接口（不得触发登录、不得发起写请求）
 └─> :integration:native-sdk

禁止  :core:* / :data:*  ──> :feature:* / :ui
禁止  :feature:a          ──> :feature:b
禁止  网络层（crawler/net）──> 业务层（AHURepository / dao）
禁止  `utils`             ──> `data`（utils 是叶子）
禁止  在 `data/network` 之外构造 HTTP 日志拦截器（release 不得输出请求日志）
禁止  在 `data/network` 之外自行装配 `AutoLoginInterceptor` / `TokenAuthenticator`（用 `campusCookies` / `campusAutoLogin` / `campusSessionRefresh`）
禁止  在 `data/network` 之外构造 OkHttp 客户端（统一走 `AhuHttp.plain(...)`）
```

当前存量违规登记在 `docs/architecture/boundary-allowlist.txt`，**只减不增**。

## 3. 模块职责

| 模块 | 负责 | 不负责 | 对外接口 |
|---|---|---|---|
| `:core:model` | 领域模型、值对象 | 任何 IO、Android 依赖 | 数据类本身 |
| `:core:common` | 错误模型、Dispatcher、时钟、结果映射 | 业务语义 | `AhuError`、`AppClock` |
| `:core:designsystem` | 主题、形状、通用组件、动效令牌 | 取数、导航目标 | Compose 组件 |
| `:core:network` | 传输、超时、重试、Cookie、日志脱敏 | 认识业务类型 | `HttpTransport` |
| `:core:auth` | 登录态、凭据、验证码编排、会话刷新 | HTTP 细节、UI 呈现 | `AhuSession`、`CredentialVault`、`HumanChallenge` |
| `:core:storage` | 设置、按用户缓存的分级读写 | 凭据（归 auth） | `SettingsStore`、`ProfileCache` |
| `:data:ahu` | jwxt / adwmh / ycard 协议适配（含校方 DES） | 缓存、UI 文案、登录态 | `CampusGateway` |
| `:data:chaoxing` | 学习通协议与凭据 | 日历提醒调度 | `ChaoxingGateway` |
| `:data:repository-index` | 仓库索引与文档下载 | 页面排版 | `RepositoryIndex` |
| `:data:personalization` | 行为记录、端侧训练与推理、遥测 | Compose 宿主、UI 决策文案 | `BehaviorRecorder`、`PresetSuggestions`、`PersonalizationSettings` + 词汇表 |
| `:data:update` | 版本检查、下载、校验、安装意图 | 弹窗与进度 UI | `AppUpdater` |
| `:integration:native-sdk` | Rust 库加载、loopback 启停、端口与令牌 | 业务协议语义 | `NativeCampusService` |
| `:feature:*` | 单个界面的状态与交互 | 访问其它 feature、直接访问存储 | ViewModel + Screen |
| `:background` | 小组件、通知、提醒的渲染与调度 | 触发登录、发起写请求 | 只读模型 + 调度接口 |

表里 `:data:personalization` 的对外接口曾写成 `SuggestionProvider`、`TelemetrySink`：前者后来定名 `PresetSuggestions`，
后者**刻意没有建**——P4 收尾核对了调用点，非个性化层今天没有任何地方需要上报遥测，凭空造一个没有使用者的接口
违反「接口由使用者定义」。真出现第二个使用者再抽。

> 第 3 节的接口名是**目标词汇表**，不是今天的实现清单：`HttpTransport`、`CampusGateway`、`ChaoxingGateway`、
> `AppUpdater`、`HumanChallenge`、`ProfileCache`、`:data:ahu`、`:integration:native-sdk` 至今没有落地。
> 实际在跑的是 `AhuHttp`/`AhuRetrofit`、`:data:schedule` / `:data:grade` / `:data:recharge` / `:data:chaoxing` 各自的端口、
> `ApkUpdateChecker` + `ApkDownloader`，以及留在 :app 的 RustSDK 装配与验证码界面。改动前先看代码，别照着表找不存在的类。

## 4. 五条"各司其职"的判定问题

改动前问自己：

1. 我要改的行为，属于哪个模块的**负责**列？不在任何一列 → 说明还缺一个模块，先补接口。
2. 我要 import 的类型，是否跨过了第 2 节的禁令？跨了 → 说明接口没设计好，不要用 allowlist 绕过。
3. 这个改动会不会让**两个适配器**（生产 + 假实现）行为不一致？会 → 补一条契约测试。
4. 后台组件（widget / 通知 / 提醒）会不会因此触发登录或写请求？会 → 方案错了。
5. 凭据、密码、Cookie 是否经过了 `CredentialVault` / `SecureStorage`？没有 → 停下来。

## 5. 现状（基线事实，供对照）

- 单 Gradle 模块 `:app`；`app/src/main/java` 292 文件 / 67,075 行；JVM 单测 53 个文件。
- 已存在的真实接缝：`data/base/BaseDataSource`（适配器：`CrawlerDataSource`、`SdkDataSource`、mock）。
- 已做对的安全设施：`data/security/SecureStorage`（AES-GCM + Keystore，fail-closed）、`EncryptedCookiePersistor`、更新包签名指纹校验、`network_security_config` 仅放行 loopback 明文。
- 已知全局状态：`AHUApplication.getApp()`（20 处引用）、`AHUApplication.sessionExpired`（6 处）、`reLoginMutex`（声明后未使用）。
