package com.ahu.ahutong.ui.screen.scan

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.ResultPointCallback
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import com.google.zxing.qrcode.detector.FinderPattern
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
import kotlin.math.abs

/** A reader belongs to one analysis thread; photo decoding uses its own instance. */
internal class QrImageDecoder {
    private val multipleReader = QRCodeMultiReader()
    private var reducedPixels = ByteArray(0)
    private var reducedRow: ByteArray? = null
    private var enhancedPixels = ByteArray(0)
    private var tileMinimum = IntArray(0)
    private var tileMaximum = IntArray(0)

    /** Enumerate the full view, including mixed normal/inverted codes, before deciding to open one. */
    fun decodeCandidates(
        source: LuminanceSource,
        tryHarder: Boolean = true,
        onCandidate: ((center: QrPoint, size: Float) -> Unit)? = null
    ): List<QrDetection> {
        val hints = buildMap<DecodeHintType, Any> {
            // Respect ECI and ZXing's encoding detection for legacy Latin-1/Shift-JIS QR codes.
            // Forcing UTF-8 can return a successful decode whose actual payload is corrupted.
            // The default sparse row search skips 1–2 pixel modules in a distant QR.
            // Search original-resolution finder rows densely even on the camera's fast pass.
            put(DecodeHintType.TRY_HARDER, true)
        }
        val detections = mutableListOf<QrDetection>()
        val potential = mutableListOf<Pair<QrPoint, Float>>()
        fun readScale(input: LuminanceSource, scale: Int) {
            for (luminance in listOf(input, input.invert())) {
                val finders = if (onCandidate != null) mutableListOf<FinderPattern>() else null
                val passHints = if (finders == null) hints else hints +
                    (DecodeHintType.NEED_RESULT_POINT_CALLBACK to ResultPointCallback { point ->
                        if (point is FinderPattern && finders.size < 16) finders += point
                    })
                try {
                    multipleReader.decodeMultiple(BinaryBitmap(HybridBinarizer(luminance)), passHints)
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
                if (finders != null) finderCandidate(finders, source, scale)?.let { potential += it }
            }
        }
        // Large finder modules can be missed at full resolution, even with TRY_HARDER.
        // Recover them on every frame before searching the original for small/dense targets.
        val shortSide = minOf(source.width, source.height)
        var reduced: LuminanceSource? = null
        var reduction = 1
        if (shortSide >= 600) {
            val scale = maxOf(2, (shortSide + 479) / 480)
            reduction = scale
            val width = source.width / scale
            val height = source.height / scale
            if (reducedPixels.size != width * height) reducedPixels = ByteArray(width * height)
            for (y in 0 until height) {
                val currentRow = source.getRow(y * scale, reducedRow)
                reducedRow = currentRow
                for (x in 0 until width) reducedPixels[y * width + x] = currentRow[x * scale]
            }
            reduced = PlanarYUVLuminanceSource(reducedPixels, width, height, 0, 0, width, height, false)
            readScale(reduced, scale)
        }
        // Always enumerate both original polarities: success at reduced size says nothing
        // about a nearby small code, including one with the same payload or opposite polarity.
        readScale(source, 1)
        // A single local contrast recovery is enough for high-key, low-key and unevenly
        // lit symbols. Keep full-resolution recovery on the caller's thorough cadence;
        // a smaller failed camera view can be recovered without processing another full frame.
        val recovery = if (tryHarder) source else reduced ?: source
        if (tryHarder || detections.isEmpty()) {
            enhanceContrast(recovery)?.let { readScale(it, if (tryHarder) 1 else reduction) }
        }
        potential.filter { (center, _) -> detections.none { detected ->
            hypot(detected.center.x - center.x, detected.center.y - center.y) < 0.05f
        } }.minByOrNull { (center, _) -> hypot(center.x - 0.5f, center.y - 0.5f) }
            ?.let { (center, size) -> onCandidate?.invoke(center, size) }
        return detections.sortedWith(compareBy({ it.center.y }, { it.center.x }, { it.raw }))
    }

    /** ZXing has already cross-checked these finders horizontally and vertically. */
    private fun finderCandidate(
        points: List<FinderPattern>, source: LuminanceSource, scale: Int
    ): Pair<QrPoint, Float>? {
        for (a in points.indices) for (b in a + 1 until points.size) for (c in b + 1 until points.size) {
            val trio = listOf(points[a], points[b], points[c])
            val module = trio.map { it.estimatedModuleSize }.average().toFloat()
            if (module < 1f || trio.any { abs(it.estimatedModuleSize - module) > module * 0.25f }) continue
            for (pivot in trio.indices) {
                val origin = trio[pivot]
                val first = trio[(pivot + 1) % 3]
                val second = trio[(pivot + 2) % 3]
                val ux = first.x - origin.x; val uy = first.y - origin.y
                val vx = second.x - origin.x; val vy = second.y - origin.y
                val u = hypot(ux, uy); val v = hypot(vx, vy)
                if (minOf(u, v) < maxOf(u, v) * 0.75f ||
                    abs(ux * vx + uy * vy) > u * v * 0.18f ||
                    minOf(u, v) / module !in 13f..170f) continue
                val center = QrPoint((first.x + second.x) * 0.5f * scale / source.width,
                    (first.y + second.y) * 0.5f * scale / source.height)
                val size = (maxOf(u, v) + 7f * module) * scale / minOf(source.width, source.height)
                if (center.x in 0f..1f && center.y in 0f..1f && size in 0.015f..0.8f) return center to size
            }
        }
        return null
    }

    /** Expand local ranges before HybridBinarizer's fixed minimum-contrast threshold. */
    private fun enhanceContrast(source: LuminanceSource): LuminanceSource? {
        val width = source.width
        val height = source.height
        val columns = (width + 31) / 32
        val rows = (height + 31) / 32
        val count = columns * rows
        if (tileMinimum.size != count) {
            tileMinimum = IntArray(count)
            tileMaximum = IntArray(count)
        }
        tileMinimum.fill(255)
        tileMaximum.fill(0)
        val pixels = source.matrix
        for (y in 0 until height) for (x in 0 until width) {
            val tile = (y / 32) * columns + x / 32
            val value = pixels[y * width + x].toInt() and 255
            tileMinimum[tile] = minOf(tileMinimum[tile], value)
            tileMaximum[tile] = maxOf(tileMaximum[tile], value)
        }
        // Uniform blank frames and already well-exposed black/white symbols need no retry.
        if ((0 until count).none { tile ->
                val range = tileMaximum[tile] - tileMinimum[tile]
                range >= 8 && (range < 48 || tileMaximum[tile] < 80 || tileMinimum[tile] > 180)
            }) return null
        if (enhancedPixels.size != width * height) enhancedPixels = ByteArray(width * height)
        for (ty in 0 until rows) for (tx in 0 until columns) {
            var minimum = 255
            var maximum = 0
            for (ny in maxOf(0, ty - 1)..minOf(rows - 1, ty + 1)) {
                for (nx in maxOf(0, tx - 1)..minOf(columns - 1, tx + 1)) {
                    minimum = minOf(minimum, tileMinimum[ny * columns + nx])
                    maximum = maxOf(maximum, tileMaximum[ny * columns + nx])
                }
            }
            val range = maximum - minimum
            for (y in ty * 32 until minOf(height, (ty + 1) * 32)) {
                for (x in tx * 32 until minOf(width, (tx + 1) * 32)) {
                    val index = y * width + x
                    val value = pixels[index].toInt() and 255
                    enhancedPixels[index] = if (range >= 8) ((value - minimum) * 255 / range).toByte() else pixels[index]
                }
            }
        }
        return PlanarYUVLuminanceSource(enhancedPixels, width, height, 0, 0, width, height, false)
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
