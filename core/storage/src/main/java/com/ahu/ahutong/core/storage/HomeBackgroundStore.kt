package com.ahu.ahutong.core.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 全局自定义背景存储（沿用原主页背景的偏好与文件名）：
 * 选图 → 按屏幕比例 centerCrop → 清晰落盘；
 * 观感调节改用「亮暗遮罩不透明度」（白色/黑色罩层），保住图片清晰度。
 * revision 递增驱动所有页面重组。
 */
object HomeBackgroundStore {
    private const val SRC_FILE = "home_bg_src.jpg"
    private const val BLUR_FILE = "home_bg.jpg"

    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    private val prefs
        get() = AppEnvironmentHolder.context()
            .getSharedPreferences("home_background", Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = prefs.getString("path", null) != null

    /** 遮罩不透明度百分比 0-100（全虚到全实；亮色模式白罩 / 暗色模式黑罩）。 */
    val maskPercent: Int get() = prefs.getInt("mask", 50)

    fun blurredFile(context: Context): File = File(context.filesDir, BLUR_FILE)

    /** 选图落盘（crop 到屏幕比例）并生成当前模糊度的成品图。 */
    suspend fun importFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val raw = decodeBoundsSafe(context, uri) ?: return@withContext
        val metrics = context.resources.displayMetrics
        val cropped = centerCrop(raw, metrics.widthPixels, metrics.heightPixels)
        if (cropped != raw) raw.recycle()
        File(context.filesDir, SRC_FILE).outputStream().use {
            cropped.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        regenerateBlurred(context, cropped)
        prefs.edit().putString("path", BLUR_FILE).apply()
        _revision.value++
    }

    /** 调遮罩不透明度（0-60%）：仅改设置，图不动。 */
    fun updateMask(percent: Int) {
        prefs.edit().putInt("mask", percent.coerceIn(0, 100)).apply()
        _revision.value++
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        File(context.filesDir, SRC_FILE).delete()
        File(context.filesDir, BLUR_FILE).delete()
        prefs.edit().clear().apply()
        _revision.value++
    }

    /** 取主色调（缩样后量化统计，返回 #AARRGGBB）。 */
    fun dominantColorHex(context: Context): String? {
        val file = blurredFile(context)
        if (!file.exists()) return null
        val small = BitmapFactory.decodeFile(file.absolutePath)?.let {
            Bitmap.createScaledBitmap(it, 24, 24, true)
        } ?: return null
        // 量化到 4bit/通道分桶，取「饱和度尚可的最常见桶」
        val buckets = HashMap<Int, Int>()
        for (x in 0 until small.width) {
            for (y in 0 until small.height) {
                val c = small.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                if (maxC - minC < 24) continue // 滤掉近灰像素
                val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                buckets[key] = (buckets[key] ?: 0) + 1
            }
        }
        small.recycle()
        val best = buckets.maxByOrNull { it.value }?.key ?: return null
        val r = ((best shr 8) and 0xF) * 17
        val g = ((best shr 4) and 0xF) * 17
        val b = (best and 0xF) * 17
        return "#FF%02X%02X%02X".format(r, g, b)
    }

    private fun regenerateBlurred(context: Context, src: Bitmap) {
        blurredFile(context).outputStream().use {
            src.compress(Bitmap.CompressFormat.JPEG, 88, it)
        }
    }

    private fun decodeBoundsSafe(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        // 目标分辨率约 2 倍屏宽即可（模糊后细节不敏感）
        val target = context.resources.displayMetrics.widthPixels * 2
        val sample = max(1, bounds.outWidth / target)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** 按目标宽高比居中裁剪。 */
    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = targetW.toFloat() / targetH
        if (kotlin.math.abs(srcRatio - dstRatio) < 0.01f) return src
        return if (srcRatio > dstRatio) {
            // 原图更宽：裁左右
            val newW = (src.height * dstRatio).toInt()
            val x = (src.width - newW) / 2
            Bitmap.createBitmap(src, x, 0, newW, src.height)
        } else {
            val newH = (src.width / dstRatio).toInt()
            val y = (src.height - newH) / 2
            Bitmap.createBitmap(src, 0, y, src.width, newH)
        }
    }
}
