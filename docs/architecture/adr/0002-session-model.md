# ADR 0002：会话模型（登录态唯一真相 + 有界刷新）

- 状态：已决定（P0），P2 落地
- 日期：2026-09-12
- 关联：`docs/architecture/CONTEXT.md`

## 背景

登录态目前是 `AHUApplication` 上的两个公开静态字段：`sessionExpired`（6 处读写，含 2 处 UI）与 `reLoginMutex`（声明后未使用）。网络层为此反向依赖业务层：`data/crawler/net/TokenAuthenticator` 直接调用 `AHURepository.loginWithCrawler()`，并在 `runBlocking` 中用存储的密码执行一次完整登录。

后果：

1. 网络层认识业务层，`data → data.AHURepository` 形成环，任何装配改动都可能引发登录时序问题；
2. 后台请求可以静默触发全量密码登录，协议变化或学校风控时表现为"卡住"，且难以观测；
3. 登录态没有单一 readable 的状态源，UI 只能轮询这个布尔值。

## 决策

1. `AhuSession`（`:core:auth`）是登录态唯一真相：

   ```kotlin
   interface AhuSession {
       val state: StateFlow<SessionState>     // Unknown / Anonymous / Authenticated / Expired
       suspend fun signIn(input: SignInInput): AhuResult<Unit>
       suspend fun ensureFresh(stamp: SessionStamp): Boolean
       suspend fun signOut()
   }
   ```

2. 网络层只依赖 `SessionExpiryHook`（`:core:network` 声明，`:core:auth` 实现），不再 import 任何业务类型。
3. `sessionExpired` 与 `reLoginMutex` 删除；保留现有 `SessionRefreshCoordinator` 的 generation 机制（它防止并发重复登录，是正确的），把 generation 收进 `AhuSession` 实现内部。
4. **有界恢复**：同一服务范围、同一代会话的并发请求共享一轮恢复和 30 秒预算。网络、超时、HTTP 408/429/5xx 及暂时不可读的安全存储允许最多两次重试，退避为 500 ms、1500 ms；连续两次明确凭据拒绝，或确认本地缺少身份/密码，才发布 `Expired` 并停止自动密码尝试，直到人工登录。网络故障耗尽预算仍保留原登录态，冷却 15 秒后允许后续请求再试；冷却从恢复结束时计时。网页验证、WAF 和协议变化不重复提交密码。认证器对原业务请求最多重试一次，不因普通网络失败重放写操作。
5. 凭据只经 `CredentialVault` 读写（`SecureStorage` 实现）。
6. `Expired` 是会话层完成确认后的最终结果；界面不再发起额外密码探测或叠加过期弹窗，只在登录页统一提示并引导重新登录。邮箱等派生服务无权把教务的全局过期状态改回已认证。

## 后果

- 正面：登录态可观察、可测试；网络层与业务解耦；后台恢复统一受服务范围、预算和重试次数限制，界面不再竞争发起密码探测。
- 负面：临时失败后的下一轮恢复依赖冷却结束后的新请求；网页验证和协议变化仍需用户或服务端处理。无设备时，JVM 回归不能替代实际网络和页面交互验证。
