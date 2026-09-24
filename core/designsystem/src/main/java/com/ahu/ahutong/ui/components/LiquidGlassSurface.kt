package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
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
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

val LocalLiquidGlassAmbientBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

val LocalLiquidGlassContentBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

data class AppBackground(val image: ImageBitmap, val maskAlpha: Float)

val LocalAppBackground = compositionLocalOf<AppBackground?> { null }

@Composable
fun BoxScope.AppBackgroundLayer(background: AppBackground) {
    Image(
        bitmap = background.image,
        contentDescription = null,
        modifier = Modifier.matchParentSize(),
        contentScale = ContentScale.Crop
    )
    if (background.maskAlpha > 0f) {
        Box(
            modifier = Modifier.matchParentSize().background(
                (if (isSystemInDarkTheme()) Color.Black else Color.White)
                    .copy(alpha = background.maskAlpha)
            )
        )
    }
}

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
    val appTheme = LocalAppUiTheme.current
    val appBackground = LocalAppBackground.current
    val capturesAmbientBackdrop = tokens.quality.supportsBackdrop || appBackground != null
    val capturesContentBackdrop = tokens.quality.supportsBackdrop
    val ambientBackdrop: Backdrop = if (capturesAmbientBackdrop) ambientLayer else emptyBackdrop()
    val contentBackdrop: Backdrop = if (capturesContentBackdrop) contentLayer else emptyBackdrop()
    val background = when (appTheme) {
        AppUiTheme.MIUIX -> MiuixTheme.colorScheme.surface
        AppUiTheme.MATERIAL -> MaterialTheme.colorScheme.background
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> tokens.screenBackground
    }

    Box(modifier = modifier.background(background)) {
        if (tokens.enabled || appBackground != null) {
            val primary = tokens.ambientPrimary.compositeOver(background)
            val secondary = tokens.ambientSecondary.compositeOver(background)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (capturesAmbientBackdrop) Modifier.layerBackdrop(ambientLayer) else Modifier
                    )
                    .then(
                        if (appBackground == null) Modifier.background(
                            Brush.verticalGradient(
                                listOf(background, primary, secondary, background)
                            )
                        ) else Modifier
                    )
            ) {
                appBackground?.let { AppBackgroundLayer(it) }
            }
        }

        CompositionLocalProvider(
            LocalLiquidGlassAmbientBackdrop provides ambientBackdrop,
            LocalLiquidGlassContentBackdrop provides contentBackdrop,
            LocalLiquidGlassContentLayer provides contentLayer.takeIf { capturesContentBackdrop }
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
/** 自定义全局背景开启时，玻璃表面加模糊与着色以保住文字可读性。 */
val LocalGlassReadabilityBoost = compositionLocalOf { false }

/** MIUIX / Material 在自定义背景上使用的轻量毛玻璃表面。 */
@Composable
fun Modifier.appWallpaperFrostedSurface(
    shape: Shape,
    tint: Color,
    blurRadius: Dp = 18.dp
): Modifier {
    if (LocalAppBackground.current == null) return this
    val backdrop = LocalLiquidGlassAmbientBackdrop.current
    return drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { blur(blurRadius.toPx()) },
        onDrawSurface = { drawRect(tint) }
    )
}

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
        return if (LocalAppBackground.current != null) {
            appWallpaperFrostedSurface(miuixShape, miuixColor.copy(alpha = 0.66f))
        } else {
            clip(miuixShape).background(miuixColor)
        }
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
            if (LocalAppBackground.current != null && LocalAppUiTheme.current == AppUiTheme.MATERIAL) {
                appWallpaperFrostedSurface(shape, fallbackColor.copy(alpha = 0.66f))
            } else {
                clip(shape).background(fallbackColor)
            }

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
        if (LocalAppBackground.current != null) Color.Transparent else when (LocalAppUiTheme.current) {
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
