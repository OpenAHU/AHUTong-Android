# 校园卡凭证自动恢复

基线：官方 `master`，`72eb6606`（3.4.6）。

校园卡充值账户、账单、电费、浴室与网费等 ycard 请求共用 `TokenManager`。原有 HTTP 401 刷新已能重试业务请求一次，但首次 OAuth 换取凭证失败时会直接向页面返回“校园卡登录凭证暂未就绪”。

## 原页面失败后的路径

| 入口 | 加载链与失败状态 | 原有后续操作 |
| --- | --- | --- |
| 主页“充值” | `card_balance_deposit` → `CardBalanceDeposit` 的进入 effect → `CardBalanceDepositViewModel.load()` → `RepositoryCardRechargeSource.account()` → `AHURepository.getCardInfo()` → `ensureYcardCredential()`；失败转 `CardAccountState.Error`，页面显示“加载失败”与 `￥--` | “加载失败”只是文本，没有点击重试；银行充值确认依赖 `Ready`，账户失败时不可提交。退出后重进会重新加载；支付宝入口是独立跳转逻辑。 |
| 主页“账单” | `billing` → `BillingViewModel.refresh()` → 并行加载汇总及第一页；凭证获取失败经 `AHURepository` 返回“校园卡登录凭证暂未就绪”，列表转 `ListState.Error` | 错误卡重试调用 `refresh()`，清空分页状态并重取汇总与第一页；汇总单独失败不阻塞列表。 |

`SdkDataSource` 和 `CrawlerDataSource` 的校园卡接口最终使用 `YcardApi.authorizedCall()`。此次将首次凭证获取失败的恢复放在二者共同依赖的 `TokenManager`，使原页面加载可以直接继续。

## 自动恢复

现在同一次加载会在凭证层完成恢复：

1. 优先复用当前身份的内存 Token。
2. 没有 Token 时探测校园卡 SSO 并换取 OAuth Token；获取失败时重新探测一次，取得新的单次票据，不重放旧票据。
3. 任一次探测遇到 CAS 登录页时，用保险箱中保存的凭据续期一次，再获取新的校园卡票据和 Token。续期失败或再次取不到 Token 就结束，沿用现有错误提示和登录态处理。
4. 成功取得凭证后，原来的页面加载继续执行，无需退出页面重新进入。

业务接口返回 HTTP 401 后仍只刷新并重试一次。一般业务错误、扣款失败和结果不明的网络异常不会新增支付重放；不会重新执行整笔充值流程。OAuth 重取最多一次，CAS 续期最多一次；CAS 的超时、失败冷却、并发锁与账号代号校验沿用现有实现。退出或切换账号后，不继续为旧请求续期或保存 Token。

回归测试：`CampusCardTokenRecoveryTest` 覆盖 OAuth 首次失败后恢复、CAS 续期、OAuth 重取后才发现 CAS 过期、次数上限、空白 Token 与取消；`YcardUnauthorizedRetryTest` 补充重试后仍为 401 时不再次提交的用例。

## 验证记录

- 已运行 `adb devices`，检测到 `emulator-5554`。Debug APK 构建、保留数据安装和 `MainActivity` 启动均成功，版本为 `3.4.6-debug / 304010`。
- 最终测试全部通过：校园卡恢复 9 项、票据解析 5 项、401 重试 5 项、抢先体验仓库 19 项，以及 `:core:network:testDebugUnitTest` 34 项，合计 72 项，无失败或跳过；其中后 19 项用于伴随的主页性能改动回归。

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.crawler.manager.CampusCardTokenRecoveryTest' --tests 'com.ahu.ahutong.data.crawler.manager.CampusCardCredentialTest' --tests 'com.ahu.ahutong.data.crawler.api.ycard.YcardUnauthorizedRetryTest' --tests 'com.ahu.ahutong.data.preview.EarlyAccessRepositoryTest' :core:network:testDebugUnitTest :app:assembleDebug
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity
```

本机全局 Gradle 镜像脚本会破坏既有 JitPack 排除规则；首次标准命令因此未能解析锁定的 Monet / PersistentCookieJar。成功验证使用项目同版本的原版 Gradle 9.2.0、已有独立真实 Gradle 用户目录、只读依赖缓存，以及忽略目录 `build/payment-validation.init.gradle` 和 `build/review-maven` 中的临时构件镜像。使用前已逐项核对仓库 `verification-metadata.xml` 中的构件 SHA-256；项目依赖、锁文件、校验 metadata 与全局配置均未修改。GuiXu 子模块按 master 的固定版本初始化以补齐许可证资产。

设备只读冒烟：使用本次 Debug 的真实数据源打开账单后成功显示流水；充值页成功显示数值账户余额，无“凭证未就绪”或“加载失败”。未点击确认充值或支付宝入口，未发起交易。脱敏状态证据位于忽略的 `build/release-home-check/payment-smoke-sanitized.json`。

Pixel 9 Pro 接入后，也已保留数据安装 `3.4.6-debug / 304010` APK 并启动；由用户自行处理新版政策页后，同账号与真实数据源核对通过。性能实验结束后已恢复包含最终修复的正常 Debug 及原背景状态，并再次只读验证：账单显示 11 行流水、无错误；充值账户显示数值余额、无“加载失败”；随后回到主页，全程未发起交易。脱敏摘要为 `build/pixel-home-check/final-payment-smoke-sanitized.json`。真机上的此项检查是安装与会话冒烟，不代表主动制造了凭证过期。

恢复失败与取消等分支由 JVM 测试注入验证；尚未在学校实际服务中主动制造 Token 失效或重放真实扣费。测试账号和密码未写入源码、报告或提交。
