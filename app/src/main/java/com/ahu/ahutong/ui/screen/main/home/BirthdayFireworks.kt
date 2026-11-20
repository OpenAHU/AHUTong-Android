package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 生日祝福背景：纯烟花循环（移植自参考 demo 的 2D 粒子实现）。
 * 深紫底 + 升空火箭 + 加色混合的爆炸火花，永不停歇。
 */
@Composable
internal fun BirthdayFireworks(modifier: Modifier = Modifier) {
    val state = remember { FireworksState() }
    val density = LocalDensity.current.density
    val tick = remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos == 0L) {
                    1f / 60f
                } else {
                    ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                }
                lastNanos = nanos
                state.step(dt, density)
                tick.intValue++
            }
        }
    }
    Canvas(modifier) {
        tick.intValue
        state.draw(this)
    }
}

private class Rocket(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val targetY: Float,
    val color: Color,
) {
    val trail = ArrayDeque<Offset>()
}

private class Spark(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val decay: Float,
    val size: Float,
    val color: Color,
)

private class FireworksState {
    private val random = Random(System.nanoTime())
    private val rockets = mutableListOf<Rocket>()
    private val sparks = mutableListOf<Spark>()
    private var width = 0f
    private var height = 0f
    private var launchTimer = 0f

    private fun rand(from: Float, to: Float) = from + random.nextFloat() * (to - from)

    fun step(dt: Float, density: Float) {
        if (width <= 0f || height <= 0f) return
        launchTimer -= dt
        if (launchTimer <= 0f) {
            launchRocket(density)
            if (random.nextFloat() < 0.25f) launchRocket(density)
            launchTimer = rand(0.25f, 0.9f)
        }

        for (index in rockets.indices.reversed()) {
            val rocket = rockets[index]
            rocket.trail.addLast(Offset(rocket.x, rocket.y))
            while (rocket.trail.size > 12) rocket.trail.removeFirst()
            rocket.x += rocket.vx * dt
            rocket.y += rocket.vy * dt
            rocket.vy += 220f * density * dt
            if (rocket.y <= rocket.targetY || rocket.vy >= 0f) {
                explode(rocket.x, rocket.y, rocket.color, density)
                rockets.removeAt(index)
            }
        }

        for (index in sparks.indices.reversed()) {
            val spark = sparks[index]
            spark.x += spark.vx * dt
            spark.y += spark.vy * dt
            spark.vy += 130f * density * dt
            val drag = 0.985f.pow(dt * 60f)
            spark.vx *= drag
            spark.vy *= drag
            spark.life -= spark.decay * dt
            if (spark.life <= 0f) sparks.removeAt(index)
        }
    }

    private fun launchRocket(density: Float) {
        rockets.add(
            Rocket(
                x = rand(width * 0.1f, width * 0.9f),
                y = height + 8f * density,
                vx = rand(-60f, 60f) * density,
                vy = rand(-height * 1.5f, -height * 1.1f),
                targetY = rand(height * 0.08f, height * 0.48f),
                color = PALETTE[random.nextInt(PALETTE.size)],
            )
        )
    }

    private fun explode(x: Float, y: Float, color: Color, density: Float) {
        val count = 70 + random.nextInt(40)
        repeat(count) {
            val angle = random.nextFloat() * (Math.PI.toFloat() * 2f)
            val speed = rand(60f, 340f) * density
            sparks.add(
                Spark(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    life = 1f,
                    decay = rand(0.45f, 0.85f),
                    size = rand(1.2f, 2.6f) * density,
                    color = color,
                )
            )
        }
        sparks.add(
            Spark(
                x = x,
                y = y,
                vx = 0f,
                vy = 0f,
                life = 1f,
                decay = 2.6f,
                size = 26f * density,
                color = Color.White,
            )
        )
    }

    fun draw(scope: DrawScope) {
        width = scope.size.width
        height = scope.size.height
        val radius = max(width, height) * 0.75f
        scope.drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF241633),
                    0.55f to Color(0xFF120B1F),
                    1f to Color(0xFF05030A),
                ),
                center = Offset(width * 0.5f, height * 0.45f),
                radius = radius,
            ),
            size = Size(width, height),
        )

        for (rocket in rockets) {
            for ((index, point) in rocket.trail.withIndex()) {
                val alpha = (index.toFloat() / rocket.trail.size) * 0.6f
                scope.drawCircle(
                    color = rocket.color.copy(alpha = alpha),
                    radius = 1.6f * scope.density,
                    center = point,
                )
            }
        }

        for (spark in sparks) {
            val alpha = spark.life.coerceIn(0f, 1f)
            val radius = spark.size * (0.4f + spark.life * 0.6f)
            scope.drawCircle(
                color = spark.color.copy(alpha = alpha),
                radius = radius,
                center = Offset(spark.x, spark.y),
                blendMode = BlendMode.Plus,
            )
        }
    }

    private companion object {
        val PALETTE = listOf(
            Color(0xFFFF5F6D),
            Color(0xFFFFC371),
            Color(0xFF4FACFE),
            Color(0xFF43E97B),
            Color(0xFFF857A6),
            Color(0xFFA18CD1),
            Color(0xFFFFD166),
            Color(0xFF00F5D4),
        )
    }
}
