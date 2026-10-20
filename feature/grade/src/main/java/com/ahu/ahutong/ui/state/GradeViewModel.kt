package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.grade.GradeSource
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.schedule.ScheduleWeekConfig
import com.ahu.ahutong.ext.getSchoolYears
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.preset.PresetSuggestions
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.data.session.SessionIdentity

@HiltViewModel
class GradeViewModel @Inject constructor(
    private val behavior: BehaviorRecorder,
    private val presets: PresetSuggestions,
    private val grades: GradeSource,
    private val session: SessionIdentity,
    private val weekConfig: ScheduleWeekConfig,
    @GradeDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val tag = "GradeViewModel"

    var totalGradePointAverage by mutableStateOf("暂无")
    var termGradePointAverage by mutableStateOf("暂无")
    var grade by mutableStateOf<Grade?>(null)

    /** 从登录身份算出的学年列表；未登录且没有假数据时抛错（与迁移前一致）。 */
    private val schoolYears: List<String> by lazy {
        session.currentUser()?.getSchoolYears()?.toList()
            ?: if (grades.usesMockData()) {
                listOf("2024-2025", "2023-2024", "2022-2023")
            } else {
                throw IllegalStateException("未登录，无法打开成绩界面！")
            }
    }

    var schoolYear by mutableStateOf(schoolYears.firstOrNull())
    var schoolTerm by mutableStateOf(terms.keys.firstOrNull())
    var errorMessage by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    var gpaRankInfo by mutableStateOf<GpaRankInfo?>(null)
    var rankLoading by mutableStateOf(false)
    var rankEmptyMessage by mutableStateOf<String?>(null)
    var studentProfiles by mutableStateOf<List<GradeStudentProfile>>(emptyList())
    var selectedProfileIndex by mutableStateOf(0)
    var presetCandidates by mutableStateOf<List<PresetCandidate>>(emptyList())
        private set
    private var activePresetInteraction: PresetInteractionToken? = null
    private var candidatesAtOpportunity: List<PresetCandidate> = emptyList()

    /** mock 场景被刷新过几次（0 = 从未）：界面据此重新拉一次。 */
    val mockRefreshRevisions: Flow<Long> = grades.mockRefreshRevisions()

    /** 调试用的假数据开关：为真时不必登录也能看到成绩。 */
    val usesMockData: Boolean get() = grades.usesMockData()

    /** 用户手动刷新成绩：界面只发出意图，上报由这里负责（见 BehaviorRecorder）。 */
    fun onManualRefresh() = behavior.recordOrganicAction(AppActionId.MANUAL_REFRESH_GRADE)

    /** 每个 profile ID → Grade（null = 该专业无成绩） */
    private var perProfileGrades: Map<String, Grade?> = emptyMap()
    private val cacheRestoreJob: Job

    fun getGpaRank() = viewModelScope.launch {
        cacheRestoreJob.join()
        if (rankLoading) {
            Log.i(tag, "getGpaRank skip: already loading")
            return@launch
        }
        rankLoading = true
        rankEmptyMessage = null
        try {
            Log.i(tag, "getGpaRank start profiles=${studentProfiles.size} selected=$selectedProfileIndex")
            val rankProfiles = resolveRankProfiles()
            val safeProfileIndex = selectedProfileIndex.takeIf { it in rankProfiles.indices } ?: 0
            val profile = rankProfiles.getOrNull(safeProfileIndex)
            val studentId = profile?.id ?: run {
                Log.w(tag, "getGpaRank skip: no selected profile")
                return@launch
            }
            val result = grades.fetchGpaRank(studentId)
            Log.i(
                tag,
                "getGpaRank ok=${result.isSuccess} hasData=${result.valueOrNull() != null} " +
                    "error=${result.errorOrNull()?.let { it::class.java.simpleName }.orEmpty()}"
            )
            val rankPayload = result.valueOrNull()
            if (rankPayload != null) {
                gpaRankInfo = rankPayload
                withContext(ioDispatcher) { grades.saveGpaRank(studentId, rankPayload) }
            } else {
                gpaRankInfo = null
                rankEmptyMessage = "「${profile.displayName}」暂无排名信息"
                Log.w(tag, "getGpaRank returned no rank data")
            }
        } catch (t: Throwable) {
            gpaRankInfo = null
            rankEmptyMessage = "获取排名失败：${t.message}"
            Log.w(tag, "getGpaRank failed", t)
        } finally {
            rankLoading = false
            Log.i(tag, "getGpaRank finish rankLoading=$rankLoading hasRank=${gpaRankInfo != null}")
        }
    }

    fun getGarde(isRefresh: Boolean = false) = viewModelScope.launch {
        loadGrade(isRefresh = isRefresh, preserveSelectedTerm = true)
    }

    private suspend fun loadGrade(isRefresh: Boolean, preserveSelectedTerm: Boolean) {
        cacheRestoreJob.join()
        if (isLoading) return
        val previousTerm = selectedTerm()
        isLoading = true
        try {
            val result = grades.fetchGrade(isRefresh)
            if (result.isSuccess) {
            grade = result.valueOrNull()
                // 加载由 CrawlerDataSource 写入的 per-profile 缓存
                perProfileGrades = withContext(ioDispatcher) { grades.cachedPerProfileGrades() }
                val profiles = resolveRankProfiles()
                // 如果 per-profile 缓存为空（单学号学生首次加载），直接使用合并后的 grade
                if (perProfileGrades.isNotEmpty()) {
                    switchToSelectedProfile()
                } else {
                    // 无 per-profile 数据：单学号学生直接使用合并成绩。
                }
                selectTermAfterLoad(previousTerm, preserveSelectedTerm)
                refreshTermAndYearGPA()
                errorMessage = null
                val count = grade?.termGradeList.orEmpty().sumOf { it.gradeList.orEmpty().size }
                behavior.recordContentState(
                    SemanticDomain.GRADE,
                    if (count == 0) ContentStateBucket.EMPTY else ContentStateBucket.READY,
                    freshnessBucket = if (isRefresh) 0 else 1,
                    resultCount = resultCountBucket(count),
                    errorType = ErrorTypeBucket.NONE
                )
                if (profiles.isNotEmpty()) {
                    getGpaRank()
                }
            } else {
                errorMessage = result.errorOrNull()?.toUserMessage() ?: "获取成绩失败"
                reportGradeError()
            }
        } catch (t: Throwable) {
            errorMessage = t.message ?: "获取成绩失败"
            reportGradeError()
        } finally {
            isLoading = false
        }
    }

    private fun switchToSelectedProfile() {
        val profile = studentProfiles.getOrNull(selectedProfileIndex)
        val profileGrade = profile?.let { perProfileGrades[it.id] }
        grade = profileGrade
        // 新专业的成绩可能为空，先更新专业数据，再由统一的学期策略决定定位位置。
        if (profileGrade == null) {
            termGradePointAverage = "暂无"
            totalGradePointAverage = "暂无"
        }
    }

    private suspend fun resolveRankProfiles(): List<GradeStudentProfile> {
        var profiles = studentProfiles
        if (profiles.isEmpty() && !grades.usesMockData()) {
            profiles = withContext(ioDispatcher) { grades.cachedProfiles() }
            Log.i(tag, "resolveRankProfiles cache size=${profiles.size}")
        }
        if (profiles.isEmpty() && !grades.usesMockData()) {
            profiles = grades.fetchProfiles()
            Log.i(tag, "resolveRankProfiles repository size=${profiles.size}")
        }
        if (profiles.isNotEmpty()) {
            if (selectedProfileIndex !in profiles.indices) selectedProfileIndex = 0
            if (perProfileGrades.isNotEmpty() || profiles.size == 1) {
                studentProfiles = profiles
            } else {
                Log.i(
                    tag,
                    "resolveRankProfiles keep selector hidden: profiles=${profiles.size} perProfileGrades=0"
                )
            }
        }
        return profiles
    }

    var isRefreshing by mutableStateOf(false)
        private set

    fun loadOnEnter() {
        if (isLoading || isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                loadGrade(isRefresh = true, preserveSelectedTerm = false)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun refreshGrade() {
        if (isLoading || isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            try {
                loadGrade(isRefresh = true, preserveSelectedTerm = true)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun selectProfile(index: Int) {
        if (selectedProfileIndex == index || index !in studentProfiles.indices) return
        selectedProfileIndex = index
        gpaRankInfo = null
        rankEmptyMessage = null
        val previousTerm = selectedTerm()
        switchToSelectedProfile()
        selectTermAfterLoad(previousTerm, preserveSelectedTerm = true)
        getGpaRank()
        commitCurrentPreset()
    }

    fun selectTerm(year: String, term: String) {
        if (schoolYear == year && schoolTerm == term) return
        schoolYear = year
        schoolTerm = term
        commitCurrentPreset()
    }

    fun applyPresetCandidate(candidate: PresetCandidate) = viewModelScope.launch {
        val applied = presets.apply(candidate) ?: return@launch
        activePresetInteraction = applied.interactionToken
        candidatesAtOpportunity = presetCandidates
        val decoded = runCatching { Gson().fromJson(applied.localPayloadJson, GradePresetPayload::class.java) }.getOrNull()
            ?: return@launch
        if (decoded.profileIndex !in studentProfiles.indices && studentProfiles.isNotEmpty()) return@launch
        if (decoded.schoolYear !in schoolYears || decoded.term !in terms.keys) return@launch
        selectedProfileIndex = decoded.profileIndex.coerceAtLeast(0)
        if (studentProfiles.isNotEmpty()) switchToSelectedProfile()
        schoolYear = decoded.schoolYear
        schoolTerm = decoded.term
        refreshTermAndYearGPA()
        presetCandidates = emptyList()
        commitCurrentPreset()
    }

    private fun commitCurrentPreset() = viewModelScope.launch {
        val year = schoolYear ?: return@launch
        val term = schoolTerm ?: return@launch
        val selectedResult = grade?.termGradeList
            ?.firstOrNull { it.schoolYear == year && it.term == term }
            ?: return@launch
        if (selectedResult.gradeList.isNullOrEmpty()) return@launch
        val profileIndex = selectedProfileIndex.coerceAtLeast(0)
        val payload = GradePresetPayload(profileIndex, year, term)
        val coarse = GradeCoarsePreset(
            termCategory = if (year == schoolYears.firstOrNull()) "CURRENT" else "HISTORICAL",
            profileCategory = if (profileIndex == 0) "PRIMARY" else "OTHER_LOCAL_PROFILE"
        )
        presets.recordNaturalSubmission(
            PresetSubmission(
                SemanticDomain.GRADE,
                Gson().toJson(payload),
                Gson().toJson(coarse),
                "$profileIndex|$year|$term"
            ),
            interactionToken = activePresetInteraction,
            candidatesAtOpportunity = candidatesAtOpportunity.ifEmpty { presetCandidates }
        )
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
        presetCandidates = presets.rank(SemanticDomain.GRADE)
    }

    fun onPresetCandidateVisible(candidate: PresetCandidate) = viewModelScope.launch {
        val token = presets.markExposed(candidate) ?: return@launch
        activePresetInteraction = token
        candidatesAtOpportunity = presetCandidates
    }

    fun onPresetSurfaceDisposed() {
        presets.expire(activePresetInteraction)
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
    }

    private fun reportGradeError() {
        behavior.recordContentState(
            SemanticDomain.GRADE,
            ContentStateBucket.ERROR,
            freshnessBucket = 7,
            resultCount = ResultCountBucket.ZERO,
            errorType = ErrorTypeBucket.NETWORK
        )
    }

    private fun resultCountBucket(count: Int): ResultCountBucket = when (count) {
        0 -> ResultCountBucket.ZERO
        in 1..5 -> ResultCountBucket.ONE_TO_FIVE
        in 6..20 -> ResultCountBucket.SIX_TO_TWENTY
        else -> ResultCountBucket.TWENTY_ONE_PLUS
    }

    companion object {
        val terms = mutableMapOf("1" to "0", "2" to "1")
    }

    init {
        snapshotFlow { gpaRankInfo }
            .onEach { info ->
                totalGradePointAverage = info?.gpa?.let { "%.2f".format(it) } ?: "暂无"
                refreshTermAndYearGPA()
            }
            .launchIn(viewModelScope)

        snapshotFlow { grade }
            .onEach { refreshTermAndYearGPA() }
            .launchIn(viewModelScope)

        snapshotFlow { schoolYear to schoolTerm }
            .onEach { refreshTermAndYearGPA() }
            .launchIn(viewModelScope)

        cacheRestoreJob = viewModelScope.launch {
            val owner = session.currentUser()?.xh to session.isLoggedIn()
            val (cachedProfiles, cachedGrades, cachedRank) = withContext(ioDispatcher) {
                val profiles = if (grades.usesMockData()) emptyList() else grades.cachedProfiles()
                Triple(profiles, grades.cachedPerProfileGrades(), profiles.firstOrNull()?.let { grades.cachedGpaRank(it.id) })
            }
            if (owner != (session.currentUser()?.xh to session.isLoggedIn())) return@launch
            perProfileGrades = cachedGrades
            studentProfiles = if (cachedGrades.isNotEmpty() || cachedProfiles.size <= 1) cachedProfiles else emptyList()
            gpaRankInfo = cachedRank
        }
        viewModelScope.launch {
            presetCandidates = presets.rank(SemanticDomain.GRADE)
        }
    }

    private fun refreshTermAndYearGPA() {
        val g = grade
        if (g == null) {
            termGradePointAverage = "暂无"
            return
        }
        if (schoolYear == null || schoolTerm == null) return
        termGradePointAverage = g.termGradeList
            ?.find { it.schoolYear == schoolYear && it.term == schoolTerm }
            ?.termGradePointAverage
            ?: "暂无"
    }

    private fun selectedTerm(): GradeTermKey? {
        val year = schoolYear ?: return null
        val term = schoolTerm ?: return null
        return GradeTermKey(year, term)
    }

    private fun selectTermAfterLoad(
        previousTerm: GradeTermKey?,
        preserveSelectedTerm: Boolean
    ) {
        val calendarCurrent = GradeTermSelectionPolicy.currentForDate(DebugClock.nowLocalDate())
        val cachedCurrent = weekConfig.cachedSemesterKey()?.let {
            GradeTermKey(it.schoolYear, it.schoolTerm)
        }
        val available = grade?.termGradeList.orEmpty().mapNotNull { termGrade ->
            val year = termGrade.schoolYear?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val term = termGrade.term?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GradeTermKey(year, term)
        }
        val selected = GradeTermSelectionPolicy.choose(
            current = calendarCurrent,
            cached = cachedCurrent,
            previous = previousTerm,
            preservePrevious = preserveSelectedTerm,
            available = available
        )
        schoolYear = selected.schoolYear
        schoolTerm = selected.schoolTerm
        Log.i(
            tag,
            "grade term selected=${selected.schoolYear}-${selected.schoolTerm} " +
                "availableTerms=${available.size} preservePrevious=$preserveSelectedTerm"
        )
    }

    override fun onCleared() {
        onPresetSurfaceDisposed()
        super.onCleared()
    }

}

private data class GradePresetPayload(val profileIndex: Int, val schoolYear: String, val term: String)
private data class GradeCoarsePreset(val termCategory: String, val profileCategory: String)
