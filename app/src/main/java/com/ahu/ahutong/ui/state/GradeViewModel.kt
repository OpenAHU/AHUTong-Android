package com.ahu.ahutong.ui.state

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.data.debug.DebugClock
import com.ahu.ahutong.data.schedule.CurrentWeekResolver
import com.ahu.ahutong.ext.getSchoolYears
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class GradeViewModel @Inject constructor(
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {
    private val tag = "GradeViewModel"

    var totalGradePointAverage by mutableStateOf("暂无")
    var termGradePointAverage by mutableStateOf("暂无")
    var grade by mutableStateOf<Grade?>(null)
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

    /** 每个 profile ID → Grade（null = 该专业无成绩） */
    private var perProfileGrades: Map<String, Grade?> = emptyMap()

    fun getGpaRank() = viewModelScope.launch {
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
            val result = AHURepository.getGpaRankInfo(studentId)
            Log.i(
                tag,
                "getGpaRank response code=${result.code} hasData=${result.data != null} " +
                    "msg=${result.msg.orEmpty().take(120)}"
            )
            if (result.code == 0 && result.data != null) {
                gpaRankInfo = result.data
                AHUCache.saveGpaRankInfo(studentId, result.data)
            } else {
                gpaRankInfo = null
                rankEmptyMessage = "「${profile.displayName}」暂无排名信息"
                Log.w(tag, "getGpaRank returned no rank data code=${result.code}")
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
        if (isLoading) return
        val previousTerm = selectedTerm()
        isLoading = true
        try {
            val result = AHURepository.getGrade(isRefresh)
            if (result.isSuccess) {
                grade = result.getOrNull()
                // 加载由 CrawlerDataSource 写入的 per-profile 缓存
                perProfileGrades = AHUCache.getPerProfileGrades()
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
                behaviorRuntime.onContentStateChanged(
                    SemanticDomain.GRADE,
                    if (count == 0) ContentStateBucket.EMPTY else ContentStateBucket.READY,
                    freshnessBucket = if (isRefresh) 0 else 1,
                    resultCount = resultCountBucket(count)
                )
                if (profiles.isNotEmpty()) {
                    getGpaRank()
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "获取成绩失败"
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
        if (profiles.isEmpty() && !AHUCache.getMockData()) {
            profiles = AHUCache.getGradeStudentProfiles()
            Log.i(tag, "resolveRankProfiles cache size=${profiles.size}")
        }
        if (profiles.isEmpty() && !AHUCache.getMockData()) {
            profiles = AHURepository.getGradeStudentProfiles()
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
        val applied = behaviorRuntime.applyLocalPreset(candidate) ?: return@launch
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
        behaviorRuntime.recordNaturalPresetSubmission(
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
        presetCandidates = behaviorRuntime.rankLocalPresets(SemanticDomain.GRADE)
    }

    fun onPresetCandidateVisible(candidate: PresetCandidate) = viewModelScope.launch {
        val token = behaviorRuntime.markPresetRecommendationExposed(candidate) ?: return@launch
        activePresetInteraction = token
        candidatesAtOpportunity = presetCandidates
    }

    fun onPresetSurfaceDisposed() {
        behaviorRuntime.expirePresetInteractionAsync(activePresetInteraction)
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
    }

    private fun reportGradeError() {
        behaviorRuntime.onContentStateChanged(
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
        val schoolYears: List<String> by lazy {
            AHUCache.getCurrentUser()?.getSchoolYears()?.toList()
                ?: if (AHUCache.getMockData()) {
                    listOf("2024-2025", "2023-2024", "2022-2023")
                } else {
                    throw IllegalStateException("未登录，无法打开成绩界面！")
                }
        }
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

        val cachedProfiles = if (AHUCache.getMockData()) emptyList() else AHUCache.getGradeStudentProfiles()
        perProfileGrades = AHUCache.getPerProfileGrades()
        studentProfiles = if (perProfileGrades.isNotEmpty() || cachedProfiles.size <= 1) {
            cachedProfiles
        } else {
            emptyList()
        }
        // 加载第一个专业的缓存排名
        cachedProfiles.firstOrNull()?.let {
            gpaRankInfo = AHUCache.getGpaRankInfo(it.id)
        }
        viewModelScope.launch {
            presetCandidates = behaviorRuntime.rankLocalPresets(SemanticDomain.GRADE)
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
        val cachedCurrent = CurrentWeekResolver.getCachedSemesterKey()?.let {
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
