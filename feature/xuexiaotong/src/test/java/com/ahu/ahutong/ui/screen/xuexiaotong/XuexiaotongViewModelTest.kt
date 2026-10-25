package com.ahu.ahutong.ui.screen.xuexiaotong

import com.ahu.ahutong.data.xuexiaotong.Course
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.testing.FakeChaoxingReminders
import com.ahu.ahutong.testing.FakeChaoxingSession
import com.ahu.ahutong.testing.FakeChaoxingStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * 学习通 ViewModel 的契约测试：三个协作方都是 fake，因此不连学习通、不碰存储、不需要设备。
 *
 * 这里钉住的几件事在界面上看不出来：状态从存储的哪几处读、开关是不是落盘、
 * 登出后提醒要重排、同步成功要写时间戳、同步失败也不能把已排的提醒丢下。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class XuexiaotongViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an early toggle applies after the saved setting is restored`() = runTest(dispatcher) {
        val store = FakeChaoxingStore(showDone = true)
        val subject = XuexiaotongViewModel(FakeChaoxingSession(), store, FakeChaoxingReminders(), dispatcher)
        subject.toggleShowDone()
        advanceUntilIdle()
        assertFalse(subject.showDone.value)
        assertFalse(store.showDone)
    }

    @Test
    fun `toggles are persisted through the store`() = runTest(dispatcher) {
        val store = FakeChaoxingStore()
        val subject = XuexiaotongViewModel(FakeChaoxingSession(), store, FakeChaoxingReminders(), dispatcher)
        advanceUntilIdle()

        subject.toggleShowDone()
        subject.toggleDoneGray()
        subject.toggleShowEmptyCourses()

        assertTrue(subject.showDone.value)
        assertTrue(store.showDone)
        assertTrue(subject.doneGray.value)
        assertTrue(store.doneGray)
        assertTrue(subject.showEmptyCourses.value)
        assertTrue(store.showEmptyCourses)
    }

    @Test
    fun `saving the reminder setting stores it and reschedules`() = runTest(dispatcher) {
        val store = FakeChaoxingStore()
        val reminders = FakeChaoxingReminders()
        val subject = XuexiaotongViewModel(FakeChaoxingSession(), store, reminders, dispatcher)
        advanceUntilIdle()

        subject.saveRemind(RemindSetting(enabled = true, leadMinutes = 15, onlyTodo = false))

        assertEquals(RemindSetting(enabled = true, leadMinutes = 15, onlyTodo = false), store.savedRemindSetting)
        assertEquals(1, reminders.rescheduleCount)
    }

    @Test
    fun `logout clears the session and the login data, then re-schedules reminders`() = runTest(dispatcher) {
        val session = FakeChaoxingSession()
        val store = FakeChaoxingStore(
            works = listOf(Work(title = "作业一")),
            lastSync = 42L
        )
        val reminders = FakeChaoxingReminders()
        val subject = XuexiaotongViewModel(session, store, reminders, dispatcher)
        advanceUntilIdle()

        subject.logout()

        assertFalse(subject.loggedIn.value)
        assertEquals(1, session.clearSessionCount)
        assertEquals(1, store.clearLoginDataCount)
        assertEquals(1, reminders.cancelCount)
        assertEquals(1, reminders.scheduleCount)
        assertTrue(subject.works.value.isEmpty())
        assertEquals(0L, subject.lastSync.value)
    }

    @Test
    fun `a successful sync stores the works and the timestamp, then reschedules`() = runTest(dispatcher) {
        val session = FakeChaoxingSession(works = listOf(Work(title = "作业一")))
        val store = FakeChaoxingStore(courses = listOf(Course(courseId = "c1", name = "高等数学")))
        val reminders = FakeChaoxingReminders()
        val subject = XuexiaotongViewModel(session, store, reminders, dispatcher)
        advanceUntilIdle()

        subject.syncWorks()
        advanceUntilIdle()

        assertEquals(1, session.silentReloginCount)
        assertEquals(1, session.syncWorksCount)
        assertEquals(listOf(Work(title = "作业一")), subject.works.value)
        assertTrue(store.lastSync > 0L)
        assertEquals(1, reminders.rescheduleCount)
        assertFalse(subject.syncing.value)
    }

    @Test
    fun `a failed sync reports the message and still reschedules`() = runTest(dispatcher) {
        val session = FakeChaoxingSession(syncFailure = IllegalStateException("网络连接失败"))
        val reminders = FakeChaoxingReminders()
        val subject = XuexiaotongViewModel(session, FakeChaoxingStore(), reminders, dispatcher)
        advanceUntilIdle()

        subject.syncWorks()
        advanceUntilIdle()

        assertEquals("网络连接失败", subject.syncProgress.value.message)
        assertFalse(subject.syncing.value)
        // 失败也要重排：已排的提醒基于旧数据，不能就这么留着。
        assertEquals(1, reminders.rescheduleCount)
    }

    @Test
    fun `a sync without a session does nothing`() = runTest(dispatcher) {
        val session = FakeChaoxingSession(hasSession = false)
        val subject = XuexiaotongViewModel(session, FakeChaoxingStore(), FakeChaoxingReminders(), dispatcher)
        advanceUntilIdle()

        subject.syncWorks()
        advanceUntilIdle()

        assertFalse(subject.loggedIn.value)
        assertEquals(0, session.syncWorksCount)
    }

    @Test
    fun `custom events are persisted through the store`() = runTest(dispatcher) {
        val store = FakeChaoxingStore()
        val reminders = FakeChaoxingReminders()
        val subject = XuexiaotongViewModel(FakeChaoxingSession(), store, reminders, dispatcher)
        advanceUntilIdle()

        subject.addCustomEvent(CustomEvent(id = "e1", title = "考试"))
        subject.toggleCustomEventDone("e1")

        assertEquals(1, store.customEvents.size)
        assertTrue(store.customEvents.single().done)
        assertEquals(2, reminders.rescheduleCount)
    }

    @Test
    fun `login goes through the session port`() = runTest(dispatcher) {
        val session = FakeChaoxingSession()
        val subject = XuexiaotongViewModel(session, FakeChaoxingStore(), FakeChaoxingReminders(), dispatcher)
        advanceUntilIdle()

        subject.login("13800000000", "secret")

        assertEquals(listOf("13800000000" to "secret"), session.loginCalls)
    }

    @Test
    fun `a rejected login surfaces the message and changes nothing`() = runTest(dispatcher) {
        val session = FakeChaoxingSession(
            hasSession = false,
            loginFailure = IllegalStateException("账号或密码错误")
        )
        val subject = XuexiaotongViewModel(session, FakeChaoxingStore(), FakeChaoxingReminders(), dispatcher)
        advanceUntilIdle()

        val error = assertFailsWith<IllegalStateException> {
            subject.login("13800000000", "wrong")
        }

        assertEquals("账号或密码错误", error.message)
        assertFalse(subject.loggedIn.value)
    }
}
