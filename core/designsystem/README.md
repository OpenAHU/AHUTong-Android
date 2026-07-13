# `:core:designsystem` — AHUTong 统一 UI 库

业务 feature **不要**直接声明 Compose / Material3 / Monet 等依赖，只依赖本模块。

## 依赖契约

```kotlin
// feature/xxx/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // 仍需要：编译 @Composable
    // hilt / ksp …
}

android {
    buildFeatures { compose = true } // 仍需要
}

dependencies {
    api(project(":core:designsystem"))
    // api(project(":data:xxx")) 等业务依赖
    // ❌ 不要再写 androidx.compose / material3 / monet / capsule / navigation-compose
}
```

本模块用 `api` 透出：

- Compose BOM + ui / foundation
- Material3 + Icons Extended
- Activity Compose / Navigation Compose / Hilt Navigation Compose
- Runtime LiveData
- Coil Compose
- Monet / Capsule / Backdrop

## 常用 API

### Tokens

```kotlin
AhuDimens.ContentHorizontal   // 16.dp
AhuDimens.CardCorner          // 32.dp
AhuDimens.BottomNavClearance  // 96.dp
AhuColors.pageBackground
AhuColors.card
AhuColors.primaryAction
```

### 页面骨架

```kotlin
AhuScreen(clearBottomNav = true) {
    AhuPageHeader(title = "标题") {
        AhuHeaderIconButton(Icons.Default.Refresh, "刷新") { /* … */ }
    }
    AhuInsetCard {
        Text("内容")
    }
    AhuSectionTitle("分组")
    AhuListGroup {
        AhuListItem(label = "条目", icon = Icons.Outlined.Tune, onClick = { })
    }
}
AhuErrorToastEffect(errorMessage) { viewModel.errorMessage = null }
```

### 已有特效组件

- `LiquidBottomTabs` / `LiquidButton` / `LiquidToggle` / `LiquidSlider`
- `SmoothRoundedCornerShape`
- `animatedComposable`（导航转场）

## 边界说明

| 可以 | 不要期望 |
|------|----------|
| feature 源码 `import androidx.compose…` 写布局 | 完全禁止 Compose API（不现实：Screen 本身就是 Compose） |
| Gradle 只声明 designsystem | 去掉 `kotlin.compose` 插件 |
| 新视觉能力进 designsystem | 在各 feature 复制一份卡片/列表样式 |

Glance 桌面微件（`:feature:widget`）额外依赖 Glance，不受本契约约束。
