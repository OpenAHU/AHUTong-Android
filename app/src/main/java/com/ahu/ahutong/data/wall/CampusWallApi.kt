package com.ahu.ahutong.data.wall

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.toAhuError
import javax.inject.Inject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 校园墙只读接口。客户端只访问 inchangejiang.cn/wall 这一层，
 * 不接触校园集市凭据，也不直连上游。
 */
interface CampusWallApi {

    @GET("status")
    suspend fun status(): Response<WallStatus>

    @GET("feed")
    suspend fun feed(
        @Query("limit") limit: Int = 20,
        @Query("before_ts") beforeTs: Long? = null,
        @Query("before_id") beforeId: Long? = null,
    ): Response<WallFeedResponse>

    @GET("hot")
    suspend fun hot(): Response<WallHotResponse>

    @GET("topic/{id}")
    suspend fun topic(@Path("id") id: Long): Response<WallTopic>

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): Response<WallSearchResponse>

    companion object {
        private const val BASE_URL = "https://inchangejiang.cn/wall/"

        val API: CampusWallApi by lazy {
            AhuHttp.retrofit(BASE_URL).create(CampusWallApi::class.java)
        }
    }
}

/** 把 HTTP 状态与异常收敛成 [AhuResult]，界面和 AI 能力只认这一种结果。 */
class CampusWallRepository @Inject constructor() {

    suspend fun status(): AhuResult<WallStatus> = call { CampusWallApi.API.status() }

    suspend fun feed(limit: Int = 20, beforeTs: Long? = null, beforeId: Long? = null): AhuResult<WallFeedResponse> =
        call { CampusWallApi.API.feed(limit, beforeTs, beforeId) }

    suspend fun hot(): AhuResult<WallHotResponse> = call { CampusWallApi.API.hot() }

    suspend fun topic(id: Long): AhuResult<WallTopic> = call { CampusWallApi.API.topic(id) }

    suspend fun search(query: String, limit: Int = 10): AhuResult<WallSearchResponse> =
        call { CampusWallApi.API.search(query, limit) }

    private suspend fun <T> call(block: suspend () -> Response<T>): AhuResult<T> = try {
        val response = block()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> AhuResult.Success(body)
            response.isSuccessful -> AhuResult.Failure(AhuError.ProtocolChanged("响应为空"))
            else -> AhuResult.Failure(
                AhuError.Server(response.code(), response.message().ifBlank { "请求失败" })
            )
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AhuResult.Failure(error.toAhuError())
    }
}
