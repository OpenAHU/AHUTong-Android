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
4. **有界刷新**：自动刷新只允许发生一次且带超时；失败即返回 `Unauthorized`，由 UI 引导用户重新登录。禁止在后台用存储凭据无限重试。
5. 凭据只经 `CredentialVault` 读写（`SecureStorage` 实现）。

## 后果

- 正面：登录态可观察、可测试；网络层与业务解耦；后台不再有隐式重登。
- 负面：`Unauthorized` 的 UI 处理路径需要补齐（当前依赖 `sessionExpired` 的全屏弹窗逻辑），这部分要在 P2 一并改造并回归验证。
