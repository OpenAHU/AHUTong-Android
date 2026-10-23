# ADR 0001：统一错误模型

- 状态：已决定（P0），P2 落地（2026-09-13 按实现回填，见下文"落地情况"）
- 日期：2026-09-12
- 关联：`docs/architecture/CONTEXT.md`

## 背景

当前同一条数据链路存在三套错误表达：

- `data/AHUResponse.java`：业务层返回值，带 `msg` 字符串；
- Kotlin `Result<T>` / `runCatching`：仓储层大量使用；
- 各协议适配器自行决定失败语义（`ApkUpdatePolicy.validate` 返回 `Result`，登录返回 `AHUResponse`）。

后果：UI 需要同时理解三种形状；错误分支靠 `msg` 字符串判断，协议变更时无法区分"密码错误"与"学校系统改版"；测试只能断言字符串。

## 决策

引入唯一错误类型 `AhuError`（放在 `:core:common`），所有跨模块边界的结果都用它：

```kotlin
sealed interface AhuError {
    data object Network : AhuError                       // 连接失败、DNS、TLS
    data object Timeout : AhuError
    data class Unauthorized(val message: String) : AhuError                 // 会话失效，需重新登录；message 保留上游原文
    data class ProtocolChanged(val detail: String) : AhuError               // 解析失败：学校改版
    data class Server(val code: Int, val message: String) : AhuError        // 上游明确的业务错误
    data class Unknown(val message: String) : AhuError                      // 无法归类；正常路径不应产生
}
```

规则：

1. 模块对外只返回 `AhuResult<T>`（成功或 `AhuError`），不再暴露 `AHUResponse.msg`。
2. "解析不出来"必须映射为 `ProtocolChanged`，而不是 `Unknown`——它决定了 UI 提示与埋点。
3. 用户可见文案由 UI 层根据 `AhuError` 决定，错误类型本身不携带中文字符串。
4. 迁移期允许 `AHUResponse → AhuResult` 的适配函数存在，但只能有一处，且在 P2 结束时删除。

## 落地情况（2026-09-13 回填）

规则 1（只返回 `AhuResult`）与规则 4（适配函数只留一处并删除）已达成：`AHUResponse` 与其桥接已从代码中删除。
规则 1 的最后一处边界（`EvaluationRepository` 曾把 Kotlin `Result` 交给自己的 ViewModel）也已在 2026-09-14 收口；此后 `Result` 只允许出现在模块内部的私有辅助函数里，不再跨越边界。
规则 2（解析失败 → `ProtocolChanged`）本轮补齐：网关里 7 处"解析失败"原先落在 `Server(-1, …)`，现已改为 `ProtocolChanged(…)`——用户可见文案不变，但分支语义正确、可埋点。

规则 3（错误类型不携带中文）**刻意偏离**，理由与代价都记录在此：

- `Unauthorized(message)` / `Server(code, message)` 会携带**上游原文**，目的是保持既有提示文案不丢（例如"课表响应缺少数据"），这是 P2 迁移期明确选择的取舍；
- 文案翻译函数 `toUserMessage()` 目前位于 `data`（`AhuErrorMapping.kt`），而不是展示侧。等 P3 抽出 feature 模块时，它应随展示侧一起搬走；在那之前它是"唯一的翻译点"，这一点比位置更重要。

同理，异常 → 错误的映射只有一处（`Throwable.toAhuError()`），任何新增的解析 catch 都应当映射为 `ProtocolChanged`，而不是 `Server(-1, …)`。

## 后果

- 正面：错误分支可穷举（`when` 编译期检查）、可测试、可埋点；协议改版能第一时间区分出来。
- 负面：一次性改动面较大（仓储层与 ViewModel 都要跟着改），因此按 P2 的分步实施，旧类型保留一版。
