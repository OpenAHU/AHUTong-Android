package com.ahu.ahutong.ui.screen.scan

import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic Android camera frames exercise the production analyzer without a physical QR target. */
@RunWith(AndroidJUnit4::class)
class QrAnalyzerFrameTest {
    @Test
    fun rescanIgnoresAndClosesFramesUntilOneSecondHasElapsed() {
        val matrix = QRCodeWriter().encode("8661234567", BarcodeFormat.QR_CODE, 256, 256)
        val pixels = ByteArray(256 * 256) { if (matrix[it % 256, it / 256]) 18 else 235.toByte() }
        val closed = AtomicInteger()
        var now = 0L
        val found = mutableListOf<String>()
        val analyzer = QrAnalyzer(
            onFailure = { throw AssertionError("Cooldown frames must not enter the decoder") }, nanoTime = { now },
            onFrame = { candidates, _ -> found += candidates.single().raw },
            recognizeAfterNanos = 1_000_000_000L)
        analyzer.analyze(frame(byteArrayOf(0), 16, 16, closed))
        now = 999_999_999L
        analyzer.analyze(frame(pixels, 256, 256, closed))
        assertEquals(emptyList<String>(), found)
        assertEquals(2, closed.get())
        now = 1_000_000_000L
        analyzer.analyze(frame(pixels, 256, 256, closed))
        analyzer.analyze(frame(pixels, 256, 256, closed))
        assertEquals(listOf("8661234567"), found)
        assertEquals(4, closed.get())
    }

    @Test
    fun repeatedSnapshotFaultsReleaseFramesAndProduceOneRecoverySignal() {
        val matrix = QRCodeWriter().encode("8661234567", BarcodeFormat.QR_CODE, 256, 256)
        val pixels = ByteArray(256 * 256) { if (matrix[it % 256, it / 256]) 18 else 235.toByte() }
        val closed = AtomicInteger()
        val failures = AtomicInteger()
        var now = 0L
        val analyzer = QrAnalyzer(
            onFailure = { failures.incrementAndGet() }, nanoTime = { now },
            onFrame = { _, _ -> throw IllegalStateException("fixture capture failure") })
        repeat(4) {
            now += 50_000_000L
            analyzer.analyze(frame(pixels, 256, 256, closed))
        }
        assertEquals(1, failures.get())
        assertEquals(4, closed.get())
    }

    @Test
    fun frozenTargetsUseTheSameAnalyzedCropAndRotationBeforeTheCameraFrameCloses() {
        val payload = "ahutong-aligned-frame-fixture"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 128, 128)
        val pixels = ByteArray(640 * 480) { 235.toByte() }
        for (y in 0 until 128) for (x in 0 until 128) {
            pixels[(156 + y) * 640 + 196 + x] = if (matrix[x, y]) 18 else 235.toByte()
        }
        for (rotation in listOf(0, 90, 180, 270)) {
            val bitmap = Bitmap.createBitmap(IntArray(pixels.size) {
                if ((pixels[it].toInt() and 255) < 128) Color.BLACK else Color.WHITE
            }, 640, 480, Bitmap.Config.ARGB_8888)
            val closed = AtomicInteger()
            var delivered = 0
            val analyzer = QrAnalyzer(onFailure = { throw AssertionError("Aligned fixture failed") },
                onFrame = { found, image ->
                    assertEquals(0, closed.get())
                    val frozen = freezeQrFrame(image)
                    try {
                        val result = found.single()
                        assertEquals(payload, result.raw)
                        assertEquals(frozen.width.toFloat() / frozen.height, result.aspectRatio)
                        var left = frozen.width
                        var top = frozen.height
                        var right = 0
                        var bottom = 0
                        for (y in 0 until frozen.height) for (x in 0 until frozen.width) {
                            if (Color.red(frozen.getPixel(x, y)) < 128) {
                                left = minOf(left, x); top = minOf(top, y)
                                right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                            }
                        }
                        assertEquals(left.toFloat(), result.corners.minOf { it.x } * frozen.width, 2f)
                        assertEquals(top.toFloat(), result.corners.minOf { it.y } * frozen.height, 2f)
                        assertEquals(right.toFloat(), result.corners.maxOf { it.x } * frozen.width, 2f)
                        assertEquals(bottom.toFloat(), result.corners.maxOf { it.y } * frozen.height, 2f)
                        delivered++
                    } finally { frozen.recycle() }
                })
            try {
                analyzer.analyze(frame(pixels, 640, 480, closed, Rect(160, 80, 480, 400), rotation, bitmap))
                analyzer.analyze(frame(pixels, 640, 480, closed))
                assertEquals(1, delivered)
                assertEquals(2, closed.get())
            } finally { if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    private fun frame(bytes: ByteArray, width: Int, height: Int, closed: AtomicInteger,
        crop: Rect = Rect(0, 0, width, height), rotation: Int = 0, bitmap: Bitmap? = null): ImageProxy {
        val plane = Proxy.newProxyInstance(
            ImageProxy.PlaneProxy::class.java.classLoader,
            arrayOf(ImageProxy.PlaneProxy::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getBuffer" -> ByteBuffer.wrap(bytes)
                "getRowStride" -> width
                "getPixelStride" -> 1
                else -> null
            }
        } as ImageProxy.PlaneProxy
        val info = Proxy.newProxyInstance(ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java)) { _, method, _ ->
                when (method.name) { "getRotationDegrees" -> rotation; else -> null }
            } as ImageInfo
        return Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getWidth" -> width
                "getHeight" -> height
                "getCropRect" -> crop
                "getPlanes" -> arrayOf(plane)
                "getImageInfo" -> info
                "toBitmap" -> bitmap
                "close" -> { closed.incrementAndGet(); null }
                else -> null
            }
        } as ImageProxy
    }
}
