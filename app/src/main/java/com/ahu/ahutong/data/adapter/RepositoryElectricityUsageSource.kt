package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityUsageHistoryPage
import com.ahu.ahutong.data.crawler.model.ycard.ElectricityUsageProtocol
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityDailyUsage
import com.ahu.ahutong.data.recharge.ElectricityUsageSnapshot
import com.ahu.ahutong.data.recharge.ElectricityUsageSource
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.ResponseBody
import retrofit2.Response

@Singleton
class RepositoryElectricityUsageSource @Inject constructor() : ElectricityUsageSource {
    override suspend fun snapshot(
        controller: ElectricityController,
        selection: RoomSelectionInfo,
        asOf: LocalDate
    ): AhuResult<ElectricityUsageSnapshot> = withContext(Dispatchers.IO) {
        val building = selection.building?.value?.takeIf { it.isNotBlank() }
            ?: return@withContext protocolFailure("未选择楼栋")
        val floor = selection.floor?.value?.takeIf { it.isNotBlank() }
            ?: return@withContext protocolFailure("未选择楼层")
        val room = selection.room?.value?.takeIf { it.isNotBlank() }
            ?: return@withContext protocolFailure("未选择房间")
        val campus = selection.campus?.value?.takeIf { it.isNotBlank() }
        if (controller.requiresCampus && campus == null) {
            return@withContext protocolFailure("未选择校区")
        }
        try {
            val form = FormBody.Builder()
                .add("feeitemid", controller.feeItemId)
                .add("type", "IEC")
                .add("level", controller.roomInfoLevel)
                .add("building", building)
                .add("floor", floor)
                .add("room", room)
                .apply { campus?.let { add("campus", it) } }
                .build()
            val balanceBody = when (val result = request { getFeeItemThirdData(form) }) {
                is AhuResult.Failure -> return@withContext result
                is AhuResult.Success -> result.value
            }
            val balance = when (val result = ElectricityUsageProtocol.balance(balanceBody)) {
                is AhuResult.Failure -> return@withContext result
                is AhuResult.Success -> result.value
            }
            val periodStart = asOf.minusDays(30)
            val periodEnd = asOf.minusDays(1)
            val history = collectElectricityUsagePages { page ->
                val body = when (val result = request {
                    getElectricityUsageHistory(
                        feeItemId = controller.feeItemId,
                        building = building,
                        floor = floor,
                        room = room,
                        campus = campus,
                        startDate = periodStart.toString(),
                        // Include the boundary in the wire query, then discard today's
                        // partial readings for a consistent 30 complete day forecast.
                        endDate = asOf.toString(),
                        page = page,
                        rows = PAGE_SIZE
                    )
                }) {
                    is AhuResult.Failure -> return@collectElectricityUsagePages result
                    is AhuResult.Success -> result.value
                }
                ElectricityUsageProtocol.history(body)
            }
            when (history) {
                is AhuResult.Failure -> history
                is AhuResult.Success -> AhuResult.Success(
                    ElectricityUsageSnapshot(
                        asOf,
                        balance,
                        history.value.filter {
                            !it.date.isBefore(periodStart) && !it.date.isAfter(periodEnd)
                        },
                        periodStart,
                        periodEnd
                    )
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SocketTimeoutException) {
            AhuResult.Failure(AhuError.Timeout)
        } catch (_: IOException) {
            AhuResult.Failure(AhuError.Network)
        } catch (_: Exception) {
            AhuResult.Failure(AhuError.Unknown("电费查询失败"))
        }
    }

    private suspend fun request(
        call: suspend YcardApi.() -> Response<ResponseBody>
    ): AhuResult<String> {
        val response = YcardApi.authorizedCall(request = call)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return if (response.code() == 401 || response.code() == 403) {
                AhuResult.Failure(AhuError.Unauthorized("校园卡登录已失效"))
            } else {
                AhuResult.Failure(AhuError.Server(response.code(), "电费查询失败"))
            }
        }
        val body = response.body()?.use { it.string() }
            ?: return protocolFailure("电费查询响应为空")
        return AhuResult.Success(body)
    }

    private fun protocolFailure(detail: String) = AhuResult.Failure(AhuError.ProtocolChanged(detail))

    private companion object {
        const val PAGE_SIZE = 100
    }
}

/** Collect only complete history; a failed or looping page must not produce a low estimate. */
internal suspend fun collectElectricityUsagePages(
    fetch: suspend (Int) -> AhuResult<ElectricityUsageHistoryPage>
): AhuResult<List<ElectricityDailyUsage>> {
    val records = mutableListOf<ElectricityDailyUsage>()
    var expectedTotal: Int? = null
    var previousPage: List<ElectricityDailyUsage>? = null
    for (page in 1..20) {
        val history = when (val result = fetch(page)) {
            is AhuResult.Failure -> return result
            is AhuResult.Success -> result.value
        }
        if (page > 1 && (history.total != expectedTotal || history.records == previousPage)) {
            return AhuResult.Failure(AhuError.ProtocolChanged("用电记录分页不完整"))
        }
        expectedTotal = history.total
        previousPage = history.records
        records += history.records
        if (expectedTotal != null && records.size > expectedTotal) {
            return AhuResult.Failure(AhuError.ProtocolChanged("用电记录总数不一致"))
        }
        // The HAR declares ispage=true but returns all records without total. The official
        // client also relies on total, not row, to decide whether to paginate.
        if (expectedTotal == null || records.size >= expectedTotal) {
            return AhuResult.Success(records)
        }
        if (history.records.isEmpty()) {
            return AhuResult.Failure(AhuError.ProtocolChanged("用电记录分页提前结束"))
        }
    }
    return AhuResult.Failure(AhuError.ProtocolChanged("用电记录超出分页限制"))
}
