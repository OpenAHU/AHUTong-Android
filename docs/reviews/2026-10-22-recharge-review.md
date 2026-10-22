# 电控缴费与低电提醒审查（2026-10-22）

基线：`upstream/develop`，`7f7d9c53`。本审查检查 `feature/recharge`、`data/recharge`、电控仓库适配器和校园卡授权调用；共享登录协调由主审查处理。测试账号和密码没有写入代码、测试或报告。

## 已确认并修复的源码缺陷

### 1. 无校园卡 token 时提示 `Response.error()`

原调用链为 `YcardApi.authorizedCall()` → `Response.error(401, 中文说明 body)` → `RepositoryElectricityDepositSource.request()` → `callAndParse()`。仓库读取 `response.message()`，拿到的是 Retrofit 人工响应的固定 `Response.error()`，而不是中文响应体。这能解释提示来源，但不足以断言某次现场 token 缺失的根因。

现在 `RepositoryElectricityDepositSource.kt:346` 将 401/403 映射为“校园卡登录暂不可用，请稍后重试”，其他 HTTP 失败保留状态码和清晰的服务错误提示；支付请求采用同样映射。没有依据瞬时 token 获取失败把全局会话标记为过期。

回归：`app/src/test/java/com/ahu/ahutong/data/adapter/ElectricityHttpErrorTest.kt`（2 个用例）。

### 2. 校园卡授权刷新失败后返回已关闭响应体

原 `YcardApi.authorizedCall()` 和 `enterBathroomFeeItem()` 在刷新 token 前关闭首个 401 的 `errorBody`，刷新失败却又把该响应交给调用者。读取响应体的仓库路径可能得到 `closed`，覆盖原本的认证失败信息。

现在两个入口共用 `YcardUnauthorizedRetry.kt:6`：刷新失败时保留响应体供调用者读取；成功重试前关闭旧响应；刷新抛异常或取消时关闭废弃响应并透传。仍然只重试一次，不自动重放非 401 或一般网络错误，也不更改支付写请求的重试策略。

回归：`app/src/test/java/com/ahu/ahutong/data/crawler/api/ycard/YcardUnauthorizedRetryTest.kt`（4 个用例），覆盖失败后可读、成功前关闭且只重试一次、非 401 不刷新、取消关闭并透传。

### 3. 已知零/负余额仍被历史记录接口失败挡住提醒

原 `RepositoryElectricityUsageSource.snapshot()` 在取得余额后，无条件读取完整历史，历史失败就整体返回失败。`ElectricityAlertChecker.kt:40` 当天不再请求该房间，因此即使已明确查到零/负余额，也可能整天没有提醒。

现在 `RepositoryElectricityUsageSource.kt:120` 的快照编排在余额 <= 0 时直接使用余额生成快照，不发送历史请求；`ElectricityUsagePredictor` 已有的零/负余额分支可以据此提醒。正余额仍要求完整历史；没有把历史接口失败当成零用电量。每日请求和弹窗限制维持现有约定。

回归：`app/src/test/java/com/ahu/ahutong/data/adapter/ElectricityUsageSnapshotTest.kt`（3 个用例），覆盖零/负余额跳过历史、正余额不忽略历史失败、只纳入此前 30 个完整日。

### 4. 电控选择请求取消被当成网络错误，旧请求清除新请求加载状态

原 `ElectricityDepositViewModel` 的五个 `fetch*` 方法统一捕获 `Exception`，包含 `CancellationException`；被取消的请求可以写出“网络错误”，且 `finally` 清除后来请求的 `isLoading`。重复点击重试也没有先取消旧请求。

现在这些方法透传取消；恢复房间和选项请求仅在自身仍活动时清除加载状态，重试先取消旧选择请求。回归在旧请求清理晚于新请求开始的时序下验证不出现错误、不提前取消加载提示。

回归：`feature/recharge/src/test/java/com/ahu/ahutong/ui/state/ElectricityDepositViewModelTest.kt` 新增 1 个用例；fake 仅增加可挂起的房间请求钩子。

## 按需求调整界面

`feature/recharge/src/main/java/com/ahu/ahutong/ui/screen/main/ElectricityDeposit.kt:512`：电控入口、校区（适用时）、楼栋、楼层、房间统一放在同一个 `AppSectionCard` 内，作为共享卡片中的多个选项行。电控缴费页和低电提醒房间设置页复用同一选择器。没有改变逐级选择规则。

公共组件参数影响：`AppSelectField` 末尾新增可选 `grouped: Boolean = false`，经 `AppComponentPack`、组件槽覆盖、Miuix 和 Radiant pack 完整传递。仅上述电控选择器启用，现有调用默认不受影响。Miuix 使用内嵌原生下拉；Radiant / Liquid Glass 不再为各行绘制独立 surface、描边或圆角；Material 在既有 exposed menu 锚点内显示无独立边框的标题/值行。菜单、占位值、禁用态和语义锚点保持可用。此项低影响视觉变更没有新增镜像单元测试，需主审查增量构建、安装后检查各主题显示。

## 证据边界与待验证项

- 本次确认的是源码可达缺陷。未通过账号实测复现“特定一次后台恢复后的电控失败”，没有把这些修复宣称为该现场问题的唯一根因。
- 主审查反馈：Sentry 最近 14 天 release 的 unresolved 列表中没有 `Response.error()` 或电控专属异常。该查询不覆盖全部历史与已解决事件；一般 timeout/连接异常尚不足以证明与此调用链相同。
- 共享 CAS 恢复、token 获取失败分类和全局登录状态由主审查同步处理，本子任务没有修改 `TokenManager`。
- UI 尚需设备检查不同主题、较长楼栋名称、加载中切换和下拉菜单定位；没有从源码断言视觉验收完成。
- 当前配置将首次自动查询失败也记为当日已尝试；已有测试明确约定此行为。这会使离线启动后当天不再自动检查，属于已存在的请求限额取舍。是否放宽重试需要独立确定限额策略，本次未修改。

## 验证记录

本子任务未独立运行 Gradle，避免与主审查争用构建。`git diff --check` 通过。新增逻辑测试 10 个，尚需主审查统一运行；当前状态为 **IMPLEMENTED / UNVERIFIED**。

主审查已检测到 `emulator-5554`；首次统一 Gradle 检查受既有 JitPack Monet / PersistentCookieJar 依赖解析失败阻塞，主审查正在处理验证环境。建议统一命令：

```powershell
adb devices
.\gradlew.bat :app:testDebugUnitTest --tests 'com.ahu.ahutong.data.adapter.ElectricityHttpErrorTest' --tests 'com.ahu.ahutong.data.adapter.ElectricityUsageSnapshotTest' --tests 'com.ahu.ahutong.data.crawler.api.ycard.YcardUnauthorizedRetryTest' --tests 'com.ahu.ahutong.electricity.ElectricityAlertCheckerTest' :feature:recharge:testDebugUnitTest --tests 'com.ahu.ahutong.ui.state.ElectricityDepositViewModelTest' :app:assembleDebug
```

设备可用时，构建成功后按主审查安排安装、启动并检查 UI。具体执行命令和结果以主审查最终验证记录为准；此处不提前宣称测试通过或真实缴费成功。

## 初次审查统一验证

初次审查在 `7f7d9c53` 基线上通过 1,083 项单元测试、Debug / AndroidTest 构建及 4 项设备测试；随后合入最新 develop 和子页返回改动的结果见总报告。各子项的实际验证范围与剩余限制，以[总审查与验证记录](2026-10-22-session-codebase-review.md)为准；本文前述未验证状态指并行子任务交付时。
