package com.ahu.ahutong.data.laundry

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 龙河机房与机器状态（趣智校园）。
 *
 * 平台没有批量接口，状态按**机房**整批下发，所以一次刷新只有 1 个请求；
 * 机房列表变动慢，缓存 5 分钟。所有请求经 [call] 串行并强制间隔 [MIN_INTERVAL_MS]，
 * 连续失败进冷却，避免高频访问被风控。
 *
 * 门店 ID 固定（安徽大学洗衣机 / 干衣机 / 洗鞋机各一个门店），不探测门店列表。
 */
@Singleton
class QiekjLaundrySource internal constructor(private val api: QiekjLaundryApi) {

    @Inject
    constructor() : this(QiekjLaundryApi.API)

    /** 一个机房（点位）：龙河的点位名就是楼栋名。 */
    data class Point(val orgId: String, val name: String)

    /** 一次机房查询结果；[clockOffsetMillis] = 服务器时间 - 本机时间。 */
    data class Snapshot(val items: List<QiekjMachine>, val clockOffsetMillis: Long)

    private class Stamped<T>(val value: T, val at: Long)

    private class Shop(val shopId: String, val typeId: String)

    private val gate = Mutex()
    private var lastCallAt = 0L
    private var consecutiveFailures = 0
    private var cooldownUntil = 0L

    private val pointsCache = mutableMapOf<LaundryKind, Stamped<List<Point>>>()

    /** 该机型在龙河的全部机房；洗衣机实测 20 个点位。 */
    suspend fun points(kind: LaundryKind, refresh: Boolean = false): List<Point> {
        val cached = pointsCache[kind]
        if (!refresh && cached != null && System.currentTimeMillis() - cached.at < POINTS_TTL_MS) {
            return cached.value
        }
        val shop = SHOPS[kind] ?: return emptyList()
        val list = call { api.points(shop.shopId, shop.typeId) }.requireData().orEmpty()
            .mapNotNull { point ->
                val orgId = point.orgId ?: return@mapNotNull null
                val name = point.orgName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Point(orgId = orgId, name = name)
            }
        pointsCache[kind] = Stamped(list, System.currentTimeMillis())
        return list
    }

    /**
     * 机房内全部机器的实时状态。
     *
     * [point] 为 null 时按门店全量查询（洗鞋机没有机房分组，只能整店拉），
     * 机器名自带楼栋，[QiekjLaundryMatcher] 再按楼栋筛。
     */
    suspend fun machines(kind: LaundryKind, point: Point?): Snapshot {
        val shop = SHOPS[kind] ?: throw IOException("未知机型")
        val envelope = call { api.machines(shop.shopId, shop.typeId, point?.orgId ?: ALL_POINTS) }
        val page = envelope.requireData()
        val serverNow = envelope.t ?: System.currentTimeMillis()
        return Snapshot(
            items = page?.items.orEmpty(),
            clockOffsetMillis = serverNow - System.currentTimeMillis()
        )
    }

    /**
     * 串行限速调用：同一时刻只有一个请求在路上，相邻请求至少隔 [MIN_INTERVAL_MS]；
     * 连续失败 [FAILURE_LIMIT] 次进入冷却，期间直接失败不再打平台。
     */
    private suspend fun <T> call(block: suspend () -> T): T = gate.withLock {
        val now = System.currentTimeMillis()
        if (now < cooldownUntil) throw IOException("龙河接口冷却中")
        val wait = MIN_INTERVAL_MS - (now - lastCallAt)
        if (wait > 0) delay(wait)
        try {
            val result = block()
            consecutiveFailures = 0
            result
        } catch (error: Exception) {
            consecutiveFailures += 1
            if (consecutiveFailures >= FAILURE_LIMIT) {
                cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
            }
            throw error
        } finally {
            lastCallAt = System.currentTimeMillis()
        }
    }

    companion object {

        /** 无机房分组的门店（洗鞋机）用该 orgId 拉整店机器。 */
        const val ALL_POINTS = "0"

        /**
         * 安徽大学三个门店的固定 ID 与机型编号
         * （machineTypeId 由 `/appointNew/newNearByList` 实测：00 洗衣 / 02 烘干 / 01 洗鞋）。
         */
        private val SHOPS = mapOf(
            LaundryKind.WASHER to Shop("202308241533480000011301057363", "00"),
            LaundryKind.DRYER to Shop("202309021831210000067493218753", "02"),
            LaundryKind.SHOE to Shop("202406080925310000015886164817", "01")
        )

        /** 相邻请求最小间隔：一次刷新 1 个请求，留足余量。 */
        private const val MIN_INTERVAL_MS = 1_200L
        private const val FAILURE_LIMIT = 3
        private const val COOLDOWN_MS = 90_000L
        private const val POINTS_TTL_MS = 5 * 60_000L
    }
}
