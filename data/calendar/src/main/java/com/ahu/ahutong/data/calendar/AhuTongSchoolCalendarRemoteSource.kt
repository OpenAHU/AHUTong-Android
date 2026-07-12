package com.ahu.ahutong.data.calendar

import android.content.Context
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.utils.FileUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AhuTongSchoolCalendarRemoteSource @Inject constructor() : SchoolCalendarRemoteSource {
    override suspend fun download(
        context: Context,
        targetFile: File,
        onProgress: (Float) -> Unit,
    ): AppResult<File> {
        return try {
            val response = AhuTong.API.downloadFile("xiaoli.jpg")
            if (!response.isSuccessful || response.body() == null) {
                return AppResult.error("下载校历失败", code = response.code())
            }
            val saved = FileUtils.saveResponseBodyToFile(
                context = context,
                body = response.body()!!,
                fileName = targetFile.name,
                onProgress = onProgress,
            )
            if (saved != null && saved.exists() && saved.length() > 0L) {
                AppResult.success(saved)
            } else {
                AppResult.error("保存校历失败")
            }
        } catch (t: Throwable) {
            AppResult.error(t.message ?: "下载校历失败", t)
        }
    }
}
