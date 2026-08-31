package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * 液态玻璃：将当前组件表面的背景替换为毛玻璃质感。
 * 依赖页面最外层由 [GlassBackdropContainer] 创建的 backdrop 背景层。
 */
fun Modifier.liquidGlassSurface(
    backdrop: Backdrop,
    shape: Shape,
    surfaceColor: Color,
    shadowRadius: Dp = 14.dp
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(18.dp.toPx())
    },
    shadow = {
        Shadow(
            radius = shadowRadius,
            color = Color.Black.copy(alpha = 0.12f)
        )
    },
    onDrawSurface = {
        drawRect(surfaceColor)
    }
)

/** 液态玻璃卡片表面的着色，随亮/暗主题自动取色。 */
@Composable
fun liquidGlassTint(): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    } else {
        Color.White.copy(alpha = 0.46f)
    }
}

/**
 * 液态玻璃容器：为页面提供 backdrop 背景采样层与材质背景。
 * 开启液态玻璃时为素色底 + 渐变色带（增强玻璃质感），关闭时为纯 surface。
 */
@Composable
fun GlassBackdropContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Backdrop) -> Unit
) {
    val backdrop = rememberLayerBackdrop()
    val liquid = LocalIsLiquidGlassEnabled.current
    val background = if (liquid) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surface
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(modifier = modifier.background(background)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clipToBounds()
                .layerBackdrop(backdrop)
                .background(
                    if (liquid) {
                        Brush.verticalGradient(
                            listOf(
                                background,
                                primary.copy(alpha = 0.08f),
                                secondary.copy(alpha = 0.05f),
                                background
                            )
                        )
                    } else {
                        Brush.linearGradient(listOf(background, background))
                    }
                )
        )
        content(backdrop)
    }
}