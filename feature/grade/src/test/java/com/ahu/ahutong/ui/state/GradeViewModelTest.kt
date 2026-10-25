package com.ahu.ahutong.ui.state

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.personalization.preset.AppliedPreset
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.testing.FakeGradeBehavior
import com.ahu.ahutong.testing.FakeGradeSession
import com.ahu.ahutong.testing.FakeGradeSource
import com.ahu.ahutong.testing.FakeGradeWeekConfig
import com.ahu.ahutong.testing.FakePresetSuggestions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Rule

/**
 * 成绩页 ViewModel 的契约测试：五个协作方都是 fake，因此不连教务、不碰缓存、不需要设备。
 *
 * 钉住的是几件在界面上看不出差别的事：成功加载要上报内容状态、空成绩与有成绩用不同的桶、
 * 失败要用 ADR 0001 的文案并上报错误、排名取到要落盘、取不到要给一句明确的空态、
 * 以及手动刷新要上报成用户动作。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GradeViewModelTest {

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
        source: FakeGradeSource = FakeGradeSource(),
        behavior: FakeGradeBehavior = FakeGradeBehavior(),
        presets: FakePresetSuggestions = FakePresetSuggestions(),
        session: FakeGradeSession = FakeGradeSession()
    ) = GradeViewModel(behavior, presets, source, session, FakeGradeWeekConfig(), dispatcher)

    @Test
    fun `a successful load records a ready content state`() = runTest(dispatcher) {
        val behavior = FakeGradeBehavior()
        val subject = viewModel(source = FakeGradeSource(gradeResult = AhuResult.Success(gradeOf(2))), behavior = behavior)

        subject.getGarde()
        advanceUntilIdle()

        assertEquals(2, subject.grade?.termGradeList?.sumOf { it.gradeList.orEmpty().size })
        assertNull(subject.errorMessage)
        assertEquals(listOf(ContentStateBucket.READY), behavior.contentStates)
    }

    @Test
    fun `an empty grade records the empty bucket`() = runTest(dispatcher) {
        val behavior = FakeGradeBehavior()
        val subject = viewModel(source = FakeGradeSource(gradeResult = AhuResult.Success(Grade())), behavior = behavior)

        subject.getGarde()
        advanceUntilIdle()

        assertEquals(listOf(ContentStateBucket.EMPTY), behavior.contentStates)
    }

    @Test
    fun `a failed load reports the error model wording and records an error`() = runTest(dispatcher) {
        val behavior = FakeGradeBehavior()
        val subject = viewModel(
            source = FakeGradeSource(gradeResult = AhuResult.Failure(AhuError.Network)),
            behavior = behavior
        )

        subject.getGarde()
        advanceUntilIdle()

        assertEquals(AhuError.Network.toUserMessage(), subject.errorMessage)
        assertEquals(listOf(ContentStateBucket.ERROR), behavior.contentStates)
    }

    @Test
    fun `a fetched rank is saved for the selected profile`() = runTest(dispatcher) {
        val profile = profileOf("122850")
        val rank = GpaRankInfo(gpa = 3.7, majorRank = 3, majorHeadCount = 120)
        val source = FakeGradeSource(
            profileList = listOf(profile),
            rankResult = AhuResult.Success(rank)
        )
        val subject = viewModel(source = source)

        subject.getGarde()
        advanceUntilIdle()

        assertEquals(rank, subject.gpaRankInfo)
        assertEquals(listOf("122850" to rank), source.savedRanks)
    }

    @Test
    fun `switching profile cancels the old rank request and loads the selected rank`() = runTest(dispatcher) {
        val primary = profileOf("primary")
        val minor = profileOf("minor")
        val primaryResult = CompletableDeferred<AhuResult<GpaRankInfo>>()
        val minorResult = CompletableDeferred<AhuResult<GpaRankInfo>>()
        val primaryRank = GpaRankInfo(gpa = 3.7, majorRank = 3)
        val minorRank = GpaRankInfo(gpa = 3.2, majorRank = 8)
        val requestedProfiles = mutableListOf<String>()
        val source = FakeGradeSource().apply {
            cachedProfileList = listOf(primary, minor)
            perProfile = mapOf(primary.id to gradeOf(1), minor.id to gradeOf(1))
            rankFetcher = { studentId ->
                requestedProfiles += studentId
                if (studentId == primary.id) primaryResult.await() else minorResult.await()
            }
        }
        val subject = viewModel(source = source)

        subject.getGpaRank()
        runCurrent()
        subject.selectProfile(1)
        runCurrent()

        assertEquals(listOf(primary.id, minor.id), requestedProfiles)
        assertTrue(subject.rankLoading)
        assertNull(subject.gpaRankInfo)
        primaryResult.complete(AhuResult.Success(primaryRank))
        runCurrent()
        assertNull(subject.gpaRankInfo)
        assertTrue(subject.rankLoading)

        minorResult.complete(AhuResult.Success(minorRank))
        advanceUntilIdle()

        assertEquals(minorRank, subject.gpaRankInfo)
        assertEquals(listOf(minor.id to minorRank), source.savedRanks)
        assertEquals(false, subject.rankLoading)
    }

    @Test
    fun `a preset profile switch also discards the previous profile rank`() = runTest(dispatcher) {
        val primary = profileOf("primary")
        val minor = profileOf("minor")
        val primaryResult = CompletableDeferred<AhuResult<GpaRankInfo>>()
        val minorResult = CompletableDeferred<AhuResult<GpaRankInfo>>()
        val primaryRank = GpaRankInfo(gpa = 3.7, majorRank = 3)
        val minorRank = GpaRankInfo(gpa = 3.2, majorRank = 8)
        val requestedProfiles = mutableListOf<String>()
        val source = FakeGradeSource().apply {
            cachedProfileList = listOf(primary, minor)
            perProfile = mapOf(primary.id to gradeOf(1), minor.id to gradeOf(1)).mapValues { (_, grade) ->
                grade.apply { termGradeList.first().setSchoolYear("2025-2026") }
            }
            rankFetcher = { studentId ->
                requestedProfiles += studentId
                if (studentId == primary.id) primaryResult.await() else minorResult.await()
            }
        }
        val payload = """{"profileIndex":1,"schoolYear":"2025-2026","term":"1"}"""
        val candidate = PresetCandidate(
            opportunityId = "opportunity", presetId = "minor-preset", domain = SemanticDomain.GRADE,
            localPayloadJson = payload, coarseFeaturesJson = "{}", statScore = 1f, tinyScore = 1f,
            effectiveScore = 1f, reason = "test"
        )
        val token = PresetInteractionToken("interaction", SemanticDomain.GRADE, "opportunity", "minor-preset", "minor")
        val presets = FakePresetSuggestions(applied = AppliedPreset(payload, token))
        val subject = viewModel(source = source, presets = presets)
        subject.getGpaRank()
        runCurrent()
        subject.gpaRankInfo = primaryRank

        subject.applyPresetCandidate(candidate)
        runCurrent()

        assertEquals(1, subject.selectedProfileIndex)
        assertEquals(listOf(primary.id, minor.id), requestedProfiles)
        assertNull(subject.gpaRankInfo)
        assertTrue(subject.rankLoading)
        primaryResult.complete(AhuResult.Success(primaryRank))
        runCurrent()
        assertNull(subject.gpaRankInfo)
        minorResult.complete(AhuResult.Success(minorRank))
        advanceUntilIdle()

        assertEquals(minorRank, subject.gpaRankInfo)
        assertEquals(listOf(minor.id to minorRank), source.savedRanks)
        assertEquals(1, presets.submissions.size)
    }

    @Test
    fun `a failed rank request reports a loading error instead of missing statistics`() = runTest(dispatcher) {
        val source = FakeGradeSource(
            profileList = listOf(profileOf("122850")),
            rankResult = AhuResult.Failure(AhuError.Network)
        )
        val subject = viewModel(source = source)

        subject.getGarde()
        advanceUntilIdle()

        assertNull(subject.gpaRankInfo)
        assertTrue(subject.rankEmptyMessage?.contains(AhuError.Network.toUserMessage()) == true)
        assertTrue(source.savedRanks.isEmpty())
    }

    @Test
    fun `grades can finish before the independent rank request and keep its loading state`() = runTest(dispatcher) {
        val rankResult = CompletableDeferred<AhuResult<GpaRankInfo>>()
        val rank = GpaRankInfo(gpa = 3.7, majorRank = 3, majorHeadCount = 120)
        val source = FakeGradeSource(
            gradeResult = AhuResult.Success(gradeOf(2)),
            profileList = listOf(profileOf("primary"))
        ).apply { rankFetcher = { rankResult.await() } }
        val subject = viewModel(source = source)

        subject.loadOnEnter()
        runCurrent()

        assertEquals(2, subject.grade?.termGradeList?.sumOf { it.gradeList.size })
        assertFalse(subject.isLoading)
        assertTrue(subject.rankLoading)
        assertNull(subject.gpaRankInfo)
        assertNull(subject.rankEmptyMessage)

        rankResult.complete(AhuResult.Success(rank))
        advanceUntilIdle()

        assertFalse(subject.rankLoading)
        assertEquals(rank, subject.gpaRankInfo)
        assertEquals("3.70", subject.totalGradePointAverage)
        assertNull(subject.rankEmptyMessage)
    }

    @Test
    fun `a failed rank refresh keeps previously loaded rank data visible`() = runTest(dispatcher) {
        val cachedRank = GpaRankInfo(gpa = 3.7, majorRank = 3, majorHeadCount = 120)
        val source = FakeGradeSource(
            profileList = listOf(profileOf("primary")),
            rankResult = AhuResult.Failure(AhuError.Timeout)
        )
        val subject = viewModel(source = source)
        runCurrent()
        subject.gpaRankInfo = cachedRank

        subject.getGpaRank()
        advanceUntilIdle()

        assertFalse(subject.rankLoading)
        assertEquals(cachedRank, subject.gpaRankInfo)
        assertTrue(subject.rankEmptyMessage?.contains(AhuError.Timeout.toUserMessage()) == true)
        assertTrue(source.savedRanks.isEmpty())
    }

    @Test
    fun `without a login user and without mock data the page refuses to open`() {
        // 学年列表是从登录身份算出来的（与迁移前一致）：没有用户、又不是假数据演示，页面就打不开。
        assertFailsWith<IllegalStateException> {
            viewModel(session = FakeGradeSession(loggedIn = false, user = null))
        }
    }

    @Test
    fun `mock data mode opens the page without a login user`() = runTest(dispatcher) {
        val subject = viewModel(
            source = FakeGradeSource(mockData = true),
            session = FakeGradeSession(loggedIn = false, user = null)
        )

        assertEquals("2024-2025", subject.schoolYear)
    }

    private fun gradeOf(items: Int): Grade = Grade().apply {
        val bean = Grade.TermGradeListBean().apply {
            setSchoolYear("2025")
            setTerm("1")
            setGradeList(
                (1..items).map { index ->
                    Grade.TermGradeListBean.GradeListBean().apply {
                        setCourse("课程" + index)
                        setCredit("2.0")
                        setGradePoint("3.5")
                    }
                }
            )
        }
        setTermGradeList(listOf(bean))
    }

    private fun profileOf(id: String) = GradeStudentProfile(
        id = id,
        trainingType = "主修",
        department = "计算机科学与技术学院",
        major = "计算机科学与技术"
    )
}
