package com.ahu.ahutong.data.calendar

import android.content.Context
import com.ahu.ahutong.core.common.AppResult
import java.io.File

/**
 * School calendar image download / cache.
 */
interface SchoolCalendarRepository {
    /**
     * Returns a local JPEG file for the school calendar.
     * Uses cache unless [forceRefresh] is true.
     */
    suspend fun getCalendarImage(
        context: Context,
        forceRefresh: Boolean = false,
        onProgress: (Float) -> Unit = {},
    ): AppResult<File>
}
