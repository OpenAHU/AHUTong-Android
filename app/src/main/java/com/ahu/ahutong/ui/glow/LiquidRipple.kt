package com.ahu.ahutong.ui.glow

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * 透镜水波：整页内容作为纹理，被从「光点爆开处」向外扩散的宽水波推开。
 *
 * - 主波很宽（140dp → 460dp），带正弦细纹，观感是水，不是放大的玻璃环；
 * - 后面跟两圈回波，形成持续的水面起伏；
 * - [ambient] 为真时叠加一层微幅常驻波动，用于扩散结束后的“余波”。
 */
private const val LIQUID_RIPPLE_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uResolution;
uniform float uProgress;
uniform float uStrength;
uniform float uAmbientPhase;
uniform float uScale;

float waveBand(float d, float radius, float widthPx, float amplitude, float freq) {
    if (radius <= 0.0) return 0.0;
    float w = d - radius;
    float band = exp(-(w * w) / (widthPx * widthPx));
    return sin(w * freq) * band * amplitude;
}

half4 main(float2 coord) {
    float2 delta = coord - uCenter;
    float d = length(delta);
    float2 dir = d > 0.001 ? delta / d : float2(0.0);
    float maxD = length(uResolution);

    float widthPx = mix(140.0, 460.0, uProgress) * uScale;
    float mainRadius = uProgress * maxD * 1.06;
    float freq = 0.055 / uScale;

    float wave = waveBand(d, mainRadius, widthPx, uStrength * (1.0 - 0.5 * uProgress), freq);

    float settle = smoothstep(0.8, 1.0, uProgress);
    float ambient = sin(d / (110.0 * uScale) - uAmbientPhase * 6.28318) * 0.8 * uScale * settle;

    float2 offset = dir * (wave + ambient);
    float dispersion = 0.7 * uScale;
    half4 sampled = content.eval(coord + offset);
    half r = content.eval(coord + offset + dir * dispersion).r;
    half b = content.eval(coord + offset - dir * dispersion).b;
    return half4(r, sampled.g, b, sampled.a);
}
"""

/**
 * 给一整层内容挂上“被水波推了一下”的透镜效果。
 *
 * [progress] 0 → 1 表示水波从 [center] 扩散到全屏；[ambient] 为真时叠加常驻微幅波动。
 * Android 13 以下没有 RuntimeShader，直接不生效（当前用户均为 14+）。
 */
fun Modifier.liquidRipple(
    progress: Float,
    center: Offset?,
    strength: Dp,
    ambient: Boolean = false,
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        center == null ||
        (!ambient && progress >= 1f)
    ) {
        return@composed this
    }
    val strengthPx = with(LocalDensity.current) { strength.toPx() }
    val shader = remember { RuntimeShader(LIQUID_RIPPLE_AGSL) }
    val phase: State<Float> = if (ambient) {
        rememberInfiniteTransition(label = "liquid-ripple")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2_600, easing = LinearEasing)
                ),
                label = "liquid-ripple-phase"
            )
    } else {
        remember { mutableStateOf(0f) }
    }
    this.graphicsLayer {
        shader.setFloatUniform("uCenter", center.x, center.y)
        shader.setFloatUniform("uResolution", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uStrength", strengthPx)
        shader.setFloatUniform("uAmbientPhase", phase.value)
        shader.setFloatUniform("uScale", density)
        renderEffect = RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}
