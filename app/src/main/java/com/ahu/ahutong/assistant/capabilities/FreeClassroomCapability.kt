package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.data.FreeClassroomGateway
import com.ahu.ahutong.data.crawler.model.jwxt.DateTimeSegmentCmd
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsRequest
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

/**
 * 空闲教室：复用 [FreeClassroomGateway]，默认查今天、磬苑校区、1~13 节的全部教学楼。
 * 与 FreeClassroomViewModel 同一套请求形状（buildingId 为空表示不限楼栋），不改任何页面逻辑。
 */
class FreeClassroomCapability(
    private val loadRooms: suspend (GetFreeRoomsRequest) -> List<FreeRoom> = { request ->
        FreeClassroomGateway.freeRooms(request).roomList
    },
    private val today: () -> LocalDate = LocalDate::now,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_free_rooms",
        title = "空闲教室",
        description = "查询指定日期（默认今天）某校区的空闲教室。campusId: 1=磬苑校区，2=龙河校区；units 为节次，默认 1-13；building 可按教学楼筛选。",
        parameters = JsonParser.parseString(
            """
            {
              "type": "object",
              "properties": {
                "date": { "type": "string", "description": "yyyy-MM-dd，默认今天" },
                "campusId": { "type": "integer", "enum": [1, 2], "description": "1=磬苑校区，2=龙河校区" },
                "units": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "节次，默认 1-13"
                },
                "building": { "type": "string", "description": "教学楼名称，例如 博学北楼；不传返回全部" }
              },
              "additionalProperties": false
            }
            """.trimIndent()
        ).asJsonObject,
        sensitivity = AssistantSensitivity.ACADEMIC,
        openRoute = "free_classroom",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val date = arguments.get("date")?.asString
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: today()
        val campusId = arguments.get("campusId")?.asInt?.takeIf { it == 1 || it == 2 } ?: 1
        val building = arguments.stringArg("building")
        val units = arguments.get("units")?.asJsonArray
            ?.mapNotNull { runCatching { it.asString }.getOrNull() }
            ?.filter { it.toIntOrNull() in 1..13 }
            ?.ifEmpty { null }
            ?: (1..13).map(Int::toString)

        val request = GetFreeRoomsRequest(
            buildingId = "",
            campusId = campusId.toString(),
            dateTimeSegmentCmd = DateTimeSegmentCmd(
                startDateTime = date.toString(),
                endDateTime = date.toString(),
                units = units,
            ),
        )
        return try {
            val rooms = loadRooms(request)
                .filter { room -> building == null || room.building.nameZh.contains(building, ignoreCase = true) }
                .distinctBy { "${it.id}-${it.building.id}" }
                .sortedWith(compareBy({ it.building.nameZh }, { it.floor }, { it.nameZh }))
            val campusName = if (campusId == 2) "龙河校区" else "磬苑校区"
            if (rooms.isEmpty()) {
                AssistantOutcome.Success(
                    AssistantAnswer("$campusName ${date.monthValue}月${date.dayOfMonth}日暂无空教室", route = spec.openRoute)
                )
            } else {
                val preview = rooms.take(MAX_PREVIEW).map { room ->
                    buildString {
                        append(room.building.nameZh)
                        append(' ')
                        append(room.floor)
                        append("F · ")
                        append(room.nameZh)
                        if (room.seats > 0) {
                            append(" · ")
                            append(room.seats)
                            append("座")
                        }
                    }
                }
                val more = (rooms.size - preview.size).takeIf { it > 0 }?.let { "…还有 $it 间" }
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = "$campusName ${date.monthValue}月${date.dayOfMonth}日有 ${rooms.size} 间空教室",
                        lines = preview + listOfNotNull(more),
                        route = spec.openRoute,
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure(
                code = "upstream",
                message = error.message ?: "空闲教室查询失败",
                retryable = true,
            )
        }
    }

    private companion object {
        const val MAX_PREVIEW = 8
    }
}
