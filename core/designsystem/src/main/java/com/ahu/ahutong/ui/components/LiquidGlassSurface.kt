package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.LiquidGlassQuality
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.ahu.ahutong.ui.theme.LocalLiquidGlassTokens
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalLiquidGlassAmbientBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

val LocalLiquidGlassContentBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

private val LocalLiquidGlassContentLayer = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * Owns the two backdrop layers used by the app.
 *
 * The ambient layer contains only the stable background, so a glass card never samples itself.
 * The content layer is captured separately for navigation and other overlays that should show the
 * page underneath them.
 */
@Composable
fun LiquidGlassAppHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalLiquidGlassTokens.current
    val ambientLayer = rememberLayerBackdrop()
    val contentLayer = rememberLayerBackdrop()
    val capturesBackdrop = tokens.quality.supportsBackdrop
    val ambientBackdrop: Backdrop = if (capturesBackdrop) ambientLayer else emptyBackdrop()
    val contentBackdrop: Backdrop = if (capturesBackdrop) contentLayer else emptyBackdrop()
    val appTheme = LocalAppUiTheme.current
    val background = when (appTheme) {
        AppUiTheme.MIUIX -> MiuixTheme.colorScheme.surface
        AppUiTheme.MATERIAL -> MaterialTheme.colorScheme.background
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> tokens.screenBackground
    }

    Box(modifier = modifier.background(background)) {
        if (tokens.enabled) {
            val primary = tokens.ambientPrimary.compositeOver(background)
            val secondary = tokens.ambientSecondary.compositeOver(background)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (capturesBackdrop) Modifier.layerBackdrop(ambientLayer) else Modifier
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(background, primary, secondary, background)
                        )
                    )
            )
        }

        CompositionLocalProvider(
            LocalLiquidGlassAmbientBackdrop provides ambientBackdrop,
            LocalLiquidGlassContentBackdrop provides contentBackdrop,
            LocalLiquidGlassContentLayer provides contentLayer.takeIf { capturesBackdrop }
        ) {
            content()
        }
    }
}

/** Captures page content only while liquid glass is enabled. */
@Composable
fun Modifier.captureLiquidGlassContent(): Modifier {
    val layer = LocalLiquidGlassContentLayer.current
    return if (layer != null) layerBackdrop(layer) else this
}

/**
 * Applies the shared liquid-glass material and preserves the supplied opaque fallback when the
 * preference is disabled.
 */
/** 自定义主页背景开启时，由主页注入 true：玻璃表面加模糊、加 tint 以保住文字可读性。 */
val LocalGlassReadabilityBoost = compositionLocalOf { false }

@Composable
fun Modifier.appLiquidGlassSurface(
    shape: Shape,
    fallbackColor: Color,
    level: LiquidGlassSurfaceLevel = LiquidGlassSurfaceLevel.Panel,
    backdrop: Backdrop? = null,
    backdropSamplingEnabled: Boolean = false,
    blurRadiusMultiplier: Float = 1f,
    tintAlphaMultiplier: Float = 1f
): Modifier {
    // 可读性增强：自定义背景开启时全场景生效（blur ×1.8、tint ×1.5）
    val boost = if (LocalGlassReadabilityBoost.current) 1.8f to 1.5f else 1f to 1f
    val effectiveBlurMultiplier = blurRadiusMultiplier * boost.first
    val effectiveTintMultiplier = tintAlphaMultiplier * boost.second
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val miuixShape = SmoothRoundedCornerShape(
            when (level) {
                LiquidGlassSurfaceLevel.Control -> 12.dp
                LiquidGlassSurfaceLevel.Panel -> 16.dp
                LiquidGlassSurfaceLevel.Floating -> 20.dp
            }
        )
        val miuixColor = when (level) {
            LiquidGlassSurfaceLevel.Control -> MiuixTheme.colorScheme.secondaryVariant
            LiquidGlassSurfaceLevel.Panel -> MiuixTheme.colorScheme.surfaceContainer
            LiquidGlassSurfaceLevel.Floating -> MiuixTheme.colorScheme.surfaceContainerHighest
        }
        return clip(miuixShape).background(miuixColor)
    }
    val tokens = LocalLiquidGlassTokens.current
    val style = tokens.surface(level)
    val renderingQuality = if (backdropSamplingEnabled) {
        tokens.quality
    } else if (tokens.enabled) {
        LiquidGlassQuality.Tinted
    } else {
        LiquidGlassQuality.Disabled
    }

    return when (renderingQuality) {
        LiquidGlassQuality.Disabled ->
            clip(shape).background(fallbackColor)

        LiquidGlassQuality.Tinted ->
            clip(shape)
                .background(
                    style.legacyTint.copy(
                        alpha = (style.legacyTint.alpha * effectiveTintMultiplier).coerceIn(0f, 1f)
                    )
                )
                .border(0.75.dp, style.outline, shape)

        LiquidGlassQuality.Blurred,
        LiquidGlassQuality.Refractive -> {
            val source = backdrop ?: LocalLiquidGlassAmbientBackdrop.current
            val canRefract = renderingQuality.supportsRefraction &&
                level != LiquidGlassSurfaceLevel.Panel &&
                shape is CornerBasedShape &&
                style.refractionHeight > 0.dp &&
                style.refractionAmount > 0.dp

            drawBackdrop(
                backdrop = source,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(style.blurRadius.toPx() * effectiveBlurMultiplier.coerceAtLeast(0f))
                    if (canRefract) {
                        lens(
                            refractionHeight = style.refractionHeight.toPx(),
                            refractionAmount = style.refractionAmount.toPx()
                        )
                    }
                },
                highlight = {
                    Highlight.Ambient.copy(alpha = style.highlightAlpha)
                },
                shadow = {
                    Shadow(
                        radius = style.shadowRadius,
                        color = style.shadowColor
                    )
                },
                onDrawSurface = {
                    drawRect(
                        style.tint.copy(
                            alpha = (style.tint.alpha * tintAlphaMultiplier).coerceIn(0f, 1f)
                        )
                    )
                }
            ).border(0.75.dp, style.outline, shape)
        }
    }
}

/** Makes a scene transparent only while its ambient host is active. */
@Composable
fun Modifier.appLiquidGlassSceneBackground(fallbackColor: Color): Modifier {
    return background(
        when (LocalAppUiTheme.current) {
            AppUiTheme.MIUIX -> MiuixTheme.colorScheme.surface
            AppUiTheme.MATERIAL -> fallbackColor
            AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> if (LocalLiquidGlassTokens.current.enabled) {
                Color.Transparent
            } else {
                fallbackColor
            }
        }
    )
}
