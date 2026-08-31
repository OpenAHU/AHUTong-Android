# SecondaryPage —— 二级页完整美化流程

> 综合 `SecondaryPageContent.md`（内容卡 + 背景）、`ChangeableUI.md §8`（标题栏框架/脚手架）、`DEV.md`（构建命令与通用经验）三份文档，给出把一个二级页从「原样」美化成「RadiantUI 统一风格」的**完整操作手册**。
>
> **全局红线（仅 RadiantUI）**：所有 UI 修改**只允许在 RadiantUI 分支下进行**；Original / Liquid Glass **冻结不再改动**。凡共用元素/页面结构，**必须用 `isRadiantUi` 写分支**，OR/LG 走 `else` 复原原始观感。以下每一节都遵循此铁律。

---

## 0. 前置准备（仅按需）

> 以下两步**只在用户明确要求「回看原工程 / 备份」时才执行**，不要求为每个页面默认做。日常美化直接跳到 §1。

- 需要时取原页代码作 OR/LG `else` 分支基线（原工程对应文件，如天气页 = `AHUTong-Android-master-original/.../Weather.kt`）。
- 需要时做**带时间戳备份**：`Copy-Item -Recurse 源→ 备份-时间戳`（排除 build/.gradle/.idea/local.properties）。
- 始终在 **RadiantUI** 下打开该页验证效果。

---

## 1. 引用已有标题栏框架

框架组件已就位（`ui/components/`），**不要自己画标题栏结构**：

| 组件 | 职责 |
|------|------|
| `SecondaryPageHeader` | 单条标题栏：Radiant = 状态栏占位 + 固定 60dp + 标题 `titleLarge` 加粗、左距 22dp + 右侧圆形包络按钮（34dp 圆底 `onSurface 6%`、20dp 图标、间距 4dp、`end=22` 与左距对称）；OR/LG = `headlineSmall` + 12dp 原观感 |
| `SecondaryPageScaffold` | 整页结构：Radiant = 背景 + 悬浮标题栏（渐变遮罩 + `zIndex(20f)`）+ 内容区在标题栏下滚动、可穿渐变；OR/LG = 整页滚动 + 标题栏随内容滚动、**完整还原原观感** |
| `TrailingAction` / `SecondarySearchState` | 「右侧按钮」和「搜索态(query/visible/onClose/onSubmit)」封装成可传参状态，页面只保留回调 |

### 1.1 套用姿势（天气页样板）

```kotlin
@Composable
fun MyPage(...) {
    SecondaryPageScaffold(
        title = "页面标题",
        actions = listOf(TrailingAction(icon, "文字") { /* onClick */ }),
        // 需要搜索态：传 searchState，框架自动在 Radiant 换搜索栏 / OR/LG 换内联搜索
    ) {
        // 你的正文（不需要自己加 Box/zIndex/背景/让位/statusBarsPadding）
    }
}
```

### 1.2 铁律（来自 ChangeableUI §8.2）

- **页面级结构必须走风格分支**：Box / zIndex / 渐变背景 / 内容让位这些**页面结构**必须放进脚手架内做 `isRadiantUi` 分流，不能只给"标题栏那一行"分流，否则 OR/LG 观感被破坏。
- **header 槽位可扩展**：搜索态等特殊 header 也要进脚手架（`search` 参数），**绝不**留在页面手写。
- **OR/LG 冻结**：所有改动只进 Radiant 分支；非 Radiant 一律走脚手架 `else` 分支的原观感。

### 1.3 支付/确认按钮（ConfirmPayButton）的位置与状态

确认 / 支付这类**带状态、非纯图标**的业务按钮，`TrailingAction`（仅 icon+点击）装不下。框架保留了 `trailingContent: (@Composable RowScope.()->Unit)?` 槽（`SecondaryPageHeader` / `SecondaryPageScaffold`），**但支付确认类按钮一律不放标题栏**——开发者实测：右上角**不在用户点击热区**。位置规范：

> **支付确认按钮（含支付成功展开态）放在表单正下方、右对齐**，与 OR/LG 分支底部确认按钮同一位置、同一**经典大胶囊样式**（"确认"文字 → 支付中转圈+文字 → 成功/失败整行撑开、`headlineSmall` 大字显示订单号/错误信息）。即：Radiant 分支 `content` 内表单之后放 `Row(fillMaxWidth, Arrangement.End) { /* 经典大胶囊 Box */ }`（网费充值直接复用同文件的 `RechargeActionRow`）；页面结构仍走 `SecondaryPageScaffold`（背景/半透标题栏/玻璃卡片照旧）。

```kotlin
SecondaryPageScaffold(
    title = "电控缴费"
) {
    ElectricityFormBody(/* 表单正文 */)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalArrangement = Arrangement.End
    ) {
        // 经典大胶囊：clip(SmoothRoundedCornerShape(32.dp)) + animateColorAsState 背景 +
        // animateContentSize(spring 低刚度)，内部 when(payState) 渲染
        // Idle"确认"文字 / InProgress 转圈+文字 / Succeeded 大号订单号行 / Failed 红叉行
        // —— 与本页 OR/LG else 分支的按钮代码逐行一致
    }
}
```

**硬约束（都踩过）**：
- 标题栏 `trailingContent` 槽仍存在，但**仅用于真正属于标题栏操作**的带状态按钮；电控/校园卡/浴室/网费四页的支付按钮已于 2026-08-31 从右上角移回右下角（右上角不在用户点击热区），样式也从 34dp 小胶囊换回经典大胶囊。
- **`ConfirmPayButton` / `ConfirmPayCapsule`（34dp 小胶囊组件）已不再用于支付确认场景**：四处引用已全部换回与 OR/LG 逐行一致的经典大胶囊内联代码（`PayCapsuleConfirmButton.kt` 组件文件保留，后续如需可再启用）。改动时**直接复刻该页 else 分支的按钮代码**到 Radiant 分支（注意把 `showDialog` 等旧分支局部变量换成 Radiant 分支自己的，如浴室页 `radiantShowDialog`），不引用组件。
- 带状态按钮用 `StateFlow` 驱动：确认态=可点 → 支付中=转圈+文字 → 成功/失败=整行 `fillMaxWidth` 撑开（`headlineSmall` 大字订单号/错误信息）→ 自动复位回确认态。**不做灵动岛式额外弹层**（位置锚定复杂，已弃用，详见项目记忆）。
- **成功态要显示真实业务数据**（如订单号）：电控缴费的订单号 = `(payState.value as PayState.Succeeded).message`（下单成功后 `_payState.value = PayState.Succeeded(orderId)` 把真实 orderId 塞进 message，而非写死 MOCK）。
- 成功态展示时长**单独拉长**（电控用 `delay(2400)`），失败态可短（`delay(1000)`），不要共用同一 delay，否则订单号看不清。
- **内容表单逐像素保留**：提取共享 `XXFormBody` 时只改动容器/标题栏/背景，表单控件（下拉 `DropdownMenu`+`ArrowDropDown`、卡片背景、空行距、信息行 `roomInfo.replace("，","\n")`）必须与之前验收过的原版完全一致，不得顺手"重构成更好看"。
- Radiant 分支表单与按钮行之间的 24dp 间距：外层 `Column(spacedBy(24.dp))` 的页面（浴室/校园卡/网费）自动获得；电控页 content 未包 spacedBy Column，需在按钮 Row 上手动 `padding(top = 24.dp)` 补齐。

---

## 2. 更改卡片样式（玻璃化）

核心组件：`GlassCard`（`ui/components/GlassCard.kt`）。

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant, // 非玻璃回退色
    overlayColor: Color? = null,           // 玻璃上额外叠的语义色薄层
    shape: Shape = SmoothRoundedCornerShape(24.dp),
    glassShadow: Dp? = 14.dp,              // null = 不加阴影（密集小卡用）
    content: @Composable () -> Unit = {}
)
```

### 2.1 替换 Card → GlassCard

原 `Card(colors=...)` 容器换 `GlassCard`，**`innerPadding` 与内容不动**：
```kotlin
GlassCard(modifier = Modifier.fillMaxWidth(), containerColor = 90.a1 withNight 30.a1) { Column(...) }
```

### 2.2 参数决策速查

| 卡片类型 | 配置 |
|---------|------|
| 大卡 / 单卡 | 默认（14dp 柔和阴影） |
| 密集小卡（预报/逐时/生活指数） | `glassShadow = null`（去影，避免视觉压抑） |
| 语义色卡（如雨伞卡蓝/绿） | `containerColor = bgColor, overlayColor = bgColor` |
| 回退模式（OR/LG） | 自动走 `clip(shape).background(containerColor)` 实色，冻结不变 |

### 2.3 关键硬约束（务必记住）

1. **玻璃条件 = `isRadiantUi`**（不是 `LocalIsLiquidGlassEnabled`）。
2. **子页面（NavHost destination）内禁用 `liquidGlassSurface` + `Backdrop`** → libhwui 无限递归崩溃（`prepareListAndChildren`）。`Home` 根页可用真玻璃，**二级子页一律用 `GlassCard` 伪玻璃**，零崩溃风险。
3. **玻璃底必须「完全不透明」**（`surfaceContainerLowest`，随亮/暗取白/黑），否则阴影从卡内透出、像影罩在卡上。内容页底部背景不显然 → 在不透明底上即可彻底遮住影层。
4. **阴影 clip 顺序**：`Modifier.shadow(...)` 后接 `background`，不要链首 `clip`（会切掉阴影）；`matchParentSize` 是 `BoxScope` 成员，不 import、在 Box content 内直接用。
5. **页面背景也要改（仅 Radiant）**：由 `SecondaryPageScaffold` 统一处理——底层 `96.n1 withNight 10.n1`、标题栏垂直渐变遮罩（`surfaceContainerLowest/surface → 透明`）；OR/LG 走 else 还原原始背景。
6. **内容必须「穿」过半透标题栏，占位只能放在「滚动内容的顶部」、绝不能放在「内容容器的外层固定 padding」**（本页最重要的教训，用户已两次强调）：
   - `contentEdgeToEdge = true` 分支**不设**任何外层顶部 padding，内容区自顶 `fillMaxSize()` 铺满、可向上滚入半透标题栏之下、被垂直渐变遮罩柔和盖住 → 这就是半透标题栏的存在意义。
   - **顶部停靠占位归属滚动容器自身**：滚动容器（LazyColumn）用 `contentPadding = PaddingValues(top = 72.dp)`，或滚动 `Column` 内首个元素放 `Spacer(Modifier.height(72.dp))`。
   - **占位必须写成 `if (isRadiantUi) …`**：Radiant 才加 72dp，OR/LG 分支恒为 0（防破坏冻结基线）。
   - 若把占位做成内容容器外层的固定 `padding(top=72)`，内容永远被压在标题栏下方、穿不进渐变区 → 半透标题栏被"挡得干干净净"，纯属白做。

### 2.4 二级界面卡片默认规范（以天气页为基准）

二级页内容卡统一用 `GlassCard` 默认参数，与天气页保持一致：

| 项 | 规范值 |
|----|--------|
| 圆角 | `GlassCard` 默认 `SmoothRoundedCornerShape(24.dp)`；**不要**显式写 16dp 或更小圆角 |
| 左右边距 | 卡片自身**不加横向 padding**，直接 `fillMaxWidth()` 全宽；左右 16dp 由 `SecondaryPageScaffold` 内容区 `start/end = 16.dp` 统一提供 |
| 卡内内容边距 | 由卡内首个 item 的 `padding(16.dp)`（Row / 标题文本）承担 |
| 背景色 | 表单等信息卡 `containerColor = 100.n1 withNight 20.n1`；主卡/大卡 `90.a1 withNight 30.a1` |

落地要点（帮助 `XXFormBody` 拆分时对齐天气页）：
- 卡片容器**不要自己加 `padding(horizontal = ...)`**，否则会叠成 16+16=32dp 的过宽视觉边距；
- 不要显式覆盖 `shape` 参数，让圆角恒为默认 24dp；
- 二级页 Radiant 下卡片横向占位即 `fillMaxWidth()`，边距完全交给 Scaffold。

#### 表单内容重叠 / 挤成一点的解决法（必查）

`GlassCard` 内部是 **`Box`**（不是 `Column`/`Row`）——多个子元素会**重叠到同一位置**，无垂直流、无间距。替换 `Card/Column` → `GlassCard` 后，若卡片内是多个垂直排列的表单控件（级联下拉 Row、信息行、金额输入等），必须在外层把内容包回垂直流：

```kotlin
GlassCard(containerColor = 100.n1 withNight 20.n1, modifier = Modifier.fillMaxWidth()) {
    Column {          // ← 关键：在 GlassCard 内补回垂直容器
        Row(/* 分行控件 */) { }
        Row(/* 信息行 */)   { }
        // …其它表单控件
    }
}
```

要点：
- **只包结构，不改控件**：`Column` 不设额外背景/圆角/阴影（玻璃底由 GlassCard 负责）；控件本身的 `padding(16.dp)` 保持，间距即恢复。
- 圆角、左右边距、阴影都不要动——它们都由 GlassCard 统一处理，区别只在于**容器语义从流式 Box 换成垂直流**。
- 这正是二级页 `XXFormBody` 换玻璃卡后"内容全挤在一起"的根因，出现即按此恢复。

---

## 3. 更改按钮 icon

### 3.1 图标资源化 + 复用

- iconpark 线性 SVG 经 `compose-svg-icon-replacer` 流程转 VectorDrawable 到 `res/drawable/ic_xxx.xml`（viewBox 48、linejoin round、stroke #333 待 tint）。
- 页面引用统一：`ImageVector.vectorResource(R.drawable.ic_xxx)`（注意：本版本无顶层 `vectorResource`，须 `import androidx.compose.ui.res.vectorResource`，写成 companion 扩展形式）。
- **资源级复用**：同一 drawable 被多页引用时，换一次资源文件所有页同步更新（不要各自用 Material Icons）。

### 3.2 预设动作工厂（推荐，统一图标）

为避免每页 icon 分叉，优先用框架提供的按钮工厂（内部统一引用 `ic_find / ic_config / ic_refresh`）：
```kotlin
actions = listOf(
    searchAction { ... },   // 查找 ic_find
    configAction { ... },   // 配置 ic_config
    refreshAction { ... },  // 刷新 ic_refresh
)
```
每页直接复用工厂 → 图标集中管理、全局同步。

### 3.3 按钮样式（Radiant）

圆形包络：34dp 圆底 `onSurface 6%` + 20dp 线性图标 + 按钮间距 4dp + 整组 `end=22`（与左侧标题缩进对称）。图标随主题 tint（`onSurface`）。

---

## 4. 新坑速查表（三篇踩坑汇总）

| # | 现象 | 根因 | 规避 |
|---|------|------|------|
| 1 | 子页面玻璃化 → 进页 flash crash，`prepareListAndChildren` 无限递归 | NavHost 子 destination 内 `liquidGlassSurface` + backdrop 形成 RenderNode 依赖环 | 二级页禁用真玻璃，用 `GlassCard` 伪玻璃 |
| 2 | 阴影"盖在卡片之上" | 玻璃底半透明，`shadow` 影层透进卡内 | 玻璃底用不透明 `surfaceContainerLowest` |
| 3 | 密集小卡一片影 | 每卡 14dp 阴影 | `glassShadow = null` |
| 4 | 语义色卡玻璃后丢色 | 玻璃透明底盖掉原色背景 | `overlayColor` 叠薄层 |
| 5 | `.clip(shape)` 切投影 | 修修饰链首 clip 裁掉边界外阴影 | `shadow(..., clip=false)`，clip 只放非玻璃分支 |
| 6 | 标题栏玻璃也崩溃 | 悬浮 Box + glass = 系统渲染 bug | 悬浮 header 用玻璃渐变(verticalGradient) 或半透明表面，不用 `liquidGlassSurface` |
| 7 | `vectorResource` 报无候选 | 本版本无顶层函数 | `ImageVector.vectorResource(R.drawable.xx)` + `import androidx.compose.ui.res.vectorResource` |
| 8 | `KeyboardActions.onSearch` 编译错 | 期望 `(KeyboardActionScope)->Unit` | 包一层 `onSearch = { search.onSubmit() }` |
| 9 | `matchParentSize` Unresolved | 是 `BoxScope` 成员 | Box content 内直接用，不 import |
| 10 | 导航 tab 已在本页再点无反应/误切子页 | 拖动层吞点击 / 导航与切子页共用回调 | 拆 `onTabTapped`（真实点击）与 `navigateToTab`（拖动校正）两个入口 |
| 11 | 右上角业务按钮访问 `as PayState.Succeeded` 成员报错 | `when(payState.value)` 中 `payState.value` 是 getter，智能转换不生效 | 先 `val state = payState.value` 存局部，再 `when(state)` 并读 `state.message` |
| 12 | 支付/确认按钮在标题栏里显得突兀 | 沿用 demo 工程自定尺寸（48dp），未对齐框架 | 与框架右侧按钮统一 34dp 圆 + 内图标 18–20dp（成功态撑开的胶囊动画除外） |
| 13 | 成功弹出的订单号一闪而过看不清 | 成功/失败共用同一个 `delay(1000)` 复位 | 成功态单独 `delay(2400)`，失败态 `delay(1000)` |
| 14 | `contentEdgeToEdge` 页内容顶不进半透标题栏、渐变被空白挡 | 占位做成了**内容容器外层**的固定 `padding(top=72)`，把内容锁死在标题栏下方 | 框架该分支去掉外层 padding；占位移到**滚动容器自身顶部**（LazyColumn `contentPadding(top=72)` 或滚动 Column 首元素 `Spacer(72.dp)`），且 `if (isRadiantUi)` 隔离 |

---

## 5. 完整落地 SOP（把任一二级页美化完）

1. 取原页代码作 OR/LG else 基线（§0）。
2. 用 `SecondaryPageScaffold` 替换页面根结构，把标题/按钮/搜索态以参数传入（§1）。
3. 逐块把内容卡容器 `Card` → `GlassCard`，按卡片类型定参数（§2.2）。
4. 图标换上资源级 `ic_xxx` / 预设动作工厂（§3）。
5. 构建 Debug → `adb install -r`（命令见 §6）。
6. **按下述验收**：Radiant 下查标题栏固定/玻璃卡/图标/背景；**切 Original / Liquid Glass 确认整页仍是原观感**。

---

## 6. 构建 / 部署命令速查（Windows PowerShell）

```powershell
cd 'C:\Users\InChange_Jiang\Documents\AHUTong-UIDesinger\AHUTong-Android-master'
.\gradlew.bat assembleDebug                       # 产物 app\build\outputs\apk\debug\app-debug.apk
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
# 启动：Debug 包组件全限名是 com.ahu.ahutong.MainActivity（类包名，不含.debug 后缀）
& $adb shell am start -n 'com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity'
# 崩溃检查
& $adb logcat -d -s AndroidRuntime:E | Select-String 'com.ahu.ahutong'
```

---

## 7. 关联文档

- `SecondaryPageContent.md` — 仅内容卡玻璃化 + 背景细节。
- `ChangeableUI.md` — 全局三态 UI 方案 / §8 脚手架 / §6 导航提级。
- `DEV.md` — 构建环境 / 通用经验 / 用户设计偏好（§12）。

---

## 8. 钩子（持续补充）

> 后续每完成**一个新二级页**、或踩到**新坑**，立即：
> 1. 在本文件对应章节追加条目（§2 卡片 / §3 图标 / 若为新类别则新增小节）；
> 2. 同步把新经验写进 `SecondaryPageContent.md` 与项目记忆 `project_memory.md`；
> 3. 保持「只改 Radiant 分支」的红线不变，确保跨会话能无缝续做。