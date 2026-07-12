package com.ahu.ahutong.data.grade.internal

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.grade.GradeCrawlerSource
import com.ahu.ahutong.data.grade.GradeLocalStore
import com.ahu.ahutong.data.grade.GradeRepository
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultGradeRepository @Inject constructor(
    private val gateway: CampusNativeGateway,
    private val localStore: GradeLocalStore,
    private val crawlerSource: GradeCrawlerSource,
) : GradeRepository {

    override suspend fun getGrade(isRefresh: Boolean): AppResult<Grade> =
        withContext(Dispatchers.IO) {
            if (!isRefresh && !localStore.isMockMode()) {
                readLocalMergedGrade()?.let {
                    Log.d(TAG, "getGrade: cache hit")
                    return@withContext AppResult.success(it)
                }
            }

            val remote = fetchRemoteGrade()
            if (remote is AppResult.Success) {
                localStore.saveGrade(remote.data)
            }
            remote
        }

    override suspend fun getGpaRank(studentId: String): AppResult<GpaRankInfo> =
        withContext(Dispatchers.IO) {
            when (val remote = crawlerSource.fetchGpaRank(studentId)) {
                is AppResult.Success -> {
                    localStore.saveGpaRank(studentId, remote.data)
                    remote
                }
                is AppResult.Error -> {
                    localStore.getGpaRank(studentId)?.let {
                        Log.d(TAG, "getGpaRank: using cached rank after remote failure")
                        return@withContext AppResult.success(it)
                    }
                    remote
                }
            }
        }

    private fun readLocalMergedGrade(): Grade? {
        val perProfile = localStore.getPerProfileGrades()
        val profileGrades = perProfile.values.filterNotNull()
        if (profileGrades.isNotEmpty()) {
            val allTerms = profileGrades.flatMap { it.termGradeList ?: emptyList() }
            return Grade().apply {
                termGradeList = allTerms
                totalGradePointAverage =
                    allTerms.firstOrNull()?.termGradePointAverage ?: "0.0"
            }
        }
        return localStore.getCachedGrade()
    }

    private suspend fun fetchRemoteGrade(): AppResult<Grade> {
        if (gateway.isLocalServiceReady()) {
            when (val http = gateway.httpGetGrade()) {
                is AppResult.Success -> {
                    return runCatching { GradeResponseConverter.fromJson(http.data) }
                        .fold(
                            onSuccess = { AppResult.success(it) },
                            onFailure = {
                                Log.w(TAG, "http grade parse failed", it)
                                AppResult.error(it.message ?: "成绩解析失败", it)
                            },
                        )
                }
                is AppResult.Error -> Log.w(TAG, "http grade failed: ${http.message}")
            }
        }

        if (gateway.isNativeLoaded()) {
            when (val jni = gateway.getGradeRaw()) {
                is AppResult.Success -> {
                    return runCatching { GradeResponseConverter.fromJson(jni.data) }
                        .fold(
                            onSuccess = { AppResult.success(it) },
                            onFailure = {
                                Log.w(TAG, "jni grade parse failed", it)
                                AppResult.error(it.message ?: "成绩解析失败", it)
                            },
                        )
                }
                is AppResult.Error -> Log.w(TAG, "jni grade failed: ${jni.message}")
            }
        }

        Log.d(TAG, "fallback to Android crawler grade")
        return crawlerSource.fetchGrade()
    }

    private companion object {
        const val TAG = "GradeRepository"
    }
}
