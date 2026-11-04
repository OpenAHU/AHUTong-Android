package com.ahu.ahutong.ui.glow

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** 透镜涟漪：整页内容作为纹理，被一圈从触发点向外扩散的折射波推开。 */
private const val LIQUID_RIPPLE_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uResolution;
uniform float uProgress;
uniform float uStrength;

half4 main(float2 coord) {
    float2 delta = coord - uCenter;
    float d = length(delta);
    float2 dir = d > 0.001 ? delta / d : float2(0.0, 0.0);

    float maxD = length(uResolution);
    float radius = uProgress * maxD * 1.02;
    float bandWidth = mix(50.0, 170.0, uProgress);
    float wave = d - radius;
    float band = exp(-(wave * wave) / (bandWidth * bandWidth));

    float push = band * uStrength * (1.0 - uProgress);
    float ripple = sin(wave * 0.09) * push;
    float2 offset = dir * (ripple * 1.6 + push);

    half4 sampled = content.eval(coord + offset);
    // 轻微色散：让折射更像玻璃透镜，而不是整体平移。
    half r = content.eval(coord + offset + dir * 0.8).r;
    half g = sampled.g;
    half b = content.eval(coord + offset - dir * 0.8).b;
    half spec = half(band * 0.05 * (1.0 - uProgress));
    return half4(r + spec, g + spec, b + spec, sampled.a);
}
"""

/**
 * 给一整层内容挂上“点击后波动一下”的透镜涟漪。
 *
 * [progress] 0 → 1 表示波前从触发点扩散到全屏；结束前强度衰减到 0，画面回到原样。
 * Android 13 以下没有 RuntimeShader，直接不生效（当前用户均为 14+）。
 */
fun Modifier.liquidRipple(
    progress: Float,
    center: Offset?,
    strength: Dp,
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        center == null ||
        progress >= 1f
    ) {
        return@composed this
    }
    val strengthPx = with(LocalDensity.current) { strength.toPx() }
    val shader = remember { RuntimeShader(LIQUID_RIPPLE_AGSL) }
    this.graphicsLayer {
        shader.setFloatUniform("uCenter", center.x, center.y)
        shader.setFloatUniform("uResolution", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uStrength", strengthPx)
        renderEffect = RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}
