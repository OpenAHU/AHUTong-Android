package com.ahu.ahutong.appwidget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SessionStore
import kotlinx.coroutines.CancellationException

/** 后台检查最新课表；成功后重绘缓存和获取时间，失败时保留原有内容。 */
class WidgetScheduleRefreshWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (!SessionStore.isLoggedIn() && !AHUCache.getMockData()) return Result.success()

        return try {
            when (AHURepository.refreshScheduleCache()) {
                is AhuResult.Success -> {
                    WidgetUpdateScheduler.renderCached(applicationContext)
                    Result.success()
                }
                is AhuResult.Failure -> Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("WidgetScheduleRefresh", "Unable to refresh schedule", error)
            Result.success()
        }
    }
}
