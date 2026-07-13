package com.ahu.ahutong.ui.state

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.grade.GradeLocalStore
import com.ahu.ahutong.data.grade.GradeRepository
import com.ahu.ahutong.data.model.GpaRankInfo
import com.ahu.ahutong.data.model.Grade
import com.ahu.ahutong.data.model.GradeStudentProfile
import com.ahu.ahutong.feature.grade.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class GradeViewModel @Inject constructor(
    private val gradeRepository: GradeRepository,
    private val gradeLocalStore: GradeLocalStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    var totalGradePointAverage by mutableStateOf(appContext.getString(R.string.not_available))
    var termGradePointAverage by mutableStateOf(appContext.getString(R.string.not_available))
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

    /** 每个 profile ID → Grade（null = 该专业无成绩） */
    private var perProfileGrades: Map<String, Grade?> = emptyMap()

    fun isMockMode(): Boolean = gradeLocalStore.isMockMode()

    fun getGpaRank() = viewModelScope.launch {
        rankLoading = true
        rankEmptyMessage = null
        try {
            val profile = studentProfiles.getOrNull(selectedProfileIndex)
            val studentId = profile?.id ?: return@launch
            when (val result = gradeRepository.getGpaRank(studentId)) {
                is AppResult.Success -> {
                    gpaRankInfo = result.data
                    gradeLocalStore.saveGpaRank(studentId, result.data)
                }
                is AppResult.Error -> {
                    gpaRankInfo = null
                    rankEmptyMessage = appContext.getString(
                        R.string.no_rank_for_profile,
                        profile.displayName,
                    )
                    Log.w("GradeViewModel", "getGpaRank empty: ${result.message}")
                }
            }
        } catch (t: Throwable) {
            gpaRankInfo = null
            rankEmptyMessage = appContext.getString(
                R.string.failed_to_get_rank,
                t.message ?: "",
            )
            Log.w("GradeViewModel", "getGpaRank failed", t)
        } finally {
            rankLoading = false
        }
    }

    fun getGarde(isRefresh: Boolean = false) = viewModelScope.launch {
        isLoading = true
        try {
            when (val result = gradeRepository.getGrade(isRefresh)) {
                is AppResult.Success -> {
                    perProfileGrades = gradeLocalStore.getPerProfileGrades()
                    if (perProfileGrades.isEmpty() && studentProfiles.size > 1) {
                        when (gradeRepository.getGrade(isRefresh = true)) {
                            is AppResult.Success -> {
                                perProfileGrades = gradeLocalStore.getPerProfileGrades()
                            }
                            is AppResult.Error -> Unit
                        }
                    }
                    switchToSelectedProfile()
                    errorMessage = null
                    studentProfiles = gradeLocalStore.getStudentProfiles()
                }
                is AppResult.Error -> {
                    errorMessage = result.message
                }
            }
        } catch (t: Throwable) {
            errorMessage = t.message ?: appContext.getString(R.string.failed_to_get_grades)
        } finally {
            isLoading = false
        }
    }

    private fun switchToSelectedProfile() {
        val profile = studentProfiles.getOrNull(selectedProfileIndex)
        val profileGrade = profile?.let { perProfileGrades[it.id] }
        grade = profileGrade
        if (profileGrade == null) {
            val na = appContext.getString(R.string.not_available)
            termGradePointAverage = na
            totalGradePointAverage = na
        }
        schoolYear = schoolYears.firstOrNull()
        schoolTerm = terms.keys.firstOrNull()
    }

    var isRefreshing by mutableStateOf(false)
        private set

    fun refreshGrade() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                getGarde(true)
                getGpaRank()
            } finally {
                isRefreshing = false
            }
        }
    }

    companion object {
        private val mockSchoolYears = listOf("2024-2025", "2023-2024", "2022-2023")

        /**
         * Resolved lazily via the injected store on first ViewModel creation path.
         * Fallback school years when store is unavailable are set in [resolveSchoolYears].
         */
        var schoolYears: List<String> = mockSchoolYears
            private set

        val terms = mutableMapOf("1" to "0", "2" to "1")

        fun resolveSchoolYears(store: GradeLocalStore): List<String> {
            schoolYears = store.getSchoolYears()
                ?: if (store.isMockMode()) {
                    mockSchoolYears
                } else {
                    throw IllegalStateException("未登录，无法打开成绩界面")
                }
            return schoolYears
        }
    }

    init {
        resolveSchoolYears(gradeLocalStore)
        schoolYear = schoolYears.firstOrNull()
        schoolTerm = terms.keys.firstOrNull()

        snapshotFlow { gpaRankInfo }
            .onEach { info ->
                totalGradePointAverage = info?.gpa?.let { "%.2f".format(it) }
                    ?: appContext.getString(R.string.not_available)
                refreshTermAndYearGPA()
            }
            .launchIn(viewModelScope)

        snapshotFlow { grade }
            .onEach { refreshTermAndYearGPA() }
            .launchIn(viewModelScope)

        snapshotFlow { schoolYear to schoolTerm }
            .onEach { refreshTermAndYearGPA() }
            .launchIn(viewModelScope)

        // 切换专业 → 清空旧排名 + 切成绩 + 重新获取排名
        snapshotFlow { selectedProfileIndex }
            .onEach {
                if (studentProfiles.isNotEmpty()) {
                    gpaRankInfo = null
                    rankEmptyMessage = null
                    switchToSelectedProfile()
                    getGpaRank()
                }
            }
            .launchIn(viewModelScope)

        studentProfiles =
            if (gradeLocalStore.isMockMode()) emptyList() else gradeLocalStore.getStudentProfiles()
        perProfileGrades = gradeLocalStore.getPerProfileGrades()
        studentProfiles.firstOrNull()?.let {
            gpaRankInfo = gradeLocalStore.getGpaRank(it.id)
        }
    }

    private fun refreshTermAndYearGPA() {
        val g = grade
        if (g == null) {
            termGradePointAverage = appContext.getString(R.string.not_available)
            return
        }
        if (schoolYear == null || schoolTerm == null) return
        termGradePointAverage = g.termGradeList
            ?.find { it.schoolYear == schoolYear && it.term == schoolTerm }
            ?.termGradePointAverage
            ?: appContext.getString(R.string.not_available)
    }
}
