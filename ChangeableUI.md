# ChangeableUI —— 可切换界面风格（经典 / 液态玻璃 / 曜光）定稿方案

> 需求：所有 UI 改动必须能在界面内开关，可选回原样式或新版样式。当前 UI 体系演化为 **Original / Liquid Glass / RadiantUI（曜光）** 三态可切换。
>
> 状态：**进行中**。已完成「UI 设置」入口落地与 RadiantUI 状态注册；两套界面组件并存的渲染分流待收到改造前源码后按本节实施。

---

## 1. 命名与三态

| 模式 | 存储值 | 说明 |
|------|--------|------|
| **Original** | `original` | 经典原样式：无玻璃、原始组件与交互 |
| **Liquid Glass** | `liquid_glass` | 原项目自带的液态玻璃风格（Apple 风格玻璃控件 + 浮动导航） |
| **RadiantUI（曜光）** | `radiant_ui` | 本项目整合风格：新主页（Hero 校园卡 + 图标网格 / 更多二级页）+ 玻璃 + 扁平导航 |

代号：**RadiantUI / 曜光**（设置项显示 "Original / Liquid Glass / RadiantUI"）。

---

## 2. 设置入口（已落地）

设置页「外观」区原「液态玻璃」开关 → 替换为 **「UI 设置」下拉**：

- 标题：**UI 设置**
- 副标题：**改变整套界面的组件和交互风格**
- 选项：Original / Liquid Glass / RadiantUI

### 已实现的数据链路

- `data/model/UiStyle.kt`：新增枚举 `UiStyle`，含 `storageValue`、`usesGlass`、`fromStorage()`（默认 `LIQUID_GLASS`，兼容现状）。
- `PreferencesManager`：
  - 新增 key `UI_STYLE`（`ui_style`）；
  - `uiStyle: Flow<UiStyle>` 读取；无显式值时按旧的 `use_liquid_glass` 迁移（true→LiquidGlass，false→Original）；
  - `setUiStyle(value)` 写入 UI_STYLE，并同步旧 `USE_LIQUID_GLASS`（`value != ORIGINAL` 开玻璃），避免两套状态分叉。
- `PreferencesViewModel`：`uiStyle` 状态流 + `setUiStyle()`（含行为埋点 `LIQUID_GLASS_CHANGED`）。
- `Preferences.kt`：`SettingsSelectRow` 下拉呈现三选项。
- 影响链：选择后经 `USE_LIQUID_GLASS` 同步，`AHUTheme` 的 `LocalIsLiquidGlassEnabled` 即时响应；RadiantUI 作为可开启入口已注册。

> 说明：此时切「UI 设置」已经能全局开关玻璃；RadiantUI 特有的主页布局/导航结构渲染分流，见 §3/§4。

---

## 3. 开关作用范围（全都要切）

用户确认 **三类改动全部纳入「经典/曜光」两套并存**（UI 设置三态，其中 RadiantUI 含所有新版）：

1. **主页视觉/布局**：课程状态行居中、Hero 校园卡、小工具图标网格、两行紧凑、卡片留白 24dp、更多按钮形态、二维码渐隐时长 —— 用 `if(uiStyle==RADIANT_UI) A else B` 分流。
2. **图标**：iconpark 统一图标 vs 经典图标 —— 两套 `res/drawable` 资源并存，按风格切换引用。（原独立 switch 已并入 UI 设置。）
3. **结构/导航**：学习通日历提级为一级页 + 底部「日程/课程」轮换、「更多」二级页、去小工具 tab、网费充值注册 —— 需维护两套导航表现。

**约束（开发者）**：取消底部「小工具」tab 后，**第三个 tab 必须可自定义小工具页面**。即第三 tab 内容可配置（日程 | 小工具 | 自定义小工具），RadiantUI 默认日程，经典可回退小工具。两套导航共用同一骨架，第三 tab 内容抽象为可配置状态。

---

## 4. 待实施：两套渲染分流（等改造前源码）

拿到改造前工程后，逐处对照做 diff，每处给出「经典实现 / 曜光实现」，用统一状态门控：

```kotlin
// 统一门控：从 PreferencesViewModel.uiStyle 取，composable 内 collectAsState
val uiStyle = ... // UiStyle
when (uiStyle) {
    UiStyle.ORIGINAL -> ClassicHome(/* 原样式 */)
    UiStyle.LIQUID_GLASS -> GlassHome(/* 纯玻璃版 */)
    UiStyle.RADIANT_UI -> RadiantHome(/* 曜光整合版 */)
}
```

### 4.1 状态抽象（导航结构）

- 新增「第三 tab 内容」配置状态：`ThirdTabDestination { SCHEDULE, TOOLS, CUSTOM_WIDGET }`，持久化到 DataStore。
- `BottomNavBar` 第三 tab 标签/图标随「内容类型」渲染；RadiantUI 内部仍支持日程/课程轮换（`XuexiaotongDockState`）。

### 4.2 资源

- 经典/曜光两套 drawable 全部保留；按 `uiStyle` 选择 `painterResource` 引用。
- 玻璃是否启用：`UiStyle.usesGlass`（Original 关，LiquidGlass/RadiantUI 开）。

### 4.3 主页组件

- `Home.kt`、`CampusCard.kt`、`HomeWidgetEditor.kt`、`MoreWidgetsScreen.kt`：以 `uiStyle` 分支呈现「经典 / 曜光」两套布局；共享组件（如 `liquidGlassSurface`、`SettingsSelectRow`）无需分流。

---

## 5. 里程碑

- [x] `UiStyle` 枚举 + DataStore 持久化 + ViewModel 状态 + 设置页「UI 设置」下拉入口（注册 RadiantUI 开启入口，可全局开关玻璃）。
- [x] 收到改造前源码，完成经典/曜光两套主页布局 diff 与 `uiStyle` 门控分流（BottomNavBar / XuexiaotongScreen / AtAGlance / CampusCard / HomeWidgetEditor / Home / MoreWidgetsScreen / HomeWidgetRegistry 均已分流）。
- [ ] 第三 tab 可自定义小工具页面（内容类型可配置）。
- [ ] 图标两套资源按风格切换。
- [ ] 真机回归：三态切换、重启持久化、玻璃联动、导航结构。

---

## 6. 开发经验：第三 tab 自定义替换（已实践：小工具 tab → 学习通日历）

> 这是「第三 tab 可自定义小工具页面」的**一次真实落地**：把底部导航第 3 个 tab 从「小工具」整体替换为「学习通日历（日程）」并提级为一级页。后续做「可自定义」多态时直接套用这套已验证的做法。详细排查可另见 `DEV.md` 第 10 节。

### 6.1 触发的根因链

`LiquidBottomTabs` 自带中间两个（代价与收益）问题，导致"同一按钮同时承担导航 + 切子页"时两件事互相干扰：

1. **拖动层吞点击**：顶层透明拖动 Box 正好覆盖「当前滑块所在 tab」，已停在该页时就点不动那个 tab。
2. **事件重复/竞态**：进入日程页时内部 `LaunchedEffect` 触发一次『选中校正』；若把「导航」和「切子页」都挂在同一个 `onTabSelect` 上，从别的页进入的瞬间会被额外 toggle 一次 → 出现"进入时反而切了子页"。

### 6.2 正确解耦：导航与切子页拆成两个入口

`BottomNavBar.kt` 里分成两个独立回调：

```kotlin
onTabTapped(route)  { // 真实点击
    if (已在当前日程/课程页) XuexiaotongDockState.toggle()   // 只切子页，不导航
    else nav(i)                                              // 否则纯导航
}
navigateToTab(route) { // 拖动结果 / 进入校正
    // 只导航，绝不动子页状态
}
```

`LiquidBottomTabs.kt` 新增可选 `onCurrentTabTapped`：在当前滑块 tab 上做**非消费**的点击识别（纯点击触发、拖动不触发），解决拖动层吞掉「已在该页又点该 tab」的点击。

### 6.3 提级 + 子页状态提升

- `XuexiaotongScreen` 去掉内部底部悬浮 Dock（`BottomDock` 的 日程/课程 两段），子页切换改由底部导航驱动。
- 子页状态从页内局部变量提升到共享 `XuexiaotongDockState`（`data class + global state`，SCHEDULE/COURSE），`navigationBarsPadding` 补好底部导航栏，去掉 `onBack`。
- 进入日程页停在**上次子页**；再点该 tab 在日程↔课程间轮换，图标文字联动。

### 6.4 图标随子页动态切换

- 第 3 tab 构造不写死 label/icon，改为读 `XuexiaotongDockState.tab` 渲染 `ic_nav_plan`（日程）/`ic_nav_degree_hat`（课程）。

### 6.5 入口收敛

- `HomeWidgetRegistry` 移除「学习通日历」小工具入口（主页/更多页同步隐藏），`Home` 固定插槽残留 id 一并清理。
- `xuexiaotong` 提级为一级 destination；`Main.kt` 移除 `tools` 一级项（`Tools()` composable 保留未用不报错）。

### 6.6 抽模型：从"写死日程"到"可配置"

上面是**写死为日程**的最小落地。做「第三 tab 可自定义小工具页面」的多态时，把 `XuexiaotongDockState.tab` 抽象为通用状态：

```kotlin
// ⚠️ 注意：子页「日程↔课程」轮换只属于 SCHEDULE（学习通日历）这个 tab 的独有配置，
//    其它第三方页面 / 小工具 entry 都是单页，没有子页概念，不要对它们调用 toggle()。
enum class ThirdTabContent { SCHEDULE, TOOLS, CUSTOM_WIDGET } // 持久化到 DataStore

// BottomNavBar 第 3 tab：
when (thirdTabContent) {
    SCHEDULE      -> 日程图标 + 子页轮换（复用 §6.2/6.4；点击该 tab 在日程↔课程间 toggle，
                     非学习通内容绝不进入该 toggle 分支）
    TOOLS         -> 经典小工具页（单页）
    CUSTOM_WIDGET -> 用户自选的一个小工具 entry，单页直达其 route
}
```

三态都共用一张导航骨架，只切换第 3 tab 的底色内容与图标，满足"可自定义"要求。

### 6.7 固定到第三 tab 后，在小工具页隐藏该入口

当某个小工具被设为「第三 tab」后，它不能再出现在小工具抽屉/`更多`页里，否则同一功能双重入口。实现：

```kotlin
// 1) 持久化"第三 tab 当前固定的小工具 id"（DataStore，可空）
val thirdTabWidgetId: Flow<String?>                     // PreferencesManager
suspend fun setThirdTabWidgetId(value: String?)         // 选定时写入，取消时置 null

// 2) 只在「已选为第三 tab」时从列表过滤，避免它再被拖进主页/出现在更多页
val thirdTabId by thirdTabWidgetId.collectAsState()
HomeWidgetRegistry.widgets
    .filter { it.id != thirdTabId && it.id !in homeWidgetIds }  // MoreWidgetsScreen / 抽屉 复用此过滤
    .forEach { widget -> ToolItem(...) }

// 3) 主页「更多」按钮与第三 tab 共用第三 tab 入口来源；选它后主页网格也自然不再重复提供（同一 id 只在一处固定）
// 4) 清空/切换第三 tab 时交回该 entry（重新出现在列表，可再次手动固定回主页插槽）
```

要点：**过滤条件 = `id != thirdTabWidgetId`**，其余小工具逻辑（registry 注册 / 主页插槽 / 图标着色）完全不变；取消固定后条目自动回归列表。

---

- `app/src/main/java/com/ahu/ahutong/data/model/UiStyle.kt`（新增）
- `app/src/main/java/com/ahu/ahutong/data/dao/PreferencesManager.kt`
- `app/src/main/java/com/ahu/ahutong/ui/state/PreferencesViewModel.kt`
- `app/src/main/java/com/ahu/ahutong/ui/screen/settings/Preferences.kt`
- 待分流：`ui/screen/main/Home.kt`、`home/CampusCard.kt`、`home/HomeWidgetEditor.kt`、`Main.kt`、`BottomNavBar.kt`、`MoreWidgetsScreen.kt`

---

## 8. 二级页脚手架（SecondaryPageHeader / SecondaryPageScaffold）

> 目标：统一二级页（工具/设置等）UI 风格——标题栏高低不一、按钮样式、筛选入口、弹窗样式全面收敛为**页面级脚手架**，各页只传数据与正文。状态：以天气页为样板完成，Radiant 新结构 + OR/LG 原观感已在脚手架内分流。

### 8.1 分层与 API

- **`SecondaryPageHeader`**（`ui/components/`）：单条标题栏。Radiant = 状态栏占位 + 固定 60dp + 标题 `titleLarge` 加粗、左距 22dp + 右侧圆形包络按钮（34dp 圆底 `onSurface 6%`、20dp 图标、间距 4dp、`end=22` 与左距对称）。OR/LG = `headlineSmall` + 12dp 原观感。
- **`SecondaryPageScaffold`**：整页结构脚手架。Radiant = 背景(`96.n1 withNight 10.n1`) + 悬浮标题栏/搜索栏（渐变遮罩 + `zIndex(20f)`）+ 内容区在标题栏下滚动、可从渐变下穿过；OR/LG = 整页滚动、标题栏随内容滚动、搜索态内联，**完整还原原页观感**。
- **`TrailingAction`** / **`SecondarySearchState`**：把"右侧按钮"和"搜索态交互(query/visible/onClose/onSubmit)"封装成可传参状态，页面只保留回调。

### 8.2 开发约定（务必遵守）

- **页面级结构也要走风格分支**：Box/zIndex/渐变背景/内容让位这些页面结构，不能只给"标题栏那一行"做 `isRadiantUi` 分流，否则 OR/LG 换了配色但布局结构没换，观感照样被破坏。所有差异收敛进脚手架的 else 分支，页面代码零旧实现 → 以后删 OR/LG 只删该分支。
- **header 槽位可扩展**：搜索态这类特殊 header 也要纳入脚手架（`search` 参数），不能留在页面里手写，否则又出现"结构漏分支"。
- **图标资源级复用**：`ic_find / ic_config / ic_refresh` 等放入 `res/drawable`，页面用 `ImageVector.vectorResource(R.drawable.xx)` 引用；换资源文件一次性全页同步。
- **OR/LG 冻结**：所有 UI 修改只在 Radiant 分支做，共用元素必须写 `isRadiantUi` 分支（见脚本顶部约束）。

### 8.3 踩坑记录

1. **`liquidGlassSurface` 在悬浮标题栏上触发 libhwui 无限递归崩溃**：在 NavHost + `Box(align TopCenter)` 悬浮结构里用 `liquidGlassSurface`（holder/backdrop），会无限递归 `prepareListAndChildren` 崩溃（SIGSEGV）。去掉 zIndex、去掉整条玻璃、只剩单个按钮玻璃仍崩溃；**Home 里普通位置的玻璃卡正常** → 是"悬浮 Box align 结构 + glass"引发的系统渲染 bug。规避：这类悬浮 header **绝不**用 `liquidGlassSurface`，改用玻璃渐变(verticalGradient)或半透明表面模拟。
2. **`zIndex` 曾被误判为元凶**：主页同款 `zIndex(20f)` 在**无 glass** 时完全正常，证明崩溃根因是 glass、zIndex 安全；排查时不要只盯着最近的改动。
3. **`background(Brush)` 用法**：`Brush.linearGradient(...)` 单参数版本不可直接传，需 `Brush.linearGradient(colors = listOf(...))`；`Modifier.background` 传 Brush 需带 shape。主页 Header 用的是 `Brush.verticalGradient(colorStops = arrayOf(...))`。
4. **`ImageVector.vectorResource` 是 companion 扩展**：本 Compose 版本没有顶层 `vectorResource(id)`；须 `import androidx.compose.ui.res.vectorResource` 并写 `ImageVector.vectorResource(R.drawable.xx)`，否则报 "None of the following candidates is applicable"。
5. **`KeyboardActions.onSearch` 签名**：期望 `(KeyboardActionScope) -> Unit`，不可直接传 `() -> Unit`，需包一层 `onSearch = { search.onSubmit() }`。
6. **import 路径**：`statusBarsPadding` 在 `androidx.compose.foundation.layout.statusBarsPadding`（不是 `foundation.statusBarsPadding`）。

### 8.4 回滚基准

OR/LG 恢复基准 = 原工程 `Weather.kt`（整页滚动 + 标题行内联搜索态）。改造前对每个二级页都要先取该页原代码，作为 its else 分支基线。

> **钩子（持续补充）**：后续每完成一个二级页/新坑，把新经验/新踩坑条目追加到本节（§8.3 踩坑、§8.2 约定），并同步到项目 `project_memory.md`，保证跨会话继承。