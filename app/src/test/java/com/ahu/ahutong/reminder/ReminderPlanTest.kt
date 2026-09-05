package com.ahu.ahutong.reminder

import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlanTest {
    private val now = 1_800_000_000_000L
    private val due = now + 2 * 60 * 60_000L
    private val setting = RemindSetting(enabled = true, leadMinutes = 60)
    private val event = CustomEvent(id = "local", title = "本地日程", startTs = due, endTs = due + 60_000L)
    private val work = Work(workId = "remote", title = "作业", endTs = due)

    @Test
    fun `local reminders are scheduled without a learning platform session`() {
        val plan = ReminderPlan.build(setting, false, listOf(work), listOf(event), now)
        assertEquals(listOf("event_local|$due"), plan.map { it.key })
        assertEquals(due - 60 * 60_000L, plan.single().triggerAtMillis)
    }

    @Test
    fun `logging out removes remote reminders while retaining the local plan`() {
        val before = ReminderPlan.build(setting, true, listOf(work), listOf(event), now)
        val after = ReminderPlan.build(setting, false, emptyList(), listOf(event), now)
        assertEquals(2, before.size)
        assertEquals(before.filter { it.key.startsWith("event_") }, after)
    }

    @Test
    fun `disabled reminders remain disabled for guests and signed in users`() {
        listOf(false, true).forEach { signedIn ->
            assertTrue(ReminderPlan.build(setting.copy(enabled = false), signedIn, listOf(work), listOf(event), now).isEmpty())
        }
    }

    @Test
    fun `finished tasks and elapsed reminder times are not rescheduled`() {
        val works = listOf(work.copy(status = "已提交"), work.copy(endTs = now + 30 * 60_000L))
        val events = listOf(event.copy(done = true), event.copy(startTs = now + 30 * 60_000L))
        assertTrue(ReminderPlan.build(setting, true, works, events, now).isEmpty())
        assertEquals(1, ReminderPlan.build(setting.copy(onlyTodo = false), true, works, events, now).size)
    }
}
