# 子页面标题栏返回按钮（2026-10-22）

基线为主审查已同步的最新 `upstream/develop`（`b6961a3a`）。用户要求 Material 和 Miuix 所有子页面提供标题栏返回按钮，RadiantUI 维持现有行为。

## 公共组件

审查确认已有 `AppPageScaffold(title, onBack)`、`AppPageHeader(title, onBack)`、`SettingsPageLayout(title, onBack)` 均可渲染 Material / Miuix 返回键。缺漏主要来自页面未传入返回回调，或使用自绘标题。公共层不推导路由、不自动给主页面注入返回按钮。

新增公共 API：

```kotlin
@Composable
fun AppPageBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    tint: Color? = null
)
```

Material 复用既有 `AppHeaderIconButton` 的自动镜像返回图标；Miuix 继续使用原生 `MiuixIcons.Useful.Back` 与原有点击 haptic，统一为 48 dp 触控区。`tint` 用于食堂等自绘亮色页面，避免暗色全局主题使图标不可见；默认使用当前主题颜色。无新增依赖或导航隐式状态。

Material / Miuix 的公共标题行、Miuix 页面壳和设置页面壳统一使用该按钮。Radiant 页面壳继续保持原有不渲染返回键的行为；Radiant 标题行继续原来的 `AppHeaderIconButton` 路径，没有调整其标题、布局或交互。

公共层变更文件：

- `core/designsystem/src/main/java/com/ahu/ahutong/ui/components/AppComponents.kt`
- `core/designsystem/src/main/java/com/ahu/ahutong/ui/components/AppPageScaffold.kt`
- `core/designsystem/src/main/java/com/ahu/ahutong/ui/components/SettingsComponents.kt`

页面侧复用已有壳补 `onBack`，自绘页复用该按钮或 `AppPageHeader`；无标题栏页面按 Material / Miuix 分支补充公共标题。页面接入由并行审查统一处理，本公共层没有修改页面或主导航。

## 验证边界

按主审查协调，本子任务没有执行 Git 或 Gradle。已静态检查现有三种页面壳及主题 pack 的回调传递、图标实现与调用点。新增 API 属于低影响 UI 收口，没有新增与实现镜像的单元测试。仍需主审查在页面接入完成后统一构建、安装并检查 Material / Miuix 子页返回，以及 Radiant 外观保持。

Material / Miuix 标题行保持单行，在空间不足时省略；缩小标题和操作之间的空隙，避免新增返回后校园通知标题被筛选控件挤到换行。Radiant 与旧 LiquidGlass 的原间距、换行规则保留。

## 主审查交付验收

合入最新 develop 后，1,084 项单元测试、Debug / AndroidTest 构建、Release Kotlin 编译全部通过。最终 APK 安装启动及 3 项登录/Keystore 设备回归通过；Material/Miuix 实际返回与 Radiant 不新增按钮已抽查，未逐一打开所有业务页面或第三方插件内部 UI。详见[总审查与验证记录](2026-10-22-session-codebase-review.md)。
