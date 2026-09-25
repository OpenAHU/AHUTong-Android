package com.ahu.ahutong.ui.state

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.testing.FakeExamSource
import com.ahu.ahutong.testing.FakeGradeBehavior
import com.ahu.ahutong.testing.FakeGradeSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule

/**
 * 考试页 ViewModel 的契约测试：考试源、登录态与行为上报都是 fake。
 *
 * 钉住的是几件在界面上看不出差别的事：新鲜缓存不再打网络、过期缓存先出旧数据再拉一次、
 * 没登录也没开假数据时给 Unauthorized 且不发请求、假数据模式用占位学号、
 * 手动刷新与考试距离各上报一次，以及刷新失败时不把已显示的缓存清掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelTest {

    @get:Rule
    val instantTaskExecutor = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        source: FakeExamSource = FakeExamSource(),
        session: FakeGradeSession = FakeGradeSession(),
        behavior: FakeGradeBehavior = FakeGradeBehavior()
    ) = ExamViewModel(source, session, behavior)

    @Test
    fun `a fresh cache is served without touching the network`() = runTest(dispatcher) {
        val cached = listOf(examOf("高等数学"))
        val source = FakeExamSource(cached = cached, cachedAtMillis = System.currentTimeMillis())
        val subject = viewModel(source = source)

        subject.loadExam()
        advanceUntilIdle()

        assertEquals(1, (subject.data.value as AhuResult.Success).value.size)
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun `a stale cache is refreshed and the new list is published`() = runTest(dispatcher) {
        val source = FakeExamSource(
            cached = listOf(examOf("旧考试")),
            cachedAtMillis = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000,
            exams = listOf(examOf("新考试"))
        )
        val subject = viewModel(source = source)

        subject.loadExam()
        advanceUntilIdle()

        assertEquals(1, source.fetchCount)
        val list = (subject.data.value as AhuResult.Success).value
        assertEquals("新考试", list.single().course)
    }

    @Test
    fun `without a session and without mock data it reports unauthorized and sends nothing`() = runTest(dispatcher) {
        val source = FakeExamSource()
        val subject = viewModel(source = source, session = FakeGradeSession(loggedIn = false, user = null))

        subject.loadExam()
        advanceUntilIdle()

        assertTrue(subject.data.value is AhuResult.Failure)
        assertEquals("账户未登录", subject.errorMessage.value)
        assertEquals(0, source.fetchCount)
    }

    @Test
    fun `mock data mode fetches without a session using the placeholder student`() = runTest(dispatcher) {
        val source = FakeExamSource(exams = listOf(examOf("模拟考试")), mockData = true)
        val subject = viewModel(source = source, session = FakeGradeSession(loggedIn = false, user = null))

        subject.loadExam(isRefresh = true)
        advanceUntilIdle()

        assertEquals(1, source.fetchCount)
        assertEquals("mock-student", source.lastStudentId)
    }

    @Test
    fun `manual refresh and exam distance are both reported`() = runTest(dispatcher) {
        val behavior = FakeGradeBehavior()
        val subject = viewModel(behavior = behavior)

        subject.onManualRefresh()
        subject.onExamDistance(ExamDistanceBucket.WITHIN_ONE_DAY)

        assertEquals(listOf(AppActionId.MANUAL_REFRESH_EXAM), behavior.organicActions)
        assertEquals(listOf(ExamDistanceBucket.WITHIN_ONE_DAY), behavior.examDistances)
    }

    @Test
    fun `a failed manual refresh keeps the cached list and returns to idle`() = runTest(dispatcher) {
        val cached = listOf(examOf("高等数学"))
        val source = FakeExamSource(cached = cached, cachedAtMillis = System.currentTimeMillis())
        val subject = viewModel(source = source)

        // 先让缓存出屏（新鲜缓存不打网络），再把网络打开成失败，手动刷新一次。
        subject.loadExam()
        advanceUntilIdle()
        assertEquals(0, source.fetchCount)
        source.failure = AhuError.Network

        subject.loadExam(isRefresh = true)
        advanceUntilIdle()

        assertEquals(1, source.fetchCount)
        assertEquals(1, (subject.data.value as AhuResult.Success).value.size)
        assertNull(subject.errorMessage.value)
        assertEquals(RefreshState.IDLE, subject.refreshState.value)
    }

    private fun examOf(course: String): Exam = Exam().apply {
        setCourse(course)
        setLocation("博学北楼 A101")
        setTime("2026-01-06 09:00")
        setSeatNum("12")
        setFinished(false)
    }
}
