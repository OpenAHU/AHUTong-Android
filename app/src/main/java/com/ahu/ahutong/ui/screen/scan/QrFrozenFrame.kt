package com.ahu.ahutong.ui.screen.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy

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
