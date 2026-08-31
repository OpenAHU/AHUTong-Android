package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape

/**
 * 统一的玻璃化卡片容器（安全实现，不使用 liquidGlassSurface/backdrop）。
 *
 * 只在 RadiantUI 下渲染高光玻璃观感（半透明玻璃底 + 1dp 高光描边 + 柔和阴影）；
 * 其余模式回退为 `clip + background(containerColor)` 的实色原观感，保证 OR/LG 冻结。
 *
 * 注意：项目内 `liquidGlassSurface` 在 NavHost 子页面/destination 中使用会触发
 * libhwui 无限递归崩溃（见 ChangeableUI §7.3），故这里统一用伪玻璃模拟，零崩溃风险。
 *
 * @param overlayColor 玻璃上额外叠的一层底色（如雨伞卡的蓝/绿语义色薄层）；非玻璃分支忽略
 * @param glassShadow 玻璃底投影高度（Radiant 生效）
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    overlayColor: Color? = null,
    shape: Shape = SmoothRoundedCornerShape(24.dp),
    glassShadow: androidx.compose.ui.unit.Dp? = 14.dp,
    content: @Composable () -> Unit = {}
) {
    val glass = isRadiantUi
    // 玻璃底用完全不透明的主题表面色（随亮/暗取白/黑系），杜绝阴影从卡片内部透出
    val base = if (glass) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        containerColor
    }
    Box(
        modifier = modifier.then(
            if (glass) {
                Modifier
                    .then(if (glassShadow != null) Modifier.shadow(glassShadow, shape, clip = false) else Modifier)
                    .background(base, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            } else {
                Modifier
                    .clip(shape)
                    .background(containerColor)
            }
        )
    ) {
        if (glass && overlayColor != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(overlayColor)
            )
        }
        content()
    }
}