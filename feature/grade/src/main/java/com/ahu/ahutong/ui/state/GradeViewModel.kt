package com.ahu.ahutong.ui.state

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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class GradeViewModel @Inject constructor(
    private val gradeRepository: GradeRepository,
    private val gradeLocalStore: GradeLocalStore,
) : ViewModel() {
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
                    rankEmptyMessage = "「${profile.displayName}」暂无排名信息"
                    Log.w("GradeViewModel", "getGpaRank empty: ${result.message}")
                }
            }
        } catch (t: Throwable) {
            gpaRankInfo = null
            rankEmptyMessage = "获取排名失败：${t.message}"
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
            errorMessage = t.message ?: "获取成绩失败"
        } finally {
            isLoading = false
        }
    }

    private fun switchToSelectedProfile() {
        val profile = studentProfiles.getOrNull(selectedProfileIndex)
        val profileGrade = profile?.let { perProfileGrades[it.id] }
        grade = profileGrade
        if (profileGrade == null) {
            termGradePointAverage = "暂无"
            totalGradePointAverage = "暂无"
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
            termGradePointAverage = "暂无"
            return
        }
        if (schoolYear == null || schoolTerm == null) return
        termGradePointAverage = g.termGradeList
            ?.find { it.schoolYear == schoolYear && it.term == schoolTerm }
            ?.termGradePointAverage
            ?: "暂无"
    }
}
