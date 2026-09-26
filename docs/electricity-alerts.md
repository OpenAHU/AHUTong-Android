# 电费余额预警

入口是电控缴费页面右上角的提醒图标，打开独立的电费预警页面，默认关闭。预警页内置独立的电控、校区、楼栋、楼层与房间选择器；选好后添加当前房间，可加入多个房间，各自设置 1–30 天阈值，默认 3 天。预警页的选择状态不会改变缴费页当前选择，再次加入同一房间不会覆盖阈值。

登录成功或恢复本机已登录账号后，在前台检查是否需要更新预测；设置变更也会触发检查。缺少预测文件的房间先查询余额与过去 30 个完整自然日的用电记录。正常余额按预测检查日期等待，到达或超过该日再确认实际余额。每个账号下的每个房间，每天最多一个自动查询周期（一次余额与一次完整历史，只有服务端确实分页才继续取页）；页面主动查询、选择器与充值后的刷新不受自动预警限额影响。

HAR 的 A/B/C 余额与历史均为度数。合并同日记录，排除当天尚未完整的读数，重建最近 30 个完整自然日。根据用户确认的房间用电语义，完整查询中没有记录的日期按真实零用电处理；部分日期没有用电不是坏数据，也不会因稀疏直接禁止预测。协议变化、单位错误、余额缺失、缺页等真正的查询失败仍不会被当作零用电或零余额。

预测候选为旧算法（30 天与最近 7 天日均耗电量的较大值）、简单指数平滑、Holt 阻尼趋势及间歇用量 TSB。内层滚动回测比较未来 1/3/7 天累计耗电误差，简单候选须改善超过 5%，趋势模型须进一步改善超过 10%；外层再用三天累计量检查整个选模策略，改善不足 5% 或历史少于 26 天时使用旧均值。TSB 分别平滑正用电的发生概率与发生时的用量，零日参与概率更新，未来尚未观测的日子不假定为零。

用 1000 条模拟路径先计算各自累计量，再取累计耗电的经验 90 分位，并使它不低于中央累计预测，作为风险曲线。均值/指数平滑采用三日残差块重采样，TSB 采用固定末态概率的 Bernoulli 发生及缩放后的历史正量重采样。实际余额小于等于阈值天数对应的风险量时提醒；该分位是波动余量，不是严格 90% 覆盖率或准确率。预计到阈值的确认日期提前一天，最长 30 天复查；中央预测在 90 天内首次耗尽余额的日期向上取整，超出范围则显示未知。实际余额恢复后重算。零余额立即提醒；全零历史没有正用量规模，不能推断无限续航，次日重查。具体公式、选模与模拟假设见 [算法研究](electricity-forecast-research.md)。

提醒放在全局导航层，在前台已登录页面均可展示；支付二维码可见时保留待提醒状态，在关闭二维码后展示。一天最多弹出一次，多个不足房间合并在一个弹窗内，可选择要充值的房间。“是，去充值”打开电控缴费并填好对应电控、校区、楼栋、楼层和房间。

设置和预计归零标记分别保存在应用私有目录 `files/electricity-alerts/<账号SHA-256>-settings.json` 与 `*-forecast.json`，通过临时文件、磁盘同步和原子替换写入。账号间隔离，不保存凭据。请求日期在联网之前保存，失败、取消与进程重启不能绕过当日自动请求限额；提示日期在展示交接前保存。导航或二维码/前后台切换不会取消已开始的展示交接，退出账号与关闭预警会清空待提醒状态。R8 保留预测模型的 JSON 字段。

预测标记的算法版本为 `2`，记录模型名称及前 30 天累计风险量；同一天改阈值可以复用相应风险量，不额外请求。旧标记在下一次允许自动请求时重算，升级不能绕过当天已消耗的请求槽。公开预测结果增加有默认值的 `riskKwhByDays` 与 `method` 字段，旧 JSON 缺少这些字段时仍可读取。非有限的预测值会被拒绝，检查器保留当日请求槽并继续检查其它房间。

最近房间现在在成功查询后保存，并按完整房间身份去重、保留最近 12 个；付款确认标记保留。删除操作是房间卡片内部的 × 按钮。

房间预填前同步清除旧房间详情；新房间查询失败时不可提交旧房间的充值。登录刷新失败路径也修正了 `commitIfCurrent` 内再次获取同一非可重入锁的死锁，失败类型先在提交锁外读取，仍保留会话代号校验。

Miuix 0.7.2 的下拉菜单由 Activity 的宿主绘制，放在独立的 Compose Dialog 中会错用坐标并显示在弹窗后方。共享选择组件检测到 Dialog 窗口后改用窗口内的原生菜单；普通页面继续使用 Miuix 选择器。此修正同样适用于其它 Miuix 弹窗中的下拉字段，无新增依赖。

## 接口与依赖影响

增加 `ElectricityAlertSettings` 配置接口、`ElectricityUsageSource` 查询接口及房间/预测模型；feature 通过接口读写，存储和 Ycard 授权请求实现在 app 装配。无新增 Android 权限。DoH 新增与既有 OkHttp 相同的 `okhttp-dnsoverhttps:5.1.0`，范围见 [DoH 说明](aliyun-doh.md)，依赖锁和校验和一并更新。app 锁文件补全构建窗口回归测试所需的既有 AndroidTest 配置，未包含本机 UTP 设备测试工具自动生成的配置；既有应用编译、运行和单元测试依赖版本保持不变。

## 验证

相关 Gradle 任务：`:data:recharge:testDebugUnitTest :core:network:testDebugUnitTest :feature:recharge:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`。测试覆盖 HAR 协议、分页、稀疏/重复记录、预测日期、阈值、关闭状态、多个房间、充值恢复、请求失败、账号隔离、重启限额、导航取消和二维码排除。

2026-09-26 的整包检查：app 全部 315 项测试中 307 项通过、8 项失败。失败涉及 `AppActionCatalogTest` 的 2 项既有路由缺失、LiquidGlass 和 Radiant 的 2 项旧布局断言、`NavigationObservationPolicyTest` 的 4 项旧 pager 断言；与 `030e6169` 基线比对，导致失败的路由、主题和导航输入及断言未被本任务改变。新独立预警路由已单独补充动作目录映射。

相关 app 回归可独立运行：`:app:testDebugUnitTest --tests '*Electricity*' --tests '*electricityAlertSettingsRoute*' --tests '*PaymentQr*' --tests '*AhuSessionContractTest'`，同时运行 data/recharge、core/network、feature/recharge 的完整 Debug 单元测试。新增预填失败安全测试已先复现旧房间详情残留，再验证修复；续期失败死锁测试同样先超时复现再验证修复。独立设置页测试验证选择房间 B 不改变已打开缴费页的房间 A。

上述相关回归及 `:core:designsystem:testDebugUnitTest` 共 124 项单元测试通过，`:app:assembleDebug` 成功。按项目规范先执行 `adb devices`，在 `MR9PGE6T855TVW7L`（Redmi 2311DRK48C，Android 16）安装 Debug 包并启动成功；已观察到独立预警页、独立选择器及真实多房间不足提醒。跨自然日提醒、实际付款后恢复及校园 IPv6 网络仍需要持续实机验证，测试没有提交充值交易。

窗口级回归：`:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ahu.ahutong.ui.components.DialogDropdownWindowTest`，上述 Redmi 设备上 1 项通过。测试复现应用的 Activity 宿主与独立 Dialog，检查菜单在活动窗口可见、靠近选择框，点击房间 B 后显示与回调均更新。测试仅使用内存假房间，不写真实预警设置、预测标记或支付数据。启动通过测试框架的 shell 权限调用 `am start`，避免 OEM 后台启动限制；等待生命周期最多 15 秒，测试总超时 60 秒。

注意本机 AGP 的 UTP 配置为 `uninstall_after_test: true`，上述 Gradle 设备任务在结束时卸载了 Debug 应用并清除了该包的数据，正式版未受影响；已重新安装 Debug 包。需要保留测试包数据时，用 `:app:assembleDebugAndroidTest` 构建测试 APK、`adb -s <serial> install -r` 安装，再直接执行 `adb -s <serial> shell am instrument -w -e class com.ahu.ahutong.ui.components.DialogDropdownWindowTest com.ahu.ahutong.debug.test/androidx.test.runner.AndroidJUnitRunner`；结束后仅卸载 `.debug.test` 包，并重新启动 Debug 应用。

最终 fixture 使用项目真实 `AHUTheme`（Miuix / 跟随系统），上述直接设备回归在同一 Redmi 上通过，耗时 4.562 秒；截图确认深色主题文字与菜单可见、菜单紧贴选择框下方。仅卸载测试包后，Debug 应用冷启动 `Status: ok`，耗时 3.375 秒。

算法版本 2 的验证命令：`:data:recharge:testDebugUnitTest :app:testDebugUnitTest --tests '*Electricity*' :app:assembleDebug`，使用上述离线缓存参数及 `--no-daemon`。data/recharge 的 37 项完整单元测试与 app 的 21 项相关测试共 58 项全部通过，Debug 构建成功。覆盖公式手算对照、负预测的原始状态更新、TSB 零日概率、未观测未来不更新概率、真实间歇零日、累计风险单调性与可重复性、日期极值、旧缓存升级和风险量提醒。一次构建因 Windows 上已有 Gradle daemon 占用 `classes.jar` 失败，停止该 daemon 后重新运行成功；未修改源代码绕过失败。

HAR 回放仅保存脱敏的每日用电量。下面是最终策略在第 21–27 天各训练前缀上预测随后三天的累计绝对误差，除以三天及七个起点后的结果，单位为度/天：

| 房间样本 | 旧算法 MAE | 新策略 MAE | 使用完整 30 天历史时选择 |
| --- | ---: | ---: | --- |
| A | 1.688877 | 2.286870 | 旧均值 |
| B | 1.613401 | 1.639232 | TSB，正量 α=0.2，概率 β=0.05 |
| C | 0.648675 | 0.635409 | SES，α=0.1 |

这些窗口相互重叠，只有三个房间；短训练前缀可能没有足够数据启用外层策略，使用完整历史时的模型也不同。因此这组结果不能证明新算法整体更准，A/B 的部分预测仍变差。外层门槛降低误选风险，但不保证后续改善；模拟风险曲线的实际覆盖率还需未来真实用电量验证。未将早期原型较好的结果替代最终实现结果。

这轮构建前检测到同一 Redmi 设备，执行 `adb -s MR9PGE6T855TVW7L install -r app/build/outputs/apk/debug/app-debug.apk` 更新成功，再执行 `shell am start -W -S -n com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity`，冷启动 `Status: ok`、耗时 2.996 秒，进程存活且未出现该进程的 `AndroidRuntime:E` 日志。本轮未卸载 Debug 应用或执行清除数据，也未使用会自动卸载应用的 UTP 设备任务。该验证证明安装与启动成功，尚未验证未来真实提醒准确率。

本机全局 `aliyun-mirrors.init.gradle.kts` 与项目同时定义 `exclusiveContent`，导致 Monet 无法解析。验证时临时移除项目这一重复过滤，并将 JitPack 指向本机原版本缓存，使用 `--offline --no-configuration-cache --init-script build/cached-jitpack.init.gradle`；验证后恢复项目配置。缓存工件与项目既有 SHA-256 一致，正式依赖版本和仓库配置没有因此改变。

交付前清理：删除 117 行仅用于本机 UTP 设备工具的锁项，以及窗口回归测试的临时日志和截图代码，保留位置、活动窗口可见性与选择回调断言。app 原有编译、运行和单元测试配置的依赖集合仅增加 `okhttp-dnsoverhttps:5.1.0`；构建窗口回归 APK 所需的 AndroidTest 锁项保留。

清理后重新执行 `:data:recharge:testDebugUnitTest :core:network:testDebugUnitTest :feature:recharge:testDebugUnitTest :core:designsystem:testDebugUnitTest :app:testDebugUnitTest --tests '*Electricity*' --tests '*PaymentQr*' --tests '*AhuSessionContractTest' :app:assembleDebug :app:assembleDebugAndroidTest`，共 133 项单元测试通过（分别 37/23/27/7/39 项），两种 APK 均构建成功。仍使用上述本机缓存处理与 `--no-daemon`；受限执行环境未加载镜像初始化文件时离线解析失败，使用完整的用户 Gradle 初始化配置后通过。项目仓库配置原样恢复。

同一 Redmi 使用 `install -r` 更新应用与测试 APK，直接执行上述 `am instrument`，窗口回归 1 项通过、耗时 4.385 秒。仅卸载 `.debug.test` 包，再启动 Debug 应用：`Status: ok`、冷启动 3.455 秒，进程存活且无该进程的 `AndroidRuntime:E` 日志。本轮没有清除或卸载 Debug 应用，不依赖 UTP 设备工具配置。
