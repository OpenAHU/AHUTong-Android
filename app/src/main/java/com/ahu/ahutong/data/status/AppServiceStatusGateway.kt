package com.ahu.ahutong.data.status

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.network.StatusPageRemoteSource
import com.ahu.ahutong.data.toAhuError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

@Singleton
class AppServiceStatusGateway @Inject constructor(
    private val remoteSource: StatusPageRemoteSource
) : ServiceStatusGateway {

    override suspend fun fetchStatus(): AhuResult<ServiceStatusSnapshot> = try {
        AhuResult.Success(remoteSource.loadSnapshot())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        AhuResult.Failure(AhuError.Server(e.code(), "状态服务异常（HTTP ${e.code()}）"))
    } catch (e: Throwable) {
        AhuResult.Failure(e.toAhuError())
    }
}
