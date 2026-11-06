package com.ahu.ahutong.ui.screen.scan

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(TransformExperimental::class)
class QrFrozenPreviewTest {
    @Test
    fun frozenPixelsAndTargetsKeepDisplayOrientationWhenAnalysisFollowsDeviceRotation() {
        val crop = Rect(1, 1, 7, 5)
        val factory = ImageProxyTransformFactory().apply {
            isUsingCropRect = true
            isUsingRotationDegrees = true
        }
        val corners = listOf(QrPoint(0.1f, 0.2f), QrPoint(0.4f, 0.2f),
            QrPoint(0.4f, 0.6f), QrPoint(0.1f, 0.6f))
        val detection = QrDetection("synthetic-orientation-fixture", corners, crop.width().toFloat() / crop.height())
        for (displayRotation in listOf(0, 90, 180, 270)) {
            val original = fixtureBitmap()
            val expected = cropAndRotateQrFrame(original, crop, displayRotation)
            try {
                val target = factory.getOutputTransform(frame(crop, displayRotation))
                for (analysisRotation in listOf(0, 90, 180, 270)) {
                    val frozen = QrFrozenFrame(frame(crop, analysisRotation, fixtureBitmap()))
                    try {
                        val (mapped, displayed) = frozen.inOutput(target, listOf(detection.rotated(analysisRotation)),
                            expected.width, expected.height)
                        try {
                            assertEquals(expected.width, displayed.width)
                            assertEquals(expected.height, displayed.height)
                            for (y in 0 until expected.height) for (x in 0 until expected.width) {
                                assertEquals("pixel $x,$y, analysis=$analysisRotation display=$displayRotation",
                                    expected.getPixel(x, y), displayed.getPixel(x, y))
                            }
                            val desired = detection.rotated(displayRotation)
                            mapped.single().corners.zip(desired.corners).forEach { (actual, wanted) ->
                                assertEquals(wanted.x, actual.x, 0.0001f)
                                assertEquals(wanted.y, actual.y, 0.0001f)
                            }
                            assertEquals(desired.aspectRatio, mapped.single().aspectRatio, 0.0001f)
                        } finally { displayed.recycle() }
                    } finally { frozen.bitmap.recycle() }
                }
            } finally {
                expected.recycle()
                if (!original.isRecycled) original.recycle()
            }
        }
    }

    @Test
    fun frozenImageAndCandidatesShareTheSameLetterboxedViewport() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            val detection = QrDetection("synthetic-letterbox-fixture",
                listOf(QrPoint(0f, 0f), QrPoint(1f, 1f)), 2f)
            val transform = Matrix().apply { setTranslate(0f, 1f) }
            val (mapped, displayed) = transformQrFrozenFrame(source, listOf(detection), transform, 4, 4)
            try {
                assertEquals(Color.BLACK, displayed.getPixel(0, 0))
                assertEquals(Color.RED, displayed.getPixel(0, 1))
                assertEquals(Color.RED, displayed.getPixel(3, 2))
                assertEquals(Color.BLACK, displayed.getPixel(3, 3))
                assertEquals(listOf(QrPoint(0f, 0.25f), QrPoint(1f, 0.75f)), mapped.single().corners)
                assertEquals(1f, mapped.single().aspectRatio)
            } finally { displayed.recycle() }
        } finally { source.recycle() }
    }

    private fun fixtureBitmap(): Bitmap = Bitmap.createBitmap(IntArray(8 * 6) { pixel ->
        Color.rgb(20 + pixel * 4, 255 - pixel * 3, 40 + pixel * 2)
    }, 8, 6, Bitmap.Config.ARGB_8888)

    private fun frame(crop: Rect, rotation: Int, bitmap: Bitmap? = null): ImageProxy {
        val info = Proxy.newProxyInstance(ImageInfo::class.java.classLoader,
            arrayOf(ImageInfo::class.java)) { _, method, _ ->
            when (method.name) { "getRotationDegrees" -> rotation; else -> null }
        } as ImageInfo
        return Proxy.newProxyInstance(ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)) { _, method, _ ->
            when (method.name) {
                "getWidth" -> 8
                "getHeight" -> 6
                "getCropRect" -> crop
                "getImageInfo" -> info
                "toBitmap" -> bitmap
                else -> null
            }
        } as ImageProxy
    }
}
