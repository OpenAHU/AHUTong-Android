package com.ahu.ahutong.data.calendar.internal

import android.content.Context
import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.calendar.SchoolCalendarRemoteSource
import com.ahu.ahutong.data.calendar.SchoolCalendarRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultSchoolCalendarRepository @Inject constructor(
    private val gateway: CampusNativeGateway,
    private val remoteSource: SchoolCalendarRemoteSource,
) : SchoolCalendarRepository {

    override suspend fun getCalendarImage(
        context: Context,
        forceRefresh: Boolean,
        onProgress: (Float) -> Unit,
    ): AppResult<File> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "images")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, "xiaoli.jpg")

        if (!forceRefresh && target.exists() && target.length() > 0L) {
            Log.d(TAG, "cache hit: ${target.absolutePath}")
            onProgress(1f)
            return@withContext AppResult.success(target)
        }

        // Native path first
        if (gateway.isNativeLoaded()) {
            val temp = File(dir, "xiaoli-rust-download.jpg")
            if (temp.exists()) temp.delete()
            val ok = runCatching { gateway.downloadSchoolCalendar(temp.absolutePath) }
                .getOrDefault(false)
            if (ok && temp.exists() && temp.length() > 0L) {
                if (target.exists()) target.delete()
                if (temp.renameTo(target) || temp.copyTo(target, overwrite = true).exists()) {
                    if (temp.exists() && temp.absolutePath != target.absolutePath) {
                        temp.delete()
                    }
                    onProgress(1f)
                    return@withContext AppResult.success(target)
                }
            }
            Log.w(TAG, "native calendar download failed, fallback to HTTP")
        }

        remoteSource.download(context, target, onProgress)
    }

    private companion object {
        const val TAG = "SchoolCalendarRepository"
    }
}
