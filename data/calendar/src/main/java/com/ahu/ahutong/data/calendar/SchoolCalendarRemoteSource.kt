package com.ahu.ahutong.data.calendar

import android.content.Context
import com.ahu.ahutong.core.common.AppResult
import java.io.File

/**
 * HTTP/crawler download fallback for school calendar. Bound in `:app`.
 */
interface SchoolCalendarRemoteSource {
    suspend fun download(
        context: Context,
        targetFile: File,
        onProgress: (Float) -> Unit = {},
    ): AppResult<File>
}
