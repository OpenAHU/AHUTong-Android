package com.ahu.ahutong.ui.components

import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import com.ahu.ahutong.ui.theme.LocalLiquidGlassTokens
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Preserve wallpaper under native Miuix headers while keeping both title sizes readable. */
@Composable
internal fun Modifier.miuixWallpaperHeaderScrim(): Modifier {
    if (LocalAppBackground.current == null) return this
    val surface = MiuixTheme.colorScheme.surface
    val scrim = remember(surface) {
        Brush.verticalGradient(
            0f to surface.copy(alpha = 0.92f),
            0.65f to surface.copy(alpha = 0.82f),
            0.9f to surface.copy(alpha = 0.64f),
            1f to surface.copy(alpha = 0f)
        )
    }
    return background(scrim)
}

/**
 * 一级页固定标题栏遮罩（共享实现，替代各页复制的 4 段式 verticalGradient）。
 *
 * 三态路由（挂接性能开关 LocalGlassEffectsReduced）：
 * 1. 性能开关开启（关闭玻璃效果）→ 纯色 4 段渐变遮罩，零采样开销（原效果降级）
 * 2. 液态玻璃可用且性能开关关闭 → 渐变式毛玻璃（nav 同款浓度配方）：
 *    顶部浓度 = 底部导航栏（vibrancy + floating blur ×1.5 + 0.4 容器色 tint），
 *    向下按 pow(1-y/h, 0.35) 非线性曲线收敛到底部 0%（内容完全清晰）。
 *    API 33+ 用 AGSL 逐行调制不透明度；API 31-32 无 AGSL 退化为均匀毛玻璃 + 线性 tint
 * 3. 其余（液态关闭 / 低端设备不支持 blur）→ 纯色渐变遮罩兜底
 *
 * 模糊采样源 = 全局背景层（壁纸/场景渐变，ambient）+ 页面内容层（[headerBackdropSource]
 * 挂在滚动内容上）的组合。壁纸画在 ambient 层，只采内容层会漏掉壁纸——头部静止区域是
 * 透明像素，blur 后仍透明，视觉上"完全没有模糊"（Main 导航栏 P2 回归同款坑）。
 * 不复用 Main 的 NavHost content 层——标题栏自身在那层里，直接采样会产生自反馈。
 */
@Composable
fun Modifier.headerScrim(headerBg: Color, contentLayer: LayerBackdrop? = null): Modifier {
    if (contentLayer == null || !isHeaderBlurActive()) {
        return background(headerScrimGradient(headerBg))
    }
    // 采样源组合：页面内容层 + 全局背景层（壁纸在 ambient 层，仅采内容层会漏掉壁纸 →
    // 头部静止区域采到透明像素，blur 后仍透明，表现为"看不到任何模糊"，即 Main 导航栏
    // P2 回归的同款坑，解法同 rememberCombinedBackdrop(ambient, content)）
    val source = rememberCombinedBackdrop(LocalLiquidGlassAmbientBackdrop.current, contentLayer)
    // 与底部导航栏（LiquidBottomTabs）同款强度参数：vibrancy + floating 级 blur 半径
    val blurRadiusPx = with(LocalDensity.current) {
        LocalLiquidGlassTokens.current.floating.blurRadius.toPx()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // 渐变式毛玻璃：均匀 blur 后接 AGSL 按行调制 alpha（顶部全显模糊 → 底部 0）。
        // 浓度对齐底部导航栏（LiquidBottomTabs）的三要素：
        // 1. blur 半径 = floating.blurRadius × [HEADER_BLUR_BOOST]（1.5：用户实测仍偏弱）
        // 2. 顶部 alpha = 1（满浓度），但衰减曲线非线性 pow(1-y/h, 0.35)——
        //    纯线性时标题行所在位置（60-80% 高度）浓度只剩 20-40%，观感远弱于
        //    全高满浓度的 nav；非线性让顶部大区间保持接近满浓、底部快速收敛 0
        // 3. nav 观感浓度的一半来自 onDrawSurface 的 0.4 容器色 tint（毛玻璃在
        //    低对比背景上不"显形"）——这里叠加同款 tint，且 alpha 同曲线衰减
        // backdrop 1.0.0 无 runtimeShaderEffect 扩展，走 scope.obtainRuntimeShader
        // + RenderEffect.createRuntimeShaderEffect 手工链（内部就是官方链式合成）
        return drawPlainBackdrop(
            backdrop = source,
            shape = { RectangleShape },
            effects = {
                vibrancy()
                blur(blurRadiusPx * HEADER_BLUR_BOOST)
                val shader = obtainRuntimeShader(
                    "header_progressive_blur",
                    PROGRESSIVE_BLUR_SHADER
                )
                shader.setFloatUniform("height", size.height.coerceAtLeast(1f))
                effect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content")
                        .asComposeRenderEffect()
                )
            },
            onDrawSurface = {
                // nav 同款容器色（浅色 0xFFFAFAFA / 深色 0xFF121212，0.4 顶部），
                // alpha 沿 pow(1-y/h, 0.35) 曲线衰减（与 AGSL alpha 调制同形状）
                drawRect(headerTintGradient(isLightTheme = headerBg.luminance() > 0.5f))
            }
        )
    }
    // API 31-32：RenderEffect 可用但无 AGSL，退化为均匀毛玻璃 + 线性 tint 渐变
    return drawPlainBackdrop(
        backdrop = source,
        shape = { RectangleShape },
        effects = {
            vibrancy()
            blur(blurRadiusPx * HEADER_BLUR_BOOST)
        },
        onDrawSurface = {
            drawRect(headerTintGradient(isLightTheme = headerBg.luminance() > 0.5f))
        }
    )
}

/**
 * 渐变模糊 AGSL：输入为已模糊的背景层（预乘 alpha），
 * 按像素 y 调制不透明度——顶部与底部导航栏同强度（全显模糊采样），底部收敛到 0。
 * 衰减用 pow(t, 0.35) 而非线性：线性在 60-80% 高度处只剩 20-40% 浓度（标题行位置），
 * 观感远弱于全高满浓度的 nav；pow 曲线让顶部大区间保持接近满浓。
 */
private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float height;
half4 main(float2 coord) {
    half4 c = content.eval(coord);
    float t = clamp(1.0 - coord.y / height, 0.0, 1.0);
    return c * pow(t, 0.35);
}
"""

/** blur 半径增强系数：标题栏高度小于 nav 视觉厚度，1.0 时用户实测仍偏弱。 */
private const val HEADER_BLUR_BOOST = 1.5f

/**
 * nav（LiquidBottomTabs）同款容器色 tint：浅色 0xFFFAFAFA / 深色 0xFF121212。
 * 顶部 alpha = 0.4（与 nav onDrawSurface 的 drawRect(containerColor) 一致），
 * 底部沿 pow(1-y, 0.35) 曲线（与 AGSL alpha 调制同形状）衰减到 0。
 * API 31-32 无 AGSL 分支的 onDrawSurface 也用本渐变（形状近似）。
 */
private fun headerTintGradient(isLightTheme: Boolean): Brush {
    val base = if (isLightTheme) Color(0xFFFAFAFA) else Color(0xFF121212)
    // pow(0.5, 0.35) ≈ 0.78 → 中点仍保留约 0.31 的 tint，顶部满额 0.4
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0f to base.copy(alpha = 0.40f),
            0.35f to base.copy(alpha = 0.37f),
            0.68f to base.copy(alpha = 0.20f),
            1f to base.copy(alpha = 0f)
        )
    )
}

/** 性能降级态 / 兜底态的纯色 4 段渐变遮罩（原一级页标题栏配方）。 */
private fun headerScrimGradient(headerBg: Color): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to headerBg,
        0.35f to headerBg,
        0.68f to headerBg.copy(alpha = 0.85f),
        1f to headerBg.copy(alpha = 0f)
    )
)

/** 标题栏模糊是否生效：液态可用 + 性能开关关闭 + 设备支持 blur。 */
@Composable
fun isHeaderBlurActive(): Boolean =
    LocalIsLiquidGlassEnabled.current &&
        !LocalGlassEffectsReduced.current &&
        LocalLiquidGlassTokens.current.quality.supportsBlur

/**
 * 滚动内容挂载为标题栏模糊的采样源。
 * 模糊未生效时零开销直通（不建层）。
 */
@Composable
fun Modifier.headerBackdropSource(layer: LayerBackdrop): Modifier =
    if (isHeaderBlurActive()) layerBackdrop(layer) else this
