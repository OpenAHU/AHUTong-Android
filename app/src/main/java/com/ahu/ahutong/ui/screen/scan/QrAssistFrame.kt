package com.ahu.ahutong.ui.screen.scan

/** Only geometry and aggregate brightness leave the analysis thread. */
internal data class QrAssistFrame(
    val timestampNanos: Long,
    val aspectRatio: Float,
    val target: QrPotentialTarget?,
    val lighting: QrLuminanceStats
)

/** Sample the central half of the cropped Y plane with a bounded amount of work. */
internal fun sampleQrLighting(data: ByteArray, width: Int, height: Int): QrLuminanceStats {
    require(width > 0 && height > 0 && width.toLong() * height <= data.size)
    val left = width / 4
    val top = height / 4
    val right = maxOf(left + 1, width - left)
    val bottom = maxOf(top + 1, height - top)
    val stepX = maxOf(1, (right - left + 63) / 64)
    val stepY = maxOf(1, (bottom - top + 63) / 64)
    var sum = 0L
    var count = 0
    var dark = 0
    var bright = 0
    for (y in top until bottom step stepY) for (x in left until right step stepX) {
        val value = data[y * width + x].toInt() and 255
        sum += value
        count++
        if (value <= 24) dark++
        if (value >= 245) bright++
    }
    return QrLuminanceStats(sum.toFloat() / count, dark.toFloat() / count, bright.toFloat() / count)
}
