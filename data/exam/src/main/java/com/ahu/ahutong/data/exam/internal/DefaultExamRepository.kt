package com.ahu.ahutong.data.exam.internal

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.exam.ExamCrawlerSource
import com.ahu.ahutong.data.exam.ExamLocalStore
import com.ahu.ahutong.data.exam.ExamRepository
import com.ahu.ahutong.data.model.Exam
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultExamRepository @Inject constructor(
    private val gateway: CampusNativeGateway,
    private val localStore: ExamLocalStore,
    private val crawlerSource: ExamCrawlerSource,
) : ExamRepository {

    override suspend fun getExamInfo(
        isRefresh: Boolean,
        studentId: String,
        studentName: String,
    ): AppResult<List<Exam>> = withContext(Dispatchers.IO) {
        if (!isRefresh && !localStore.isMockMode()) {
            val cached = localStore.getCachedExams()
            if (cached.isNotEmpty()) {
                Log.d(TAG, "getExamInfo: cache hit (${cached.size})")
                return@withContext AppResult.success(cached)
            }
        }

        val remote = fetchRemoteExams(studentId, studentName)
        if (remote is AppResult.Success) {
            localStore.saveExams(remote.data)
        }
        remote
    }

    private suspend fun fetchRemoteExams(
        studentId: String,
        studentName: String,
    ): AppResult<List<Exam>> {
        if (gateway.isLocalServiceReady()) {
            when (val http = gateway.httpGetExamInfo()) {
                is AppResult.Success -> return http
                is AppResult.Error -> Log.w(TAG, "http exam failed: ${http.message}")
            }
        }

        if (gateway.isNativeLoaded()) {
            when (val jni = gateway.getExamInfo()) {
                is AppResult.Success -> return jni
                is AppResult.Error -> Log.w(TAG, "jni exam failed: ${jni.message}")
            }
        }

        Log.d(TAG, "fallback to Android crawler exam")
        return crawlerSource.fetchExams(studentId, studentName)
    }

    private companion object {
        const val TAG = "ExamRepository"
    }
}
