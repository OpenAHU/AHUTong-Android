package com.ahu.ahutong.data.network

import android.content.Context
import coil.ImageLoader

object AppImageLoaderFactory {
    @JvmStatic
    fun create(context: Context): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient { AhuHttp.plain().build() }
        .build()
}
