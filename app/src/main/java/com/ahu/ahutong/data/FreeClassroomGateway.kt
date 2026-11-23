package com.ahu.ahutong.data

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponse
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsRequest
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsResponse
import com.ahu.ahutong.data.network.AppStatusReporting
import com.ahu.ahutong.data.network.StatusEndpoint
import kotlinx.coroutines.CancellationException

/**
 * 空闲教室的取数网关。
 *
 * R2 要求界面不得直接调用协议 API（应经数据层网关）——这两个教务端点原先写在
 * FreeClassroomViewModel 里。协议客户端换实现、或将来把 jwxt 协议抽成模块时，
 * 改的是这里，而不是界面。
 */
object FreeClassroomGateway {

    /** 某校区的教学楼列表。 */
    suspend fun buildings(campusId: Int): GetBuildingsResponse = report("buildings") {
        JwxtApi.API.getBuildings(campusId = campusId)
    }

    /** 按楼栋与时间段查空闲教室。 */
    suspend fun freeRooms(request: GetFreeRoomsRequest): GetFreeRoomsResponse = report("freeRooms") {
        JwxtApi.API.getFreeRooms(request)
    }

    private suspend inline fun <T> report(action: String, block: () -> T): T {
        val startedAt = System.currentTimeMillis()
        return try {
            block().also {
                AppStatusReporting.reportCampusSuccess(
                    StatusEndpoint.AHU_JW,
                    action,
                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppStatusReporting.reportCampusThrowable(
                StatusEndpoint.AHU_JW,
                action,
                t,
                (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            )
            throw t
        }
    }
}

