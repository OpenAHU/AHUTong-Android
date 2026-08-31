# DEV.md — 安大通 AHUTung Android 开发纪录

> 本文档用于沉淀本项目在 UI 优化过程中的所有开发经验、技术决策、踩坑记录与操作流程，
> 防止后续会话丢失上下文。**每次开展新的优化前请先阅读本文件。**
>
> 最近更新：液态玻璃（Liquid Glass）体系在设置页 / 主页卡片上的接入与统一。

---

## 1. 项目概述

- **项目**：安大通 AHUTung —— 安徽大学学生开发的校园 App。
- **当前目标**：对项目做 **UI 优化**，已完成液态玻璃视觉体系的接入。
- **构建产物**：Debug APK，安装到真机 `9bf694eb`（OPPO 系）进行视觉验证。

### 技术栈（关键）

| 项 | 值 |
|----|----|
| 语言 | Kotlin 2.2.21（含 Kotlin Serialization / Compose 插件） |
| UI | Jetpack Compose（Compose BOM 2025.10.01，Material3） |
| 架构 | MVVM（ViewModel + Repository + Room + MMKV + DataStore），Hilt 依赖注入 |
| 导航 | Navigation Compose 2.9.5，`MainActivity` 内 `NavHost` |
| AGP | 8.13.2 |
| Gradle | 9.2.0（wrapper，`services.gradle.org`） |
| compileSdk / minSdk | 36 / 26 |
| 版本 | versionName 3.2.2（versionCode 323）；Debug 后缀包名 `com.ahu.ahutong.debug` |
| 主题取色 | Monet `com.github.Kyant0:Monet:0.1.0-alpha03`（Material You 动态取色） |
| 毛玻璃背景 | `io.github.kyant0:backdrop:1.0.0`（`com.kyant.backdrop.*`） |
| 网络 | Retrofit 2.11 + OkHttp + PersistentCookieJar；Jsoup 解析 |
| 其他 | Coil 图片、Glance 桌面小组件、Bugly 崩溃上报、Google Ads SDK |

### 工程模块与包结构（`app/src/main/java/com/ahu/ahutong`）

| 包 | 职责 |
|----|------|
| `ui/screen` | 各页面：`main`（主页）、`settings`（设置）、`setup`（登录）、`xuexiaotong`（学习通）等 |
| `ui/screen/main/home` | 主页卡片与插槽（`Home.kt`、`HomeWidgetEditor.kt`、`CampusCard.kt`、`BathroomOpening.kt`、`ElectricityPayment.kt` 等） |
| `ui/components` | 共享组件：`SettingsComponents.kt`、**`LiquidGlass.kt`**（本阶段新增，液态玻璃公共组件） |
| `ui/theme` | `AHUTheme.kt`（全局主题 + 动态取色 + 液态玻璃开关注入） |
| `ui/state`、`ui/shape`、`ui/utils` | 状态管理 / 圆角工具 / 通用工具 |
| `data` | 数据层（DAO / Preferences / Repository） |
| `personalization` | 智能建议 / 行为埋点（`AppActionId`、`ActionSource`） |

---

## 2. 本机开发环境（已验证可用）

| 组件 | 状态 |
|------|------|
| JDK | OpenJDK 25.0.2（Android Studio JBR），`JAVA_HOME` 已指向 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`，`ANDROID_HOME` / `ANDROID_SDK_ROOT` 已设置 |
| SDK 平台 | android-35 / 36 / 37 |
| build-tools | 34 / 35 / 36 |
| ADB 真机 | 设备 `9bf694eb`（已授权），可安装 / 调试 / 启动 |
| Gradle | 走 wrapper；本机网络可直连 `services.gradle.org`，构建正常 |

> **注意**：本项目 `settings.gradle.kts` **未配置国内镜像**（仅 google() / mavenCentral() / jitpack）。
> 本次构建网络直连成功；若日后依赖下载变慢或失败，需临时加阿里云/腾讯云镜像（改完记得还原）。

### 工程完整性说明

- 工程为 GitHub **zip 下载**，**无 `.git`** 仓库。
- 根目录子模块 `sdk/` 与 `GuiXu-Rust/` **为空**（zip 不含子模块内容）。
- **关键依赖**：`app/build.gradle.kts` 的 `generateThirdPartyAssets` 任务会从 `GuiXu-Rust/LICENSE`、`GuiXu-Rust/NOTICE` 复制文件作为三方许可资源，
  **目录为空会导致 `mergeAssets` 失败（构建挂掉）**。
  → 已从 GitHub 拉取真实文件：`Yukon163/GuiXu`（`rust-rewrite` 分支）的 LICENSE（11KB）与 NOTICE（477B），放到 `GuiXu-Rust/`。
  → **勿删除这两个文件**，否则无法构建。
- `local.properties` 缺失，但 `ANDROID_HOME` 环境变量可替代，不影响命令行构建。

---

## 3. 液态玻璃（Liquid Glass）视觉体系

### 3.1 核心机制

整个 App 的液态玻璃由 **一个全局开关驱动**，布局/组件结构 **完全一致**，仅切换卡片表面、背景、圆角与分隔线等视觉参数。

- **CompositionLocal**：`LocalIsLiquidGlassEnabled`（默认 `true`），声明于 `components/SettingsComponents.kt` 区域，定义在 `com.ahu.ahutong.ui.components` 包内。
- **开关注入点**：`ui/theme/AHUTheme.kt` 中 `CompositionLocalProvider(LocalIsLiquidGlassEnabled provides useLiquidGlass)`，
  其中 `useLiquidGlass` 来自**偏好设置**（`Preferences.kt` 的「液态玻璃」开关 → `PreferencesViewModel.useLiquidGlass`）。
- **同步结论**：任何可组合函数只要读取 `LocalIsLiquidGlassEnabled.current`，就**自动随着偏好开关实时切换**，无需额外传参或监听。
  → 因此设置页、校园卡、主页所有小卡片共享同一个开关，天然全局同步。

### 3.2 双模式视觉参数对照表

| 元素 | 液态玻璃（Liquid） | 传统材质（Solid） |
|------|--------------------|-------------------|
| 页面背景 | `surfaceContainerLowest` + 纵向渐变（混入 primary 8% / secondary 5%） | `surface` |
| Hero 卡 | 毛玻璃 vibrancy + blur 18dp + 阴影 14dp（黑 12%）；圆角 28dp | `primaryContainer` 实底；圆角 28dp |
| 分组卡 | 同上毛玻璃；圆角 26dp | `surfaceContainer`；圆角 24dp |
| 行为行图标 | 40dp / 圆角12 / `secondaryContainer` | 相同 |
| 分隔线 | `outlineVariant` α0.55 | α0.7 |
| 主页小卡片 | 毛玻璃；圆角 24dp；选中态 2dp 描边 | `100.n1 withNight 20.n1` 实色；选中 `90.a1` |

---

## 4. 本阶段改造记录（液态玻璃接入）

> 目标：把设置页的液态玻璃体系复用到主页卡片，并保持开关统一。

### 4.1 第一步：提取底层公共组件 —— 新建 `ui/components/LiquidGlass.kt`

将原本 `SettingsComponents.kt` 中 **private** 的液金相关逻辑提取为公共组件（全部 public），供任意页面复用：

| 组件 | 签名 | 说明 |
|------|------|------|
| `Modifier.liquidGlassSurface(backdrop, shape, surfaceColor)` | 扩展函数 | 毛玻璃绘制：`vibrancy()` + `blur(18.dp.toPx())` + `Shadow(14.dp, 黑12%)`，内部用 `drawRect(surfaceColor)` 作表面 |
| `liquidGlassTint()` | `@Composable Color` | 玻璃表面着色，按亮/暗自动取色：暗=`surfaceContainer α0.64`，亮=`White α0.46` |
| `GlassBackdropContainer(modifier, content)` | `@Composable`，`content: BoxScope.(Backdrop)->Unit` | 提供 backdrop 背景采样层 + 背景色；液态=`surfaceContainerLowest`+渐变，纯色=`surface` |

**关键认知**：`liquidGlassSurface` 依赖 kyant0 的 `Backdrop` 背景层对象。
要玻璃化某个卡片，其所在页面**必须有一层 `GlassBackdropContainer` 创建 backdrop 并传递给该卡片**。

### 4.2 设置页复用（`SettingsComponents.kt`）

- 删除原 private `liquidGlassSurface`、`settingsScreenBackground()`、旧的 `SettingsBackdropContainer` 实现。
- `SettingsBackdropContainer` 改为**委托**给公共 `GlassBackdropContainer`；各组件的玻璃 tint 计算统一改用 `liquidGlassTint()`。
- 三处（`SettingsPageHeader`/`SettingsHeroCard`/`SettingsSection`）的 `isDark+glassTint` 重复块合并为 `val glassTint = liquidGlassTint()`。
- `settingsGroupColor()`（分组卡表面）保留为设置页特有配色。
- **视觉保持不变**，仅消除重复代码。

### 4.3 主页接入

1. **`Home.kt`**：用 `GlassBackdropContainer(modifier = fillMaxSize()) { backdrop -> … }` **包裹整个 Home 内容**，
   拿到 `backdrop` 后传给 `HomeWidgetSlotLayout`，并在 `HomeWidgetDragOverlay` 调用处也传 `backdrop`。
2. **`HomeWidgetEditor.kt`**：
   - `HomeWidgetSlotLayout` 新增参数 `backdrop: Backdrop`，
     **所有 8 个插槽**（顶部 column 的 slot①、slot② + 三对行 3|4、5|6、7|8）都传入 `backdrop = backdrop`。
     用 `replace_all` 对相同结尾 `onDragStopped = … )` 批量插入。
   - `HomeWidgetSlot` 新增 `backdrop` 并透传给 `TextHomeWidgetCard`。
   - `HomeWidgetDragOverlay` 新增 `backdrop` 并透传。
3. **`CampusCard.kt`**（校园卡余额横卡）：`CampusCard` / `CardView` 新增 `backdrop` 参数；
   液态时用 `liquidGlassSurface`，非液态保留 `100.n1 withNight 20.n1`。

### 4.4 第二批：所有小卡片玻璃化（`TextHomeWidgetCard`）

- **唯一入口**：所有已添加的小工具（浴室、电费、成绩、电话本…）最终都渲染在 `TextHomeWidgetCard`，
  因此只需修改这一处即可覆盖全部 8 个插槽的小卡片。
- 改动要点：
  - 读取 `LocalIsLiquidGlassEnabled`：**开 → `liquidGlassSurface`；关 → 原 `100.n1` 实色**。
  - **文字对比度**：液态态明确用 `color = onSurface`（跟随明暗主题），非液态用 `Color.Unspecified`（默认）。
  - **选中态区分**：液态下 `isHighlighted` 时叠加 `2.dp` 描边 `80.a1 withNight 90.a1`。

### 4.5 范围决策（用户明确）

- **本轮已改**：校园卡 + 所有已放置的小卡片 + 拖拽预览。
- **暂不改（保留现状）**：
  - 抽屉内候选块 `LibraryWidgetItem`（`HomeWidgetEditor.kt`，88dp 小图块）
  - 空插槽 `EmptyHomeWidgetSlot`（"拖到这里" 虚线框，避免干扰拖拽可读性）

---

## 5. 踩坑 / 关键经验（务必牢记）

### 5.1【高价值】`.clip(shape)` 会把液金投影阴影裁掉 ★

- **现象**：小卡片玻璃化后没有阴影，而校园卡阴影正常。
- **根因**：`liquidGlassSurface` 的阴影通过 `drawBackdrop` 绘制在**形状边界外**；
  若在修饰器**链首**加了 `.clip(shape)`，会把边界外的阴影投影**直接裁剪掉**。
- **正确写法**（`clip` 只放进非液态分支）：
  ```kotlin
  modifier
      .then(if (isLiquid) Modifier.liquidGlassSurface(backdrop, shape, liquidGlassTint())
            else Modifier.clip(shape).background(...))
  ```
- **排查思路**：凡玻璃卡无阴影，先检查修饰器链首/外层是否有 `clip`。

### 5.2 backdrop 必须由页面最外层容器提供

玻璃卡要生效必须有 `Backdrop` 对象。要么页面用 `GlassBackdropContainer` 包裹，要么复用已有的 backdrop 并逐层下传。
backdrop 的传递链路是「页面容器 → … → 卡片」，改动时保持链路完整。

### 5.3 Debug 包组件全限定名

Debug 构建 `applicationIdSuffix = ".debug"`，包名为 `com.ahu.ahutong.debug`，
但 **MainActivity 组件全名是 `com.ahu.ahutong.MainActivity`（类实际包名，不含 debug 后缀）**。
启动命令：
```bat
adb shell am start -n "com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity"
```

### 5.4 编译/空闲进程注意

- 多次编译会触发配置缓存复用与增量编译，改动后 `assembleDebug` 通常 6–11s 完成。
- 网络需能直连 `services.gradle.org` 与 `^raw.githubusercontent.com`（拉子模块文件时用到）。

---

## 6. 构建 / 部署命令速查（Windows PowerShell）

```powershell
# 工作目录
cd 'C:\Users\InChange_Jiang\Documents\AHUTong-UIDesinger\AHUTong-Android-master'

# 仅编译 Kotlin（快速查语法）
.\gradlew.bat compileDebugKotlin

# 构建 Debug APK
.\gradlew.bat assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk

# 设备连接
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices

# 安装并重启（含启动命令）
& $adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
& $adb shell am force-stop com.ahu.ahutong.debug
& $adb shell am start -n 'com.ahu.ahutong.debug/com.ahu.ahutong.MainActivity'

# 校验：
& $adb shell dumpsys window | Select-String 'mCurrentFocus'   # 应显示 MainActivity 在前台
& $adb shell pidof com.ahu.ahutong.debug                      # 有输出即进程存活
& $adb logcat -d -s AndroidRuntime:E | Select-String 'com.ahu.ahutong'  # 空即无本应用崩溃
```

**注意**：若 `am start` 报 `Activity class does not exist`，多半是用错了类全名（见 5.3）。
设备偶尔会从 ADB 掉线（USB 重连/授权失效），先 `adb kill-server; adb start-server` 再 `adb devices`。

---

## 7. 真机验证流程（已跑通）

1. 构建 Debug APK。
2. `adb install -r` 安装。
3. 冷启动：`am force-stop` → `am start -n …/com.ahu.ahutong.MainActivity`。
4. 验证：前台焦点 = `com.ahu.ahutong.debug/MainActivity`，`pidof` 有值，无崩溃日志。
5. **液态玻璃效果验证**：设置 → 偏好 → 「液态玻璃」开关；开启后主页的校园卡 + 小卡片即时变毛玻璃，关闭恢复纯色。

---

## 8. 已知后续方向（尚未做，待确认）

- （可选）抽屉内候选块 `LibraryWidgetItem` 玻璃化 —— 用户暂缓。
- （可选）空插槽 `EmptyHomeWidgetSlot` 玻璃化 —— 用户暂缓，默认保持虚线框。
- 主页背景层目前已随开关打淡渐变（`GlassBackdropContainer` 液态分支），如需更强玻璃质感可再增强渐变层级。

---

## 10. 学习通日历提级 + 底部导航切换（近期改造）

> 把原二级页「学习通日历」提级为底部导航第 3 个一级 tab「日程」，并让该 tab 的图标/文字随内部子页（日程/课程）动态变化、再次点击轮换子页。真机已验证三种场景全部 OK。

### 10.1 交互设计（用户拍板）

- 底部导航第 3 个 tab 由「小工具」改为「日程」，点击进入学习通日历（未登录先进其登录页）。
- 日历页原内部底部悬浮 Dock（日程/课程）**移除**，子页切换改由底部导航第 3 个 tab 驱动：
  - **从别的页**点该 tab → 纯导航进入，停在图标对应的那个子页（不切子页）。
  - **已在日历页**再点该 tab → `日程 ⇄ 课程` 轮换。图标文字：日程=`计划`、课程=`学位帽`，随子页联动。
  - 离开再回来 → 停留在上次子页。
- 原主页 / 更多页里的「学习通日历」入口已**隐藏**（只保留新 tab 一处入口）。

### 10.2 技术实现

| 文件 | 改动 |
|------|------|
| `ui/screen/xuexiaotong/XuexiaotongDockState.kt`（新增） | 全局 `enum XuexiaotongSubTab { SCHEDULE, COURSE }` + `object XuexiaotongDockState { var tab; fun toggle() }`。日历页与导航条共享同一状态，实现「重进停留上次子页」 |
| `XuexiaotongScreen.kt` | 删本地 `Tab` 枚举与局部 `tab`，改读 `XuexiaotongDockState.tab`；删 `onBack` 参数与底部悬浮 `BottomDock`（含 `DampedDragAnimation`）；根列加 `bottom = 108.dp` 给导航条让位 |
| `BottomNavBar.kt` | 第 3 tab route `tools`→`xuexiaotong`；label/icon 随 `XuexiaotongDockState.tab` 动态（`ic_nav_plan` / `ic_nav_degree_hat`）；拆出 `onTabTapped`（真实点击：已在本页→切子页）与 `navigateToTab`（拖动/校正：只导航）；给 `LiquidBottomTabs` 传 `onCurrentTabTapped` |
| `LiquidBottomTabs.kt` | 新增可选参数 `onCurrentTabTapped`，在拖动滑块层上做**非消费** tap 识别（纯点击触发、拖动不触发），解决拖动层吞掉「当前 tab 的点击」 |
| `Main.kt` | `xuexiaotong` 提级（去 `onBack`）；`SmartSuggestionHost` 一级页判定 `tools`→`xuexiaotong` |
| `HomeWidgetRegistry.kt` + `Home.kt` | 移除 `xuexiaotong` 入口并清理已固定插槽残留 id |
| `res/drawable/ic_nav_plan.xml` / `ic_nav_degree_hat.xml`（新增） | 由 iconpark SVG（计划/学位帽）按 `compose-svg-icon-replacer` 流程转为 VectorDrawable |

### 10.3 关键踩坑（务必牢记）

- **导航与「切子页」必须用两个独立入口**，切勿共用一个 `onTabSelect`：Liquid 拖动条在**进入每页时会触发一次 `onTabSelected` 校正**，若并入切子页逻辑，会从别的页进入时「误切一次」（用户第一轮反馈的现象 1）。
- **拖动条顶层滑块盒覆盖「当前选中 tab」区域**，会吞掉该 tab 的点击，导致「已在该页再点没反应」（现象 2）。解决的干净办法：在滑块盒 Modifier 链**末尾**追加一个 `pointerInput`，用 `awaitFirstDown(requireUnconsumed=false) + waitForUpOrCancellation()` 做**非消费** tap 检测——纯点击时触发、真正拖动时因事件被 consume 而 cancel，互不干扰。
- `waitForUpOrCancellation { }` 的空尾块会被解析成 `PointerEventPass` 参数而编译报 `Function0<Unit> but PointerEventPass expected`。改传 `waitForUpOrCancellation()`（无参）即得默认 pass。
- 本地函数与 `LiquidBottomTabs` 的参数 `onTabSelected` **重名**会互相遮蔽导致误递归，本地导航函数应改名（如 `navigateToTab`）。
- `XuexiaotongScreen` 现在由底部导航驱动切页，`AnimatedContent(targetState = XuexiaotongDockState.tab)` 会随共享状态自动重绘，无需在 screen 内再维护本地状态。

---

## 11. 校园卡二维码展开动画的投影被裁剪——根治经验（近期）

> 校园卡「余额条(78dp) ⇄ 二维码卡(≈300dp)」切换用了 `AnimatedContent` + `SizeTransform(clip=true)` 做拉伸动画。用户连续反馈：动画中**投影出现跳变 / 内容溢出**。最终用「外层即卡片本体、投影置于裁剪层之外」根治，用户满意。这是「Compose 动画中投影被裁剪」的通用解法。

### 11.1 问题演变与踩坑

| 尝试 | 结果 | 教训 |
|------|------|------|
| `SizeTransform(clip=true)`（卡片自带投影） | 投影被**帧级裁剪**，动画中阴影忽隐忽现 | `clip=true` 每帧用一个「当前插值尺寸」的矩形裁剪内容，超出部分（含投影）被切断 |
| `SizeTransform(clip=false)` | 投影不被裁，但**内容溢出**，动画中二维码提前压在下方内容上 | 关闭裁剪=内容也完整绘制，底部内容溢出 |
| 内层去投影 + 外层 `Modifier.shadow()` 统一绘制 | 阴影连续、不溢出，但出现**顶部黑影 + 内部明暗分界线** | `Modifier.shadow` 是绕整形的环境光投影（含顶部），且投影层与卡片轮毂错位时，阴影内缘落在卡片表面上形成分界线 |
| **外层 Box 即卡片本体(`liquidGlassSurface` 自带投影) + 内层 `AnimatedContent(clip=true)`** | ✅ 投影连续、贴卡、不溢出、无顶部黑影 | 投影属于卡片、位于裁剪层**之外**；内容仍被内层裁剪不溢出。二者不再矛盾 |

### 11.2 最终结构（直接复用）

```kotlin
val campusShape = SmoothRoundedCornerShape(24.dp)
Box(            // ✅ 外层 Box 就是卡片本体
    modifier = Modifier.fillMaxWidth()
        .then(if (LocalIsLiquidGlassEnabled.current) {
            Modifier.liquidGlassSurface(backdrop, campusShape, liquidGlassTint()) // 自带投影
        } else {
            Modifier.clip(campusShape).background(100.n1 withNight 20.n1)
        })
) {
    AnimatedContent(isQrcode,
        transitionSpec = { (fadeIn() togetherWith fadeOut()).using(SizeTransform(clip = true)) },
        contentAlignment = Alignment.TopStart) {
        // 内层两张卡（余额条 / QR 卡）只放透明内容，不再各自带表面/背景
    }
}
```

### 11.3 要点

- **投影必须放在裁剪单元之外**。只要卡片自身投影与 `SizeTransform` 的裁剪框同层，就一定被切。把「表面+投影」整体提升到外层作为卡片本体，内层真正需要裁的是「内容」。
- 内层 `CardView` / `QRcodeView` 去掉了各自的 `liquidGlassSurface`（改传 `shadowRadius=0` 此路不通，直接剥离去表面），改由外层统一提供；QR 内容加 `fillMaxWidth()` 以填满外层卡。
- `liquidGlassSurface` 增强为可选 `shadowRadius: Dp = 14.dp`（`ui/components/LiquidGlass.kt`），默认不变，需要时传 `0` 可关投影。
- 若确实只需「画一个贴卡片的投影」，用 `Modifier.shadow` 会带顶部环境光、易错位；贴合作法应优先「卡片自身投影置于裁剪层外」。

---

## 12. 用户设计偏好（UI 优化需遵循，来自长期上下文）

- 界面追求**简洁、美观、成熟**。
- 标题栏为纯导航并**固定在顶部**，搜索/筛选等作为可滚动内容；标题栏只滚动内容，右侧放搜索/筛选/刷新。
  - 三个极简 logo 按钮：搜索、筛选、更多（下拉含发布 / 我的发布等）；按钮等距（参考成绩单页三按钮间隔 8）。
- 搜索有弹出动画、占满标题栏搜索框与按钮；筛选为独立弹窗；去掉返回按钮改用系统返回。
- 按钮边距、文字大小等**设计语言保持统一**；注重运行动画规范。
- 偏好隐藏技术细节，倾向自动化的稳定流程，先保证核心功能。
- 允许大胆重构；倾向使用官方/现成组件；解决问题用简单直接方案。
- 沟通带情绪时较直接，不必介意直言。

---

*备用：授权服务（Feishu/Lark 等）与更高层协作流程未在本阶段涉及，如需扩展再补充。*