package com.ahu.ahutong.ui.screen.camera

import android.view.Surface
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 相机朝向 → 元素反向旋转角度。
 *
 * 窗口锁竖屏，用物理朝向决定"用户眼里的正上方"。量化到 90° 并带迟滞：
 * 只有越过两个象限之间的中线（±[hysteresisDegrees]）才切换，避免手一斜就来回跳。
 */
fun quantizeCameraRotation(
    sensorDegrees: Float,
    currentDegrees: Int,
    hysteresisDegrees: Float = 5f
): Int {
    if (!sensorDegrees.isFinite()) return currentDegrees
    val normalized = ((sensorDegrees % 360f) + 360f) % 360f
    val candidate = (normalized / 90f).roundToInt().mod(4) * 90
    if (candidate == currentDegrees) return currentDegrees
    // 相邻两个象限中心之间相差 ±90°，中线（边界）取 ±45°。
    val delta = ((candidate - currentDegrees) % 360 + 360) % 360
    val step = if (delta == 90) 45 else -45
    val boundary = ((currentDegrees + step) % 360 + 360) % 360
    val distance = angularDistance(normalized, boundary.toFloat())
    return if (distance >= hysteresisDegrees) candidate else currentDegrees
}

/** 0..180 的最短角度差。 */
fun angularDistance(a: Float, b: Float): Float {
    val diff = abs(a - b) % 360f
    return min(diff, 360f - diff)
}


/** 0/90/180/270 度 → CameraX 的 Surface 旋转常量。 */
fun Int.toCameraSurfaceRotation(): Int = when (((this % 360) + 360) % 360) {
    90 -> Surface.ROTATION_90
    180 -> Surface.ROTATION_180
    270 -> Surface.ROTATION_270
    else -> Surface.ROTATION_0
}
