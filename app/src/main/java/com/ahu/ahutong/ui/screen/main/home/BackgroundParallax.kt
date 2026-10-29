package com.ahu.ahutong.ui.screen.main.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlinx.coroutines.awaitCancellation

/**
 * 背景视差（设备动作 → 静态壁纸轻微位移）。
 *
 * 位移上限来自绘制时的超采样倍数 [BackgroundParallaxOverScan]：图片先放大再平移，
 * 结构上留出 (overScan-1)/2 的余量，所以只要输入被压进 (-1,1)，就不可能出现黑边。
 */
internal const val BackgroundParallaxOverScan = 1.12f

/** 软区边界：|t| 以内线性跟随，越过之后强阻尼，越靠近 1 越“推不动”。 */
internal const val BackgroundParallaxSoftEdge = 0.7f

/** 达到满幅位移所需的相对倾角（度）。日常握持的轻微晃动只应产生很小的位移。 */
internal const val BackgroundParallaxTiltRangeDeg = 26f

/** 一阶低通系数：越小越稳（越迟滞），避免手抖式抖动。 */
private const val TILT_SMOOTHING = 0.04f

/**
 * 把任意输入压进 (-1,1)：软区线性、贴墙段用 tanh 强阻尼。
 *
 * 返回值的绝对值恒小于 1，因此乘以余量后永远落在图片范围内——这是“不允许黑边”的数学保证，
 * 而不是靠调用方记得 clamp。
 */
internal fun dampedParallax(value: Float): Float {
    if (value.isNaN()) return 0f
    val magnitude = abs(value)
    val mapped = if (magnitude <= BackgroundParallaxSoftEdge) {
        magnitude
    } else {
        val span = 1f - BackgroundParallaxSoftEdge
        BackgroundParallaxSoftEdge + span * tanh((magnitude - BackgroundParallaxSoftEdge) / span)
    }
    return sign(value) * mapped.coerceIn(0f, 0.999f)
}

/** 相对基线的倾角（度）→ 归一化视差；设备往右倾，背景往左移（负视差跟随）。 */
internal fun tiltParallax(
    rollDegrees: Float,
    pitchDegrees: Float,
    baseRoll: Float,
    basePitch: Float
): Offset {
    val x = -(rollDegrees - baseRoll) / BackgroundParallaxTiltRangeDeg
    val y = -(pitchDegrees - basePitch) / BackgroundParallaxTiltRangeDeg
    return Offset(dampedParallax(x), dampedParallax(y))
}

/**
 * 撞墙即重设零点：倾角越过满幅行程后，把基线实时挪到墙的位置。
 *
 * 画面不会跳（仍停在原来那一侧），但"零点"已经换到边界上——用户换个姿势或反向倾斜时，
 * 立刻就从墙边往回走，不会像死状态一样在边界徘徊。边界本身的物理位置不受影响。
 */
internal fun rebaseCenter(
    angleDegrees: Float,
    center: Float,
    rangeDegrees: Float = BackgroundParallaxTiltRangeDeg
): Float {
    val delta = angleDegrees - center
    return when {
        delta > rangeDegrees -> angleDegrees - rangeDegrees
        delta < -rangeDegrees -> angleDegrees + rangeDegrees
        else -> center
    }
}

/** 由重力分量求 roll / pitch（度）。 */
internal fun gravityAngles(x: Float, y: Float, z: Float): Pair<Float, Float> {
    val roll = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
    val pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
    return roll to pitch
}

/** 系统「移除动画 / 动画时长 0」时不做视差，这是无障碍偏好。 */
private fun animationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}.getOrDefault(false)

/**
 * 采集设备姿态并产出归一化视差（-1..1，两轴）。
 *
 * 只在 RESUMED 期间注册传感器；基线取进入前台后的第一帧，因此无论是平放还是斜握，
 * 静止时都停在中心。关闭开关 / 系统关动画 / 没有加速度计时恒为 [Offset.Zero]。
 */
@Composable
fun rememberBackgroundParallax(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val offset = remember { mutableStateOf(Offset.Zero) }
    val active = enabled && !animationsDisabled(context)
    LaunchedEffect(active, lifecycleOwner) {
        if (!active) {
            offset.value = Offset.Zero
            return@LaunchedEffect
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return@LaunchedEffect
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var baseRoll = 0f
            var basePitch = 0f
            var smoothRoll = 0f
            var smoothPitch = 0f
            var hasBase = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                    val (roll, pitch) = gravityAngles(event.values[0], event.values[1], event.values[2])
                    if (!hasBase) {
                        baseRoll = roll
                        basePitch = pitch
                        smoothRoll = roll
                        smoothPitch = pitch
                        hasBase = true
                        return
                    }
                    smoothRoll += (roll - smoothRoll) * TILT_SMOOTHING
                    smoothPitch += (pitch - smoothPitch) * TILT_SMOOTHING
                    // 过渡不衰减：一旦越过墙就把零点挪到墙上，换姿势后不会卡在边界
                    baseRoll = rebaseCenter(smoothRoll, baseRoll)
                    basePitch = rebaseCenter(smoothPitch, basePitch)
                    offset.value = tiltParallax(smoothRoll, smoothPitch, baseRoll, basePitch)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            try {
                awaitCancellation()
            } finally {
                manager.unregisterListener(listener)
                offset.value = Offset.Zero
            }
        }
    }
    return offset
}