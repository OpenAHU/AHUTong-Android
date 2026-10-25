package com.ahu.ahutong.testing

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.grade.ExamSource
import com.ahu.ahutong.data.grade.GradeSource
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.model.ScheduleConfigBean
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.schedule.ConfigSource
import com.ahu.ahutong.data.schedule.ResolvedConfig
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.data.schedule.SemesterKey
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.preset.AppliedPreset
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.preset.PresetSuggestions
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 成绩页与考试页的端口 fake：成绩、档案、排名、考试缓存与假数据信号。 */
class FakeGradeSource(
    var gradeResult: AhuResult<Grade> = AhuResult.Success(Grade()),
    var profileList: List<GradeStudentProfile> = emptyList(),
    var rankResult: AhuResult<GpaRankInfo> = AhuResult.Success(GpaRankInfo()),
    var mockData: Boolean = false
) : GradeSource {

    var cachedProfileList: List<GradeStudentProfile> = emptyList()
    var perProfile: Map<String, Grade?> = emptyMap()
    var rankFetcher: (suspend (String) -> AhuResult<GpaRankInfo>)? = null
    val savedRanks = mutableListOf<Pair<String, GpaRankInfo>>()

    override fun usesMockData(): Boolean = mockData
    override fun mockRefreshRevisions(): Flow<Long> = MutableStateFlow(0L)
    override fun cachedProfiles(): List<GradeStudentProfile> = cachedProfileList
    override fun cachedPerProfileGrades(): Map<String, Grade?> = perProfile
    override fun cachedGpaRank(studentId: String): GpaRankInfo? = null

    override suspend fun fetchGrade(isRefresh: Boolean): AhuResult<Grade> = gradeResult

    override suspend fun fetchProfiles(): List<GradeStudentProfile> = profileList

    override suspend fun fetchGpaRank(studentId: String): AhuResult<GpaRankInfo> =
        rankFetcher?.invoke(studentId) ?: rankResult

    override fun saveGpaRank(studentId: String, rank: GpaRankInfo) {
        savedRanks += studentId to rank
    }
}

class FakeExamSource(
    var exams: List<Exam> = emptyList(),
    var cached: List<Exam> = emptyList(),
    var cachedAtMillis: Long = 0L,
    var mockData: Boolean = false,
    var failure: AhuError? = null
) : ExamSource {

    var fetchCount = 0
        private set
    var lastStudentId: String? = null
        private set

    override fun cachedExams(): List<Exam> = cached
    override fun cachedAt(): Long = cachedAtMillis

    override suspend fun fetchExams(
        isRefresh: Boolean,
        studentId: String,
        studentName: String
    ): AhuResult<List<Exam>> {
        fetchCount++
        lastStudentId = studentId
        return failure?.let { AhuResult.Failure(it) } ?: AhuResult.Success(exams)
    }

    override fun usesMockData(): Boolean = mockData
    override fun mockRefreshRevisions(): Flow<Long> = MutableStateFlow(0L)
}

/** [SessionIdentity] 的 fake：成绩页只看「有没有登录用户」。 */
class FakeGradeSession(
    var loggedIn: Boolean = true,
    var user: User? = User("张三", "2021001")
) : SessionIdentity {

    override fun isLoggedIn(): Boolean = loggedIn
    override fun currentUser(): User? = user
}

/** [ScheduleWeekConfig] 的 fake：成绩页只用得到 cachedSemesterKey()。 */
class FakeGradeWeekConfig(
    var cachedKey: SemesterKey? = null
) : ScheduleWeekConfig {

    override fun cachedSemesterKey(): SemesterKey? = cachedKey
    override fun storedSchoolYear(): String? = cachedKey?.schoolYear
    override fun currentWeekDay(): Int = 1
    override fun buildSemesterKey(schoolYear: String, schoolTerm: String): String =
        schoolYear + "-" + schoolTerm

    override fun isShowAllCourse(): Boolean = false

    override suspend fun resolveLocalFirst(): ResolvedConfig =
        ResolvedConfig(ScheduleConfigBean(), ConfigSource.LOCAL)

    override suspend fun syncRemote(): ResolvedConfig? = null

    override suspend fun saveSchoolYearAndTerm(schoolYear: String, semesterKey: String) = Unit

    override suspend fun saveTermPosition(
        schoolYear: String,
        schoolTerm: String,
        startTime: String,
        isInSemester: Boolean,
        observedOn: String
    ) = Unit
}

/** [BehaviorRecorder] 的 fake：记下每次内容状态与动作。 */
class FakeGradeBehavior : BehaviorRecorder {

    val organicActions = mutableListOf<AppActionId>()
    val contentStates = mutableListOf<ContentStateBucket>()
    val examDistances = mutableListOf<ExamDistanceBucket>()

    override fun recordOrganicAction(action: AppActionId) {
        organicActions += action
    }

    override fun recordContentState(
        domain: SemanticDomain,
        contentState: ContentStateBucket,
        freshnessBucket: Int,
        resultCount: ResultCountBucket,
        errorType: ErrorTypeBucket
    ) {
        contentStates += contentState
    }

    override fun reportCommittedMutation(
        mutationId: MutationId,
        oldValue: Any?,
        newValue: Any?,
        coarseValueBucket: String?
    ) = Unit

    override fun recordExamDistance(bucket: ExamDistanceBucket) {
        examDistances += bucket
    }
}

/** [PresetSuggestions] 的 fake：默认「没有可推荐的候选」，用例需要时再编排。 */
class FakePresetSuggestions(
    var applied: AppliedPreset? = null
) : PresetSuggestions {

    val submissions = mutableListOf<PresetSubmission>()

    override suspend fun rank(domain: SemanticDomain): List<PresetCandidate> = emptyList()

    override suspend fun markExposed(candidate: PresetCandidate): PresetInteractionToken? = null

    override suspend fun apply(candidate: PresetCandidate): AppliedPreset? = applied

    override fun expire(token: PresetInteractionToken?) = Unit

    override suspend fun recordNaturalSubmission(
        submission: PresetSubmission,
        interactionToken: PresetInteractionToken?,
        candidatesAtOpportunity: List<PresetCandidate>
    ) {
        submissions += submission
    }
}

