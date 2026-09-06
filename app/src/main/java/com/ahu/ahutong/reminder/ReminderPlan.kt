package com.ahu.ahutong.reminder

import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Work
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class PlannedReminder(
    val key: String,
    val triggerAtMillis: Long,
    val title: String,
    val content: String
)

internal object ReminderPlan {
    fun build(
        setting: RemindSetting,
        hasSession: Boolean,
        works: List<Work>,
        events: List<CustomEvent>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<PlannedReminder> {
        if (!setting.enabled) return emptyList()
        val leadMillis = setting.leadMinutes.coerceAtLeast(0) * 60_000L
        val formatter = DateTimeFormatter.ofPattern("M-d HH:mm").withZone(zone)
        fun format(time: Long): String = formatter.format(Instant.ofEpochMilli(time))

        return buildList {
            if (hasSession) {
                works.forEach { work ->
                    val end = work.endTs ?: return@forEach
                    if (end <= nowMillis || work.isDone && setting.onlyTodo) return@forEach
                    val trigger = end - leadMillis
                    if (trigger <= nowMillis) return@forEach
                    add(PlannedReminder(
                        key = "${work.workId}|$end",
                        triggerAtMillis = trigger,
                        title = work.courseName.ifEmpty { "作业提醒" },
                        content = "${work.title} 将于 ${format(end)} 截止"
                    ))
                }
            }
            events.forEach { event ->
                if (event.done || event.startTs <= nowMillis) return@forEach
                val trigger = event.startTs - leadMillis
                if (trigger <= nowMillis) return@forEach
                add(PlannedReminder(
                    key = "event_${event.id}|${event.startTs}",
                    triggerAtMillis = trigger,
                    title = "日程提醒",
                    content = "${event.title} 将于 ${format(event.startTs)} 开始"
                ))
            }
        }
    }
}
