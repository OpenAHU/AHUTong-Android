# SecondaryPageContent：子页面内容卡与背景的改造

> 适用范围：**仅 RadiantUI** 下子页面（如天气页）的内容卡片玻璃化 + 页面背景统一。Original / Liquid Glass 一律冻结、保持实色原观感，以下所有改动都只作用于 Radiant 分支。
> 涉及组件：`ui/components/GlassCard.kt`、`ui/components/SecondaryPageScaffold.kt`。天气页样板：`ui/screen/main/Weather.kt`。

---

## 1. 核心结论（先看这条）

**子页面（NavHost destination）里的内容卡，不要用 `liquidGlassSurface` + `Backdrop`。**
会触发 libhwui 无限递归崩溃（`prepareListAndChildren` 栈无限递归，SIGSEGV / 应用被杀）。

`Home` 主页里的玻璃卡能用真玻璃，是因为 backdrop 在 NavHost 顶层单层注册、Home 是根 destination；**一旦落到子 destination 里对同一 backdrop 再调用 `liquidGlassSurface`，渲染节点分解时形成依赖环 → 崩溃**。跟"卡片是否悬浮、用了几个 zIndex"无关（排查都试过，仍然崩）。

→ 子页面玻璃化**统一用 `GlassCard` 的伪玻璃实现**（半透明高光观感，不碰 backdrop），零崩溃风险且视觉接近液态玻璃。

---

## 2. `GlassCard` 组件设计（历代演进）

最终版签名（`ui/components/GlassCard.kt`）：

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant, // 非玻璃回退色
    overlayColor: Color? = null,          // 玻璃上额外叠的语义色薄层（如雨伞卡蓝/绿）
    shape: Shape = SmoothRoundedCornerShape(24.dp),
    glassShadow: Dp? = 14.dp,             // null = 不加阴影（密集小卡用）
    content: @Composable () -> Unit = {}
)
```

### 关键决策点（哪一版引入，为什么）

1. **玻璃条件 = `isRadiantUi`，不是 `LocalIsLiquidGlassEnabled`**
   - 只为 Radiant 渲染玻璃；OR/LG 走回退实色，冻结不被破坏。

2. **回退分支 = `clip(shape).background(containerColor)`**
   非玻璃模式完全复刻原来的 Material3 Card 实色观感。

3. **不用真 `liquidGlassSurface`**（见 §1）。

4. **玻璃底必须「完全不透明」**（这是反复踩出来的关键）：
   - ❌ 半透明 `surfaceContainerLowest.copy(alpha = 0.55f)`：阴影会**从卡片内部透出来**，视觉上像影子罩在卡上，因为 `Modifier.shadow` 的影层会透穿半透明底显示在卡内。
   - ✅ 用不透明 `surfaceContainerLowest`（Compose 主题色，自动跟随亮/暗取白/黑系），阴影只投在卡片外缘，卡内干干净净。

5. **阴影对应内容页特性**：
   - 内容页底部背景不显然 → 阴影层外面再叠一层符合亮暗的不透明底，即可遮挡影层（=第4点的实现方式）。
   - 密集小卡（预报/逐时/生活指数）**去掉阴影**（`glassShadow = null`），太多影子视觉压抑。
   - 大卡/单卡保留 14dp 柔和阴影，与项目玻璃卡阴影规范一致。

6. **语义色卡片用 `overlayColor` 叠加而非覆盖底色**：
   如雨伞卡的蓝/绿语义色由 `bgColor`（`Color.copy(alpha=0.15f)`）给出，玻璃后整卡透明会丢语义。做法：`containerColor = bgColor`（回退分支用）+ `overlayColor = bgColor`（玻璃分支叠薄层），两种模式语义色都在。

7. **玻璃修饰链与阴影 clip 顺序**（Compose 常识但易错）：
   ```kotlin
   Modifier
       .then(if (glassShadow != null) Modifier.shadow(glassShadow, shape, clip = false) else Modifier)
       .background(base, shape)
       .border(1.dp, Color.White.copy(alpha = 0.28f), shape)   // 高光描边
   ```
   `shadow(..., clip = false)`：clip 在链首会切断阴影，需放最后/或不 clip。
   `Modifier.matchParentSize()` 是 `BoxScope` 成员，**不能**单独 import（`androidx.compose.foundation.layout.matchParentSize` 不存在），在 Box content 内直接调用。

---

## 2.5 页面背景也要改（仅 RadiantUI）

内容卡要玻璃化，**页面背景必须先统一成 Radiant 样式**（由 `SecondaryPageScaffold` 在 Radiant 分支统一处理，页面无需自己写）：

1. **底层背景**用主页同款 `96.n1 withNight 10.n1`（不再是子页面原本的纯 surface 背景）。
2. **顶部标题栏**叠一层与主页 header 一致的**垂直渐变遮罩**（`surfaceContainerLowest/surface → 透明`），让底部从"纯色平铺"变成有层次的渐变观感。
3. **内容滚动区**：标题栏固定悬浮（状态栏占位 + `zIndex(20f)`），内容区 `top = 72dp`（60dp 标题栏 + 12dp 间隙）占位，滚动时内容从标题栏下方穿过、被渐变遮罩遮挡。
4. **非 Radiant（OR/LG）分支**：脚手架 `else` 分支还原原始背景 + 非悬浮标题栏 + 内容顶到最上，整页观感冻结不动。

> ⚠️ 标题栏悬浮层**不要**用 `liquidGlassSurface`（与 §1 同一崩溃根因），用上面这条玻璃渐变模拟。渐变 + 悬浮 + 分层的细节参考 `ChangeableUI.md` §7/§8。

---

## 3. 落地流程（把一个子页面内容卡玻璃化）

1. 组件内加 `GlassCard{...}` 把每个内容卡的容器从 `Card(colors=...)` 换掉，**原 `innerPadding` 和内容不动**，只换容器。
   ```kotlin
   // 改前
   Card(Modifier.fillMaxWidth(), CardDefaults.cardColors(containerColor = 90.a1 withNight 30.a1)) { Column(...) }
   // 改后
   GlassCard(modifier = Modifier.fillMaxWidth(), containerColor = 90.a1 withNight 30.a1) { Column(...) }
   ```
2. 逐个卡调参数：
   - 大卡/单卡：默认（14dp 阴影）。
   - 密集小卡：`glassShadow = null`。
   - 语义色卡（雨伞）：`containerColor = bgColor, overlayColor = bgColor`。
3. 只改 Radiant 分支；OR/LG 冻结观感由 `GlassCard` 回退分支保证，页面代码零 OR/LG 旧实现。
4. 页面背景统一也走 `SecondaryPageScaffold`（见 §2.5），页面本身不写背景/悬浮/让位代码。
5. 构建 + 真机验收：Radiant 玻璃观感、卡内无透影、小卡无影、切 OR/LG 仍是实色原卡。

---

## 4. 踩坑记录（LLM 复用要点）

| # | 现象 | 根因 | 规避 |
|---|------|------|------|
| 1 | 子页面用 `liquidGlassSurface(backdrop, ...)` → 进页面就 flash crash，`prepareListAndChildren` 无限递归 | NavHost 子 destination 里对顶层 backdrop 再调玻璃，形成 RenderNode 依赖环 | 子页面**禁用**真玻璃，用 `GlassCard` 伪玻璃（§2） |
| 2 | 阴影"盖在卡片之上" | 玻璃底半透明，`shadow` 影层透进卡内显示 | 玻璃底用**不透明** `surfaceContainerLowest` |
| 3 | 密集小卡一片影，看着压抑 | 每张卡都有 14dp 阴影 | 密集小卡 `glassShadow = null` |
| 4 | 雨伞卡玻璃后蓝/绿语义色丢失 | 玻璃透明底覆盖原语义色背景 | `overlayColor` 叠薄层 |
| 5 | `matchParentSize` 编译报 Unresolved | 它不是顶层 import，是 `BoxScope` 成员 | 在 Box content 内直接用，不 import |
| 6 | 阴影被裁掉 | `Modifier.shadow` 前接 `clip` 且 `clip=true` | `shadow(..., clip = false)` |
| 7 | `dp` 未解析 | 漏 import | `import androidx.compose.ui.unit.dp`（包裹组件更明显） |

---

## 5. 与整体规范化文档的关系

- 本文件只聚焦「**子页面内部内容卡的玻璃化**」经验。
- 悬浮标题栏/脚手架的玻璃与布局经验在 `ChangeableUI.md` §7 / §8（标题栏也禁用真玻璃，用玻璃渐变模拟——与 §1 共享同一崩溃根因）。
- 统一全局 UI 风格约束见工程根 `ChangeableUI.md` 顶部与项目记忆 `project_memory.md`。

---

## 6. 持续补充钩子

> 后续凡涉及「子页面卡片玻璃化 / GlassCard」的新经验或新坑，**追加到本文件对应章节**，并同步 `project_memory.md`。若新建了其他玻璃化卡片组件，在 §2 下方登记其文件名与差异。