# 学生邮箱登录 HAR 与现有实现对照（2026-10-22）

本次只读检查 `D:/Coding/AHUTong-Evolved/captured-har/mail-login.har` 与本库 `app/src/main/java/com/ahu/ahutong/data/mail/StudentMailSession.kt`，补查 `StudentMailProtocol.kt` 的跳转解析和 `StudentMailRepository.kt` 的 SID 使用。没有修改邮箱实现，没有重放请求，没有读取或输出账号、Cookie 内容、SID/ticket 值、验证码或请求体值。下文只保留路径、字段名、HTTP 状态和一致性检查结果。

## HAR 能直接确认的流程

共有 321 条 entry，其中 305 条经过 WebVPN，另 16 条为浏览器附加资源。该文件中 `/cas/login`、`/domain/oa/Entry`、`generateSsoUrl` 请求数均为 0，不能用于验证最初的 CAS 或门户 SSO 交换。

以下 entry 序号从 0 开始；`<proxy>` 替换 WebVPN 的代理路径段。

| Entry | 请求路径 | HTTP 状态 | 观察结果 |
|---|---|---|---|
| 6 | `GET /https/<proxy>/static/login/sms/index.html` | 200 | 抓包已在邮件短信验证阶段 |
| 24 | `GET /https/<proxy>/login/sms/send_code.jsp` | 200 | 请求发送短信验证码 |
| 28 | `POST /https/<proxy>/login/mobileLogin` | 302 | 跳转到 `/http/<proxy>/redirect`，目标有 `sid` 字段 |
| 29 | `GET /http/<proxy>/redirect` | 302 | 跳转到 `/http/<proxy>/static/sirius-web/jump/index.html`，目标仍有 `sid` 字段 |
| 30 | `GET /http/<proxy>/static/sirius-web/jump/index.html` | 200 | jump 页面地址有 `sid` 字段 |
| 后续 17 条 | `/http/<proxy>/js6/s` | 全部 200 | 请求均带 `sid`，其中 16 条 POST、1 条 GET |

`mobileLogin` 的表单字段名为 `_version`、`account_name`、`autoEntry`、`code`、`domain`、`p`、`pass_2fa`。`/redirect` 的 query 字段包含 `sid`，jump 页的 query 字段为 `from`、`hl`、`origin_uid`、`show_old`、`sid`。`/js6/s` 包含 `sid`、`func`、`p` 及客户端元数据字段。

在内存中比较后确认：`mobileLogin` 响应目标、`/redirect` 请求及响应目标、jump 页请求，以及全部 17 条 `/js6/s` 使用相同的 SID；报告不保存该值。HTTP 200 仅证明这些 HTTP 请求成功响应，不代替每个业务响应内容的功能验收。

整个 HAR 的 request/response cookie 数组均为空，也没有 Cookie/Set-Cookie header。因此该文件不能证明真实 Cookie 的属性、哪些响应写入邮箱 Cookie、冷启动后的 Cookie 恢复是否正确。

## 与现有代码的支持边界

| 环节 | 源码结论 | 证据与限制 |
|---|---|---|
| `/redirect` → Sirius jump 的 302 链 | 支持对应的直连路径结构 | `StudentMailSession.follow()` 手动跟随 301/302/303/307/308，`connect()` 从最终页面地址读取 `sid`；jump 地址本身已有 SID，此阶段不需要执行 jump JavaScript 来获得它 |
| `/js6/s` 使用同一 SID | 已有对应实现 | `StudentMailRepository` 将会话 SID 作为 query 参数；与 HAR 的 SID 连续性一致，但本次未对服务端重放 |
| 初始 CAS → 门户 → 邮箱入口 | 本 HAR 无法验证 | 抓包没有对应请求；本次不能用该文件为原流程或主审查的 CAS scope 修复作运行背书 |
| 短信验证码页和 `mobileLogin` 提交 | 当前未实现 | `StudentMailSession` 只处理 HTTP 跳转或惰性的 meta / `window.location.replace` 跳转，没有验证码获取、输入与提交分支；不能称该 HAR 的完整登录流程已支持 |
| 邮箱阶段的 WebVPN 代理路径 | 当前未实现 | `secureUrl(..., campus=false)` 仅允许邮箱和企业邮入口直连 host；WebVPN 只用于此前门户入口兜底，未提供邮箱请求代理 transport；不能直接接入本 HAR 的整个代理链 |
| Cookie 与加密会话恢复 | 本 HAR 无法验证 | 源码确有独立邮箱 jar 和加密会话持久化，但 HAR 未携带 Cookie 元数据，也没有应用冷启动/后台恢复证据 |

最终结论：**确认 `/redirect` → jump → `/js6/s` 的 SID/302 结构与现有直连实现相容；短信验证、邮箱 WebVPN 代理链不在当前实现中；该 HAR 不足以确认完整自动登录或冷启动登录保持已经通过实测。**

按主审查要求，本次不扩展短信或 WebVPN 新功能。报告仅为脱敏静态对照，不宣称邮箱网络/设备验证通过。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
