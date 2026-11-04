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

/**
 * 透镜水波：整页内容作为纹理，被一圈「波峰 + 波谷」推过去。
 *
 * 位移剖面用高斯导数 `x * exp(-x²)`：波前外侧外推、内侧回拉，
 * 每个波前只有一个峰和一个谷，不会出现正弦那种多周期的“旗子飘”观感。
 * 扫过之后振幅自然衰减到 0，不再叠加常驻波动。
 */
private const val LIQUID_RIPPLE_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uResolution;
uniform float uProgress;
uniform float uStrength;
uniform float uSettle;
uniform float uScale;

half4 main(float2 coord) {
    float2 delta = coord - uCenter;
    float d = length(delta);
    float2 dir = d > 0.001 ? delta / d : float2(0.0);
    float maxD = length(uResolution);

    float radius = uProgress * maxD * 1.02;
    float bandWidth = mix(120.0, 260.0, uProgress) * uScale;
    float x = (d - radius) / bandWidth;
    float profile = x * exp(-x * x) * 2.3;
    float wave = profile * uStrength * (1.0 - 0.55 * uProgress);

    // 主波过后的一次性回弹：一个周期内衰减到 0，用于“缓动”收尾。
    float settleWave = sin(uSettle * 6.28318) * exp(-uSettle * 2.4) * uStrength * 0.30;
    float settleField = settleWave * exp(-pow((d - maxD * 0.5) / (maxD * 0.35), 2.0));

    float2 offset = dir * (wave + settleField);
    float dispersion = 0.3 * uScale;
    half4 sampled = content.eval(coord + offset);
    half r = content.eval(coord + offset + dir * dispersion).r;
    half b = content.eval(coord + offset - dir * dispersion).b;
    return half4(r, sampled.g, b, sampled.a);
}
"""

/**
 * 给一整层内容挂上“被水波推了一下”的透镜效果。
 *
 * [progress] 0 → 1 表示这一个波前从 [center] 扫到全屏；扫完即结束。
 * Android 13 以下没有 RuntimeShader，直接不生效（当前用户均为 14+）。
 */
fun Modifier.liquidRipple(
    progress: Float,
    settle: Float,
    center: Offset?,
    strength: Dp,
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        center == null ||
        (progress >= 1f && settle >= 1f)
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
        shader.setFloatUniform("uSettle", settle)
        shader.setFloatUniform("uScale", density)
        renderEffect = RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}
