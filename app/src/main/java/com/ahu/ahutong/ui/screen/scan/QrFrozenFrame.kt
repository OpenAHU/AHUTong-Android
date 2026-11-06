package com.ahu.ahutong.ui.screen.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform

/** Captures image geometry before ImageProxy is closed; PreviewView geometry is read on the UI thread. */
@androidx.annotation.OptIn(TransformExperimental::class)
internal class QrFrozenFrame(image: ImageProxy) {
    private val source: OutputTransform = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }.getOutputTransform(image)
    val bitmap: Bitmap = freezeQrFrame(image)

    fun inPreview(preview: PreviewView, detections: List<QrDetection>): Pair<List<QrDetection>, Bitmap> {
        val target = checkNotNull(preview.outputTransform) { "Camera preview transform is not ready" }
        return inOutput(target, detections, preview.width, preview.height)
    }

    internal fun inOutput(
        target: OutputTransform,
        detections: List<QrDetection>,
        width: Int,
        height: Int
    ): Pair<List<QrDetection>, Bitmap> {
        val transform = Matrix()
        CoordinateTransform(source, target).transform(transform)
        return transformQrFrozenFrame(bitmap, detections, transform, width, height)
    }
}

/** Apply the exact displayed camera transform, including letterboxing and display-vs-analysis rotation. */
internal fun transformQrFrozenFrame(
    bitmap: Bitmap,
    detections: List<QrDetection>,
    transform: Matrix,
    width: Int,
    height: Int
): Pair<List<QrDetection>, Bitmap> {
    require(width > 0 && height > 0)
    val mapped = detections.map { detection ->
        val points = detection.corners.flatMap { listOf(it.x * bitmap.width, it.y * bitmap.height) }.toFloatArray()
        transform.mapPoints(points)
        detection.copy(
            corners = points.toList().chunked(2).map { QrPoint(it[0] / width, it[1] / height) },
            aspectRatio = width.toFloat() / height
        )
    }
    val displayed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        Canvas(displayed).apply {
            drawColor(Color.BLACK)
            drawBitmap(bitmap, transform, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return mapped to displayed
    } catch (error: Exception) {
        displayed.recycle()
        throw error
    }
}

/** Freeze the analyzed buffer itself, so motion and PreviewView's transform cannot move the targets. */
internal fun freezeQrFrame(image: ImageProxy): Bitmap {
    val buffer = image.toBitmap()
    try {
        val result = cropAndRotateQrFrame(buffer, image.cropRect, image.imageInfo.rotationDegrees)
        if (result !== buffer) buffer.recycle()
        return result
    } catch (error: Exception) {
        buffer.recycle()
        throw error
    }
}

internal fun cropAndRotateQrFrame(buffer: Bitmap, crop: Rect, rotation: Int): Bitmap {
    require(crop.left >= 0 && crop.top >= 0 && crop.right <= buffer.width && crop.bottom <= buffer.height)
    require(crop.width() > 0 && crop.height() > 0)
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(buffer, crop.left, crop.top, crop.width(), crop.height(), matrix, false)
}
