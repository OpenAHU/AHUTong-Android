package com.ahu.ahutong.ui.screen.scan

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.detector.FinderPattern
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/** A reader belongs to one analysis thread; photo decoding uses its own instance. */
internal class QrImageDecoder {
    private val multipleReader = QRCodeMultiReader()
    private var reducedPixels = ByteArray(0)
    private var reducedRow: ByteArray? = null

    /** Enumerate the full view, including mixed normal/inverted codes, before deciding to open one. */
    fun decodeCandidates(source: LuminanceSource, tryHarder: Boolean = true): List<QrDetection> {
        val hints = buildMap<DecodeHintType, Any> {
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
            if (tryHarder) put(DecodeHintType.TRY_HARDER, true)
        }
        val detections = mutableListOf<QrDetection>()
        fun readScale(input: LuminanceSource, scale: Int) {
            for (luminance in listOf(input, input.invert())) {
                try {
                    multipleReader.decodeMultiple(BinaryBitmap(HybridBinarizer(luminance)), hints)
                        .mapNotNull { detection(it, input) }.forEach { result ->
                            val candidate = if (scale == 1) result else result.copy(
                                corners = result.corners.map { point -> QrPoint(
                                    point.x * input.width * scale / source.width,
                                    point.y * input.height * scale / source.height) },
                                aspectRatio = source.width.toFloat() / source.height
                            )
                            val previous = detections.indexOfFirst {
                                it.raw == candidate.raw && it.centerDistance(candidate) < 0.02f
                            }
                            if (previous < 0) {
                                detections += candidate
                            } else if (scale == 1) {
                                // Prefer original-resolution coordinates when both views found this target.
                                detections[previous] = candidate
                            }
                        }
                } catch (_: ReaderException) {
                    // No complete QR in this polarity is an ordinary camera frame.
                } finally {
                    multipleReader.reset()
                }
            }
        }
        // Large finder modules can be missed at full resolution, even with TRY_HARDER.
        // Recover them on every frame before searching the original for small/dense targets.
        val shortSide = minOf(source.width, source.height)
        if (shortSide >= 600) {
            val scale = maxOf(2, (shortSide + 479) / 480)
            val width = source.width / scale
            val height = source.height / scale
            if (reducedPixels.size != width * height) reducedPixels = ByteArray(width * height)
            for (y in 0 until height) {
                val currentRow = source.getRow(y * scale, reducedRow)
                reducedRow = currentRow
                for (x in 0 until width) reducedPixels[y * width + x] = currentRow[x * scale]
            }
            readScale(PlanarYUVLuminanceSource(reducedPixels, width, height, 0, 0, width, height, false), scale)
        }
        // Always enumerate both original polarities: success at reduced size says nothing
        // about a nearby small code, including one with the same payload or opposite polarity.
        readScale(source, 1)
        return detections.sortedWith(compareBy({ it.center.y }, { it.center.x }, { it.raw }))
    }

    private fun detection(result: Result, source: LuminanceSource): QrDetection? {
        val raw = result.text?.takeIf { it.isNotBlank() } ?: return null
        val points = result.resultPoints.orEmpty()
        val corners = if (points.size >= 3) {
            val bottomLeft = points[0]
            val topLeft = points[1]
            val topRight = points[2]
            val ux = topRight.x - topLeft.x
            val uy = topRight.y - topLeft.y
            val vx = bottomLeft.x - topLeft.x
            val vy = bottomLeft.y - topLeft.y
            val uLength = hypot(ux, uy).coerceAtLeast(1f)
            val vLength = hypot(vx, vy).coerceAtLeast(1f)
            // ZXing returns finder centres, which lie 3.5 modules inside the symbol edge.
            val module = points.take(3).mapNotNull { (it as? FinderPattern)?.estimatedModuleSize }
                .takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f
            val dx = ux / uLength * module * 3.5f
            val dy = uy / uLength * module * 3.5f
            val ex = vx / vLength * module * 3.5f
            val ey = vy / vLength * module * 3.5f
            listOf(
                QrPoint(topLeft.x - dx - ex, topLeft.y - dy - ey),
                QrPoint(topRight.x + dx - ex, topRight.y + dy - ey),
                QrPoint(bottomLeft.x + topRight.x - topLeft.x + dx + ex,
                    bottomLeft.y + topRight.y - topLeft.y + dy + ey),
                QrPoint(bottomLeft.x - dx + ex, bottomLeft.y - dy + ey)
            ).map { point -> QrPoint(
                (point.x / source.width).coerceIn(0f, 1f),
                (point.y / source.height).coerceIn(0f, 1f)
            ) }
        } else emptyList()
        return QrDetection(raw, corners, source.width.toFloat() / source.height)
    }
}

/** Camera Y planes may have row padding, interleaved pixels and a nonzero buffer offset. */
internal fun copyQrLuminance(
    buffer: ByteBuffer,
    width: Int,
    height: Int,
    rowStride: Int,
    pixelStride: Int,
    destination: ByteArray? = null,
    left: Int = 0,
    top: Int = 0
): ByteArray {
    require(width > 0 && height > 0 && rowStride > 0 && pixelStride > 0 && left >= 0 && top >= 0)
    require((left + width.toLong() - 1) * pixelStride < rowStride)
    val offset = buffer.position().toLong() + top.toLong() * rowStride + left.toLong() * pixelStride
    require(offset <= Int.MAX_VALUE)
    val base = offset.toInt()
    val lastIndex = base.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride
    require(width.toLong() * height <= Int.MAX_VALUE && lastIndex < buffer.limit())
    return (destination?.takeIf { it.size == width * height } ?: ByteArray(width * height)).also { output ->
        if (pixelStride == 1) {
            val rows = buffer.duplicate()
            for (y in 0 until height) {
                rows.position(base + y * rowStride)
                rows.get(output, y * width, width)
            }
        } else {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    output[y * width + x] = buffer.get(base + y * rowStride + x * pixelStride)
                }
            }
        }
    }
}

/** Camera and gallery callbacks share one result slot until the user explicitly scans again. */
internal class ScanResultGate {
    private val accepted = AtomicBoolean(false)

    fun accept(raw: String): Boolean = raw.isNotBlank() && accepted.compareAndSet(false, true)

    fun reset() {
        accepted.set(false)
    }
}
