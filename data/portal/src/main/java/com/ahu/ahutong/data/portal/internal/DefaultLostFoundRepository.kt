package com.ahu.ahutong.data.portal.internal

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.model.adwnh.AllCampus
import com.ahu.ahutong.data.crawler.model.adwnh.AllLostFoundType
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundPublishRequest
import com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse
import com.ahu.ahutong.data.portal.LostFoundLocalStore
import com.ahu.ahutong.data.portal.LostFoundRemoteSource
import com.ahu.ahutong.data.portal.LostFoundRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DefaultLostFoundRepository @Inject constructor(
    private val localStore: LostFoundLocalStore,
    private val remoteSource: LostFoundRemoteSource,
) : LostFoundRepository {

    override suspend fun getAllCampus(forceRefresh: Boolean): AppResult<AllCampus> =
        withContext(Dispatchers.IO) {
            when (val remote = remoteSource.fetchCampus()) {
                is AppResult.Success -> {
                    localStore.saveCampus(remote.data.`object`)
                    remote
                }
                is AppResult.Error -> {
                    val cached = localStore.getCachedCampus()
                    if (!forceRefresh && cached.isNotEmpty()) {
                        Log.d(TAG, "campus remote failed, using cache")
                        AppResult.success(AllCampus(code = 0, msg = "cache", `object` = cached))
                    } else {
                        remote
                    }
                }
            }
        }

    override suspend fun getAllTypes(forceRefresh: Boolean): AppResult<AllLostFoundType> =
        withContext(Dispatchers.IO) {
            when (val remote = remoteSource.fetchTypes()) {
                is AppResult.Success -> {
                    localStore.saveTypes(remote.data.`object`)
                    remote
                }
                is AppResult.Error -> {
                    val cached = localStore.getCachedTypes()
                    if (!forceRefresh && cached.isNotEmpty()) {
                        Log.d(TAG, "types remote failed, using cache")
                        AppResult.success(
                            AllLostFoundType(code = 0, msg = "cache", `object` = cached),
                        )
                    } else {
                        remote
                    }
                }
            }
        }

    override suspend fun getList(
        pageNo: Int,
        pageSize: Int,
        state: Int,
    ): AppResult<LostFoundResponse> = withContext(Dispatchers.IO) {
        when (val remote = remoteSource.fetchList(pageNo, pageSize, state)) {
            is AppResult.Success -> {
                val page = remote.data.data
                if (pageNo <= 1) {
                    localStore.clearList(state)
                    localStore.saveList(state, page.list)
                } else {
                    localStore.appendList(state, page.list)
                }
                remote
            }
            is AppResult.Error -> remote
        }
    }

    override suspend fun publish(request: LostFoundPublishRequest): AppResult<Any> =
        withContext(Dispatchers.IO) {
            remoteSource.publish(request)
        }

    override suspend fun delete(id: String): AppResult<Any> =
        withContext(Dispatchers.IO) {
            remoteSource.delete(id)
        }

    private companion object {
        const val TAG = "LostFoundRepository"
    }
}
