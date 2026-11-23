package com.ahu.ahutong.data.laundry

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** token 失效（服务器 401）。上层应把界面切成「去看更新 token」，而不是提示网络错误。 */
class LaundryAuthException(message: String) : Exception(message)

/**
 * 占用状态查询。
 *
 * 逐台调 `get-order-list-home-page`：平台没有批量接口，所以只查**当前园区当前机型**
 * （最多 40 台，12 并发约 3 秒），切园区或下拉才重新拉。
 * 单台失败降级成 [LaundryOccupancy.Unknown]，只有 401 会中止整轮（token 没了，查下去也没意义）。
 */
@Singleton
class LaundryRepository internal constructor(private val api: LaundryApi) {

    @Inject
    constructor() : this(LaundryApi.API)

    suspend fun occupancy(
        token: String,
        devices: List<LaundryDevice>,
        concurrency: Int = DEFAULT_CONCURRENCY
    ): Map<String, LaundryOccupancy> = coroutineScope {
        val permits = Semaphore(concurrency.coerceAtLeast(1))
        devices.map { device ->
            async { device.id to permits.withPermit { queryOne(token, device) } }
        }.awaitAll().toMap()
    }

    /**
     * 校验刚粘贴的 token 是否还能用：任取一台设备打一枪，401 抛 [LaundryAuthException]。
     * 返回 false 表示 token 本身格式没问题但平台不认这台设备（例如号已下线），
     * 不代表 token 无效，所以只有 401 才当失败。
     */
    suspend fun probe(token: String, deviceId: String): Boolean = coroutineScope {
        val device = LaundryDevice(
            id = deviceId,
            parkIndex = 0,
            kind = LaundryKind.WASHER,
            location = null,
            floor = null,
            slot = null,
            remark = null,
            online = true
        )
        queryOne(token, device)
        true
    }

    private suspend fun queryOne(token: String, device: LaundryDevice): LaundryOccupancy {
        // 台账 online 是扫描时的死快照（实测过期得很快），不能当「离线」证据直接跳过——
        // 梅园那台 online=false 接口照样 200 idle。占不占用只有订单接口说了算。
        return try {
            val response = api.currentOrder(token = token, equipmentValue = device.id)
            if (response.code() == UNAUTHORIZED) throw LaundryAuthException("token 已过期")
            val envelope = response.body() ?: return LaundryOccupancy.Unknown("无响应")
            if (!envelope.isSuccess) {
                return LaundryOccupancy.Unknown(envelope.message?.takeIf { it.isNotBlank() } ?: "查询失败")
            }
            val order = envelope.body ?: return LaundryOccupancy.Idle
            toOccupancy(order)
        } catch (auth: LaundryAuthException) {
            throw auth
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            LaundryOccupancy.Unknown("网络异常")
        }
    }

    private fun toOccupancy(order: LaundryOrder): LaundryOccupancy {
        val nowTime = order.nowTime
        val endAt = order.endTime
            ?: order.surplusTime?.let { (nowTime ?: System.currentTimeMillis()) + it * 1000L }
        // body 存在但既没有 endTime 也没有 surplusTime，说明是空订单对象（空闲）
            ?: return LaundryOccupancy.Idle
        return LaundryOccupancy.InUse(
            endAtMillis = endAt,
            clockOffsetMillis = nowTime?.let { it - System.currentTimeMillis() } ?: 0L,
            serviceName = order.serviceName,
            price = order.price
        )
    }

    private companion object {
        /** 平台实测 20 req/s 稳定，这里留余量；一次刷新最多 40 台。 */
        const val DEFAULT_CONCURRENCY = 12
        const val UNAUTHORIZED = 401
    }
}
