package com.ahu.ahutong.ui.screen.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ahu.ahutong.ui.screen.scan.QrDetection
import com.ahu.ahutong.ui.screen.scan.QrImageDecoder
import com.google.zxing.RGBLuminanceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 相册导入的二维码：解码成候选列表 + 原图（用于展示与多码选择）。 */
internal suspend fun decodeQrFromUri(
    context: Context,
    uri: Uri
): Pair<List<QrDetection>, Bitmap>? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val found = try {
            QrImageDecoder().decodeCandidates(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
        } catch (_: Exception) {
            emptyList()
        }
        if (found.isEmpty()) {
            bitmap.recycle()
            null
        } else {
            found to bitmap
        }
    }.getOrNull()
}
