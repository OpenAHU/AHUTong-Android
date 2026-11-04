package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.adapter.RepositoryElectricityDepositSource
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.google.gson.JsonObject
import java.time.LocalDate

/**
 * 资金类只读能力：账单、电费、电控条件。充值/切换等动作只跳原页面。
 */
class GetBillsCapability(
    private val summary: suspend (String, String) -> AhuResult<com.ahu.ahutong.data.crawler.model.ycard.TurnoverCount> =
        { from, to -> AHURepository.getBillSummary(from, to) },
    private val page: suspend (Int, Int) -> AhuResult<com.ahu.ahutong.data.crawler.model.ycard.TurnoverPage> =
        { pageNo, size -> AHURepository.getBillPage(page = pageNo, size = size) },
    private val today: () -> LocalDate = LocalDate::now,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_bills",
        title = "查询一卡通账单",
        description = "查询校园一卡通近期流水：本月消费、充值、消费地点和逐笔明细。",
        parameters = emptySchema(),
        sensitivity = AssistantSensitivity.FINANCIAL,
        source = "一卡通系统",
        openRoute = "billing_stats",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val date = today()
        val from = date.withDayOfMonth(1).toString()
        val to = date.withDayOfMonth(date.lengthOfMonth()).toString()
        val monthSummary = (summary(from, to) as? AhuResult.Success)?.value
        val pageResult = page(1, PAGE_SIZE)
        val records = when (pageResult) {
            is AhuResult.Failure -> return pageResult.error.asAssistantFailure()
            is AhuResult.Success -> pageResult.value.records.orEmpty()
        }
        if (records.isEmpty() && monthSummary == null) {
            return AssistantOutcome.Success(
                AssistantAnswer("本月还没有账单记录", route = spec.openRoute)
            )
        }
        val lines = records.take(MAX_RECORDS).map { record ->
            buildString {
                record.effectdateStr?.takeIf { it.isNotBlank() }?.let {
                    append(it.take(16))
                    append(" · ")
                }
                append(record.resume?.takeIf { it.isNotBlank() } ?: record.toMerchant.orEmpty())
                append(" · ")
                if (record.isExpense) append("-") else append("+")
                append("¥")
                append(formatMoney(record.tranamt / 100.0))
            }
        }
        val expense = monthSummary?.expenses?.let { formatMoney(it / 100.0) } ?: "-"
        val income = monthSummary?.income?.let { formatMoney(it / 100.0) } ?: "-"
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "本月支出 ¥$expense，收入 ¥$income",
                lines = lines,
                route = spec.openRoute,
            )
        )
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_RECORDS = 10
    }
}

class GetElectricityCapability(
    private val source: ElectricityDepositSource = RepositoryElectricityDepositSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_electricity",
        title = "查询宿舍电费",
        description = "查询当前电费房间的剩余电量与账户信息。只读取已保存的房间，不切换、不充值。",
        parameters = emptySchema(),
        sensitivity = AssistantSensitivity.FINANCIAL,
        source = "电费系统",
        openRoute = "electricity_pay",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val selection = source.roomSelection()?.takeIf { it.room != null }
            ?: return AssistantOutcome.Success(
                AssistantAnswer("还没选电费房间，打开电费充值页先选一次", route = spec.openRoute)
            )
        val controller = selection.controller ?: source.selectedController()
        return when (val result = source.room(controller, selection)) {
            is AhuResult.Failure -> result.error.asAssistantFailure()
            is AhuResult.Success -> {
                val room = result.value
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = roomSummary(selection, room),
                        lines = roomLines(room),
                        route = spec.openRoute,
                    )
                )
            }
        }
    }

    private fun roomSummary(selection: RoomSelectionInfo, room: ElectricityRoom): String {
        val display = room.displayInfo?.takeIf { it.isNotBlank() }
        if (display != null) return display
        val place = listOfNotNull(
            selection.campus?.name,
            selection.building?.name,
            selection.floor?.name,
            selection.room?.name,
        ).joinToString(" ")
        return if (place.isBlank()) "电费账户" else place
    }

    private fun roomLines(room: ElectricityRoom): List<String> = buildList {
        val details = room.details
        val place = listOfNotNull(details.areaName, details.buildingName, details.floorName, details.roomName)
            .joinToString(" ")
        if (place.isNotBlank()) add("房间：$place")
        details.account?.takeIf { it.isNotBlank() }?.let { add("账户：$it") }
    }
}

class GetElectricityConditionsCapability(
    private val source: ElectricityDepositSource = RepositoryElectricityDepositSource(),
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_electricity_conditions",
        title = "查询电控条件",
        description = "查询当前电费房间和最近使用过的电控条件（照明、空调等），用于切换前确认。",
        parameters = emptySchema(),
        sensitivity = AssistantSensitivity.FINANCIAL,
        source = "电费系统",
        openRoute = "electricity_recent_rooms",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val current = source.roomSelection()?.takeIf { it.room != null }
        val items = buildList {
            addAll(source.depositHistory())
            if (current != null) {
                add(ElectricityDepositHistoryItem(selection = current, label = "", updatedAt = 0L))
            }
        }.distinctBy { selectionKey(it.selection) }
        if (items.isEmpty()) {
            return AssistantOutcome.Success(
                AssistantAnswer("还没有保存过电控条件，打开电费页选择一次房间", route = spec.openRoute)
            )
        }
        val lines = items.mapIndexed { index, item ->
            "$index. " + labelOf(item.selection)
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "已保存 ${items.size} 个电控条件",
                lines = lines,
                route = spec.openRoute,
            )
        )
    }

    private fun labelOf(selection: RoomSelectionInfo): String {
        val controller = selection.controller ?: source.selectedController()
        val place = selection.room?.name?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(selection.building?.name, selection.floor?.name)
                .joinToString(" · ")
                .takeIf { it.isNotBlank() }
        return listOfNotNull(controller.displayName, place).joinToString(" · ")
    }

    private fun selectionKey(selection: RoomSelectionInfo): String = listOf(
        selection.controller?.name,
        selection.campus?.value,
        selection.building?.value,
        selection.floor?.value,
        selection.room?.value,
    ).joinToString("|") { it ?: "" }
}
