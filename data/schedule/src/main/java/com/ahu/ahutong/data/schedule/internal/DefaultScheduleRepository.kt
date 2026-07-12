package com.ahu.ahutong.data.schedule.internal

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleCrawlerSource
import com.ahu.ahutong.data.schedule.ScheduleLocalStore
import com.ahu.ahutong.data.schedule.ScheduleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultScheduleRepository @Inject constructor(
    private val gateway: CampusNativeGateway,
    private val localStore: ScheduleLocalStore,
    private val crawlerSource: ScheduleCrawlerSource,
) : ScheduleRepository {

    override suspend fun getSchedule(isRefresh: Boolean): AppResult<List<Course>> =
        withContext(Dispatchers.IO) {
            if (!isRefresh && !localStore.isMockMode()) {
                localStore.getSchoolTerm()?.let { term ->
                    localStore.getCachedSchedule(term)?.let {
                        Log.d(TAG, "getSchedule: cache hit")
                        return@withContext AppResult.success(it)
                    }
                }
            }

            val remote = fetchCurrentScheduleRemote()
            if (remote is AppResult.Success) {
                localStore.getSchoolTerm()?.let { term ->
                    localStore.saveSchedule(term, remote.data)
                }
            }
            remote
        }

    override suspend fun getNextSchedule(isRefresh: Boolean): AppResult<List<Course>> =
        withContext(Dispatchers.IO) {
            if (!isRefresh && !localStore.isMockMode()) {
                localStore.getNextSchedule()?.let {
                    Log.d(TAG, "getNextSchedule: cache hit")
                    return@withContext AppResult.success(it)
                }
            }

            // Next-term schedule currently lives only on the Android crawler path.
            val remote = crawlerSource.fetchNextSchedule()
            if (remote is AppResult.Success) {
                localStore.saveNextSchedule(remote.data)
            }
            remote
        }

    private suspend fun fetchCurrentScheduleRemote(): AppResult<List<Course>> {
        if (gateway.isLocalServiceReady()) {
            when (val http = gateway.httpGetSchedule()) {
                is AppResult.Success -> return http
                is AppResult.Error -> Log.w(TAG, "http schedule failed: ${http.message}")
            }
        }

        if (gateway.isNativeLoaded()) {
            when (val jni = gateway.getSchedule()) {
                is AppResult.Success -> return jni
                is AppResult.Error -> Log.w(TAG, "jni schedule failed: ${jni.message}")
            }
        }

        Log.d(TAG, "fallback to Android crawler schedule")
        return crawlerSource.fetchSchedule()
    }

    private companion object {
        const val TAG = "ScheduleRepository"
    }
}
