package com.ahu.ahutong.ui.screen.scan

import kotlin.math.hypot

internal data class QrPoint(val x: Float, val y: Float)

/** PreviewView uses FIT_CENTER; detector points are relative to the rotated, cropped source. */
internal fun projectQrPoint(point: QrPoint, sourceAspectRatio: Float, width: Float, height: Float): QrPoint {
    val scale = minOf(width / sourceAspectRatio, height)
    return QrPoint((point.x - 0.5f) * sourceAspectRatio * scale + width / 2f,
        (point.y - 0.5f) * scale + height / 2f)
}

internal data class QrDetection(
    val raw: String,
    val corners: List<QrPoint> = emptyList(),
    val aspectRatio: Float = 1f
) {
    val center: QrPoint get() = if (corners.isEmpty()) QrPoint(0.5f, 0.5f) else
        QrPoint(corners.sumOf { it.x.toDouble() }.toFloat() / corners.size,
            corners.sumOf { it.y.toDouble() }.toFloat() / corners.size)

    fun centerDistance(other: QrDetection): Float = hypot(center.x - other.center.x, center.y - other.center.y)

    fun rotated(degrees: Int): QrDetection {
        val rotation = ((degrees % 360) + 360) % 360
        return copy(
            corners = corners.map { point ->
                when (rotation) {
                    90 -> QrPoint(1f - point.y, point.x)
                    180 -> QrPoint(1f - point.x, 1f - point.y)
                    270 -> QrPoint(point.y, 1f - point.x)
                    else -> point
                }
            },
            aspectRatio = if (rotation == 90 || rotation == 270) 1f / aspectRatio else aspectRatio
        )
    }
}
