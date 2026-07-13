package com.ahu.ahutong.ui

/**
 * AHUTong unified UI library entry documentation.
 *
 * ## Module
 * `:core:designsystem` — the **only** UI dependency feature modules should declare
 * for Compose / Material3 / Monet / navigation helpers.
 *
 * ## What lives here
 * | Package | Contents |
 * |---------|----------|
 * | `com.ahu.ahutong.ui.theme` | [AhuDimens], [AhuColors], [AhuTheme] |
 * | `com.ahu.ahutong.ui.components` | Page shell, cards, buttons, lists, loading… |
 * | `com.ahu.ahutong.ui.shape` | [SmoothRoundedCornerShape] |
 * | `com.ahu.ahutong.utils` | [animatedComposable] |
 *
 * ## Feature Gradle contract
 * ```kotlin
 * dependencies {
 *     api(project(":core:designsystem"))
 *     // domain: api(project(":data:xxx"))
 *     // NO androidx.compose.* / monet / capsule / material3 direct deps
 * }
 * ```
 * Keep `alias(libs.plugins.kotlin.compose)` + `buildFeatures { compose = true }`
 * so `@Composable` code still compiles; the libraries themselves come via designsystem `api`.
 *
 * ## Preferred page skeleton
 * ```kotlin
 * AhuScreen(clearBottomNav = true) {
 *     AhuPageHeader(title = "标题") {
 *         AhuHeaderIconButton(Icons.Default.Refresh, "刷新") { vm.refresh() }
 *     }
 *     AhuInsetCard {
 *         Text("内容", style = MaterialTheme.typography.bodyLarge)
 *     }
 *     AhuSectionTitle("分组")
 *     AhuListGroup {
 *         AhuListItem(label = "条目", icon = Icons.Outlined.Tune, onClick = { })
 *     }
 * }
 * AhuErrorToastEffect(error) { vm.error = null }
 * ```
 *
 * ## Still allowed low-level Compose
 * Feature screens may import `androidx.compose.*` for layout primitives
 * (`Modifier`, `Row`, `LazyColumn`…). Prefer design-system components for
 * surfaces, headers, list rows, and colors so visual language stays unified.
 */
object AhuUi
