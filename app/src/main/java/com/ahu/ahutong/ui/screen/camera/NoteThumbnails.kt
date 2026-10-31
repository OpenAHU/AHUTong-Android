package com.ahu.ahutong.ui.screen.camera

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 笔记缩略图：采样解码 + LruCache，明显内存优先于分辨率。 */
internal object NoteThumbnails {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {}

    private const val MAX_EDGE = 256

    suspend fun load(path: String, maxEdge: Int = MAX_EDGE): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        val key = "$path|${file.lastModified()}|$maxEdge"
        cache.get(key)?.let { return@withContext it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext null
        bitmap.asImageBitmap().also { cache.put(key, it) }
    }
}

@Composable
internal fun NoteThumbnail(path: String?, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, path) {
        value = path?.let { NoteThumbnails.load(it) }
    }
    Box(modifier.background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Outlined.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
        }
    }
}
