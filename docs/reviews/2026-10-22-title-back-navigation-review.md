# 子页面标题返回入口核对（2026-10-22）

本项按主任务分工独立核对 Main 的静态路由、Debug 动态诊断路由、应用声明的 Activity，以及本地编辑器/详情/网页弹窗。Material、Miuix 的二级及以下页面使用公共返回按钮；四个一级页 `home`、`schedule`、`xuexiaotong`、`settings` 不因此新增返回入口。Radiant 和旧 LiquidGlass 原路径保留。

## 本项实现

`app/src/debug/java/com/ahu/ahutong/personalization/diagnostics/DebugDiagnosticsContribution.kt`：

- `installRoutes()` 为动态路由 `debug_model_diagnostics` 显式传入 `navController.popBackStack()`。
- 仅 Material/Miuix 的诊断页接入 `AppPageScaffold(title, onBack)`，复用公共标题返回入口。
- Radiant 和旧 LiquidGlass 保留原 LazyColumn、卡片标题、间距和背景路径。Material/Miuix 的卡片内容也复用原列表，仅移除重复的卡片大标题。
- 诊断采样、暂停、悬浮球、`FLAG_SECURE`、生命周期和模型操作不变。

本项不运行 Git 或 Gradle，统一构建和界面验证由主任务执行。

## 非公共页面接入

以下增量全部只作用于 Material、Miuix，以 `!AppUiTheme.usesLiquidGlass` 判断；Radiant 与旧 LiquidGlass 使用原先的布局分支。

- 校园通知为既有 `AppPageScaffold` 补充 `onBack`，Main 显式传回上层的导航动作。
- 全部小工具、干饭足迹、必吃榜将原手绘返回键接到公共 `AppPageBackButton`，保持其原有导航语义。两个固定亮色的食堂页面显式传原文字颜色，避免应用暗色主题下返回图标看不清。
- Debug 自绘标题接到 `AppPageHeader`，Main 提供回调；Release 存根同步可选参数，不引入调试内容。灰度入口原有标题区域点击行为保留。
- 学习通的内嵌登录页获得返回标题，点击只将 `showLogin` 设回 false，与其系统返回一致；学习通一级页面没有新增返回按钮，登录页的纯色组件包与玻璃隔离保持原样。
- 登录页只有 `canCancelLogin()` 成立且未处于提交 / 成功落盘阶段才显示标题返回。按钮和系统返回调用同一处理函数，首次认证或已开始的登录仍由既有 gate 控制。
- 信息填写页新增可选回调，仅 Main 的普通 `info` 配置入口提供；首次 Setup 内不提供，因此不会新增绕过初设流程的返回按钮。
- 插件宿主在这两个主题内提供 `AppPageHeader`，标题按钮和系统返回共享 `dispatchBack()` 优先、未消费才 pop 的动作。插件不存在或创建 View 失败时，宿主返回仍可使用；插件自绘内容、主题边界和 ABI 不改变。

涉及文件：`app/.../ui/screen/Main.kt`、`main/CampusNoticeScreen.kt`、`main/MoreWidgetsScreen.kt`、`canteen/CanteenFootprintScreen.kt`、`canteen/CanteenRankingScreen.kt`、`setup/Login.kt`、`setup/Info.kt`、Debug / Release 的 `settings/Debug.kt`，以及 `feature/xuexiaotong/.../XuexiaotongScreen.kt`、`XuexiaotongLoginScreen.kt`。公共按钮与已有标题组件的统一由设计系统子任务完成。

## Main 路由核对表

| 路由/页面组 | 核对结论与返回语义 |
|---|---|
| `home` / `schedule` / `xuexiaotong` / `settings` | 底栏一级页面；不新增返回键。学习通自己的登录子页另按 `showLogin=false` 返回，不退出一级路由。 |
| `custom_courses?editId={editId}` | 列表返回上层导航；编辑器返回调用 `closeEditor()` 回列表，系统 BackHandler 同样先关闭编辑器。 |
| `widgets` | 全部小工具原有手写返回；页面接入由设置/UI 子任务处理。 |
| `campus_notices` | 原 AppPageScaffold 无 onBack；已交设置/UI 子任务和 Main 接线。 |
| `school_calendar` / `grade` / `phone_book` / `attendance_records` / `exam` / `evaluation` / `free_classroom` / `program_completion` / `lost_found` / `weather` | 调用链已有显式 onBack 或页面内 NavController pop；公共页壳的按钮统一会覆盖这些页面。 |
| `student_mail` | 复用页面的 `back` lambda：选择收件人先返回写信；内部详情先交 ViewModel；草稿 dirty/保存不确定时确认离开。不能改成直接 Nav pop。 |
| `repository` / `repository_dir?path={path}` | 标题返回 pop 一层导航；面包屑另由 `popToRepositoryPath` 按目录祖先关系多次 pop。Markdown 阅读弹窗关闭只 clearMarkdown，不返回上层目录。 |
| `repository_downloads` / `repository_settings` | 已使用 AppPageHeader(onBack=popBackStack)。 |
| `profile` / `settings__early_access` / `settings__privacy_policy` / `settings__license` / `settings__contributors` / `settings__theme_lab` | 已有页面 onBack 接线；公共设置/页面标题组件负责统一。隐私文本加载中的空白不应另触发导航。 |
| `preferences` | 保留 `navigateBackFromPreferences()` 的既有父路由规则：Radiant 回 settings，Material/Miuix 回 home，并处理缺失父栈；不替换成裸 pop。 |
| `electricity_pay` / `electricity_alert_settings` / `electricity_recent_rooms` | 已有显式上层导航回调；最近房间选中后也 pop，且保留支付页作用域的 ViewModel。 |
| `card_balance_deposit` / `cmb_card_recharge` | 同一个 CardBalanceDeposit 页面，已有 AppPageScaffold(onBack=popBackStack)。两条入口都覆盖。 |
| `billing` / `billing_stats` / `bathroom_deposit` / `network_recharge` | 已有 AppPageScaffold/显式返回。 |
| `canteen_footprint` / `canteen_ranking` | 原自绘标题/返回，公共按钮接入由设置/UI 子任务处理。 |
| `debug` | Debug 变体的自绘页面，接入由设置/UI 子任务处理；Release 存根需同步签名。 |
| `debug_model_diagnostics` | 不经过 animatedComposable，由 DiagnosticsContribution 动态挂载；本项已补 Material/Miuix 公共标题返回。Release 不安装该路由。 |
| `plugin/{pluginId}` | 统一由主任务/设置UI子任务处理宿主可控层。按钮与系统返回都应先 `host.dispatchBack()`，插件未消费才 Nav pop。插件未安装/视图创建失败时也需要可返回的宿主入口。 |
| `tools` | 兼容跳转到 widgets 的别名路由，不是稳定展示页面。 |
| `identity_code` | 拉起淘宝/浏览器后立即 pop 的过渡路由，不增加空壳标题。 |
| `splash` | 启动/隐私流程，不作为普通二级页面增加返回。 |
| `setup` / `login` / `info` | 初次配置、重新认证和信息填写有独立语义。登录提交或身份落盘期间禁止返回旧业务页面，首次 Setup 未登录时拦截系统返回。保留既有认证/初设门禁，不能由通用标题直接 pop 绕过。 |

## Activity、内部页面与弹窗

- Manifest 中的可见 Activity 为 MainActivity、AccountLoginActivity 和 StudentMailWebActivity。账号切换 Activity 复用 Login 的可取消条件；不能以统一标题返回覆盖 `canCancelLogin()` 与认证中的拦截。
- StudentMailWebActivity 的页面已经使用 `AppPageScaffold(onBack)`。标题上的返回含义是 `finish()` 回原生邮箱；系统返回先检查 WebView.canGoBack()，能回网页历史时先 goBack。Radiant/液态主题的“返回原生邮箱”操作保留。
- 学习通 WorkWebViewDialog 是本工程可控的全屏只读题目弹窗，现有自绘标题右侧关闭调用 onDismiss，含义是关闭题目，不是网页历史后退。WorkDetail、CourseDetail、Markdown 阅读等内容弹窗的返回/关闭也应先关闭弹窗，不能 pop 下面的 Nav 页面。
- LostFound 图片预览、全屏二维码、安全支付密码、扫描浮层、强制更新/下载中及隐私同意弹窗具有专用关闭/禁止取消行为，不当作普通 Nav 子页面重写返回。

## 插件统一的边界

Circle 的 Feed/Hot/Detail 由插件自己的本地状态维护，拥有自己的标题和内部返回；该 ComposeView 边界不会自动继承宿主的 CompositionLocal 主题。外部插件还可能完全自绘界面。统一公共标题仅能保证宿主可控层，不能据此声称插件内部 UI 已统一。

本项没有修改 Circle 内部标题/返回，没有新增插件 ABI、没有按插件 ID 特判，也没有改变第三方插件的内部导航。宿主按钮必须尊重已有 `dispatchBack()` 消费结果；插件未注册内部返回观察者时维持原有退出语义。实际插件界面、多个标题是否重叠和返回消费情况仍需实际插件包验证。

## 主审查交付验收

合入最新 develop 后，1,084 项单元测试、Debug / AndroidTest 构建、Release Kotlin 编译全部通过。最终 APK 安装启动及 3 项登录/Keystore 设备回归通过；Material/Miuix 实际返回与 Radiant 不新增按钮已抽查，未逐一打开所有业务页面或第三方插件内部 UI。详见[总审查与验证记录](2026-10-22-session-codebase-review.md)。
