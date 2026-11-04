package com.ahu.ahutong.ui.glow

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全局边缘光效的开关与触发点（由 Main 最上层绘制，Home 的 AI 键只负责写入）。
 */
object AssistantGlowState {
    var visible by mutableStateOf(false)
    var origin by mutableStateOf<Offset?>(null)

    /** 光带是否已经扩散到屏幕底部；输入栏只在扩散完成后出现。 */
    var revealed by mutableStateOf(false)
}

private const val GLOW_AGSL = """
uniform float2 uResolution;
uniform float4 uRadii;
uniform float uPhase;
uniform float uFade;
uniform float2 uRevealCenter;
uniform float uReveal;
uniform float2 uOrbCenter;
uniform float uOrbRadius;
uniform float uOrbAlpha;
uniform float uScale;

float sdRoundRect(float2 p, float2 halfSize, float4 radii) {
    float r = (p.x < 0.0)
        ? ((p.y < 0.0) ? radii.x : radii.w)
        : ((p.y < 0.0) ? radii.y : radii.z);
    float2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

float3 palette(float t) {
    float3 p0 = float3(0.737, 0.510, 0.953);
    float3 p1 = float3(0.965, 0.725, 0.918);
    float3 p2 = float3(0.553, 0.624, 1.000);
    float3 p3 = float3(1.000, 0.404, 0.471);
    float3 p4 = float3(1.000, 0.729, 0.443);
    float u = fract(t) * 5.0;
    int i = int(floor(u));
    float f = fract(u);
    f = 0.5 - 0.5 * cos(f * 3.14159);
    float3 c = p0;
    if (i == 0) c = mix(p0, p1, f);
    else if (i == 1) c = mix(p1, p2, f);
    else if (i == 2) c = mix(p2, p3, f);
    else if (i == 3) c = mix(p3, p4, f);
    else c = mix(p4, p0, f);
    return c;
}

half4 main(float2 coord) {
    float2 center = uResolution * 0.5;
    float2 p = coord - center;
    float d = abs(sdRoundRect(p, center, uRadii));

    float line = smoothstep(1.4 * uScale, 0.0, d) * 0.90;
    float g1 = exp(-d / (6.0 * uScale)) * 0.75;
    float g2 = exp(-d / (17.0 * uScale)) * 0.42;
    float g3 = exp(-d / (34.0 * uScale)) * 0.20;
    float intensity = clamp(line + g1 + g2 + g3, 0.0, 1.0);

    float revealRadius = uReveal * length(uResolution) * 1.05;
    float mask = 1.0 - smoothstep(revealRadius - 90.0 * uScale, revealRadius, distance(coord, uRevealCenter));

    float angle = atan(p.y, p.x) / 6.28318 + 0.5;
    float3 borderColor = palette(angle + uPhase) * intensity * mask;

    float orbDistance = distance(coord, uOrbCenter);
    float orb = exp(-pow(orbDistance / max(uOrbRadius, 1.0), 2.0) * 1.6) * uOrbAlpha;
    float3 orbColor = palette(uPhase + 0.18) * orb;

    float alpha = clamp((intensity * mask + orb) * uFade, 0.0, 1.0);
    float3 color = (borderColor + orbColor) * uFade;
    return half4(color * alpha, alpha);
}
"""

/**
 * Apple Intelligence 风格的屏幕边缘光效（单 pass AGSL）。
 *
 * 边框、色场漂移、展开遮罩、上飘光团全部在同一个片元着色器里算完，
 * 不再叠加多层 RenderEffect，因而可以安全地画在所有界面（含底部导航）之上。
 */
@Composable
fun ScreenEdgeGlow(
    visible: Boolean,
    origin: Offset?,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val view = LocalView.current
    val density = LocalDensity.current
    var radii by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f, 0f)) }

    LaunchedEffect(view) {
        repeat(20) {
            val insets = view.rootWindowInsets
            if (insets != null) {
                val read = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    floatArrayOf(
                        (insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0).toFloat(),
                        (insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)?.radius ?: 0).toFloat(),
                        (insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0).toFloat(),
                        (insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0).toFloat(),
                    )
                } else {
                    floatArrayOf(0f, 0f, 0f, 0f)
                }
                if (read.any { it > 0f }) {
                    radii = read
                    return@LaunchedEffect
                }
            }
            delay(100)
        }
        val fallback = with(density) { 28.dp.toPx() }
        radii = floatArrayOf(fallback, fallback, fallback, fallback)
    }

    val screen = LocalConfiguration.current
    val revealToBottomThreshold = remember(screen.screenWidthDp, screen.screenHeightDp) {
        val w = screen.screenWidthDp.toFloat()
        val h = screen.screenHeightDp.toFloat()
        (h / (kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat() * 1.05f) + 0.01f)
            .coerceIn(0f, 1f)
    }

    val fade = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            AssistantGlowState.revealed = false
            reveal.snapTo(0f)
            lift.snapTo(0f)
            launch { fade.animateTo(1f, tween(160)) }
            launch { lift.animateTo(1f, tween(420, easing = CubicBezierEasing(0.32f, 0f, 0.12f, 1f))) }
            launch {
                delay(300)
                reveal.animateTo(1f, tween(950, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
            }
            launch {
                delay(300)
                snapshotFlow { reveal.value }
                    .first { it >= revealToBottomThreshold }
                AssistantGlowState.revealed = true
            }
        } else {
            AssistantGlowState.revealed = false
            fade.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
        }
    }

    val phase by rememberInfiniteTransition(label = "edge-glow")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7_000, easing = LinearEasing)
            ),
            label = "edge-glow-phase"
        )

    val fadeValue = fade.value
    if (fadeValue <= 0.001f) return

    val shader = remember { RuntimeShader(GLOW_AGSL) }
    val paint = remember { Paint().apply { isAntiAlias = true } }
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = density.density
            val orbStart = origin ?: Offset(size.width / 2f, size.height / 2f)
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform(
                "uRadii",
                radii[0].coerceAtLeast(1f),
                radii[1].coerceAtLeast(1f),
                radii[2].coerceAtLeast(1f),
                radii[3].coerceAtLeast(1f),
            )
            shader.setFloatUniform("uPhase", phase)
            shader.setFloatUniform("uFade", fadeValue)
            shader.setFloatUniform("uRevealCenter", orbStart.x, 0f)
            shader.setFloatUniform("uReveal", reveal.value)
            shader.setFloatUniform(
                "uOrbCenter",
                orbStart.x,
                orbStart.y * (1f - lift.value),
            )
            shader.setFloatUniform("uOrbRadius", 30.dp.value * scale * (1f - 0.45f * lift.value))
            shader.setFloatUniform("uOrbAlpha", (1f - reveal.value).coerceIn(0f, 1f) * 0.95f)
            shader.setFloatUniform("uScale", scale)
            drawIntoCanvas { canvas ->
                paint.shader = shader
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
            }
        }
    }
}
