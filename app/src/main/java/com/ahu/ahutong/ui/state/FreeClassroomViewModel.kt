package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.UserNoticeHolder
import kotlinx.coroutines.CancellationException

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.data.FreeClassroomGateway
import com.ahu.ahutong.data.crawler.model.jwxt.DateTimeSegmentCmd
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsRequest
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockCampusData
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class FreeClassroomViewModel @Inject constructor(
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {
    val campusOptions = listOf(
        CampusOption(id = 1, name = "磬苑校区"),
        CampusOption(id = 2, name = "龙河校区")
    )
    val selectedCampusId = MutableStateFlow<Int?>(null)
    val buildings = MutableStateFlow<List<GetBuildingsResponseItem>>(emptyList())
    val selectedBuildingIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedUnits = MutableStateFlow<Set<Int>>(emptySet())
    val startDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val endDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val isLoadingBuildings = MutableStateFlow(false)
    val isSearching = MutableStateFlow(false)
    val hasSearched = MutableStateFlow(false)
    val freeRooms = MutableStateFlow<List<FreeRoom>>(emptyList())
    val errorMessage = MutableStateFlow<String?>(null)
    val presetCandidates = MutableStateFlow<List<PresetCandidate>>(emptyList())
    private var activePresetInteraction: PresetInteractionToken? = null
    private var candidatesAtOpportunity: List<PresetCandidate> = emptyList()
    private val buildingsCache = mutableMapOf<Int, List<GetBuildingsResponseItem>>()

    init {
        selectCampus(1)
        viewModelScope.launchSafe {
            presetCandidates.value = behaviorRuntime.rankLocalPresets(SemanticDomain.FREE_CLASSROOM)
        }
    }

    fun selectCampus(campusId: Int) = viewModelScope.launchSafe {
        if (selectedCampusId.value == campusId) return@launchSafe
        selectedCampusId.value = campusId
        selectedBuildingIds.value = emptySet()
        freeRooms.value = emptyList()
        hasSearched.value = false
        errorMessage.value = null
        loadBuildings(campusId)
    }

    fun refreshMockData() = viewModelScope.launchSafe {
        if (!AHUCache.getMockData()) return@launchSafe
        buildingsCache.clear()
        selectedCampusId.value?.let { campusId ->
            loadBuildings(campusId)
            if (freeRooms.value.isNotEmpty()) {
                searchFreeRooms()
            }
        }
    }

    fun toggleBuilding(buildingId: Int) {
        errorMessage.value = null
        selectedBuildingIds.value = selectedBuildingIds.value.toMutableSet().apply {
            if (contains(buildingId)) remove(buildingId) else add(buildingId)
        }
    }

    fun selectBuilding(buildingId: Int?) {
        selectedBuildingIds.value = buildingId?.let(::setOf).orEmpty()
        errorMessage.value = null
    }

    fun toggleUnit(unit: Int) {
        errorMessage.value = null
        selectedUnits.value = selectedUnits.value.toMutableSet().apply {
            if (contains(unit)) remove(unit) else add(unit)
        }
    }

    fun toggleUnitsRange(start: Int, end: Int) {
        errorMessage.value = null
        val range = (start..end).toSet()
        val current = selectedUnits.value
        selectedUnits.value = if (range.all { it in current }) current - range else current + range
    }

    fun selectAllBuildings() {
        selectedBuildingIds.value = emptySet()
        errorMessage.value = null
    }

    fun selectAllUnits() {
        selectedUnits.value = emptySet()
        errorMessage.value = null
    }

    fun selectUnitRange(range: IntRange) {
        selectedUnits.value = range.filter { it in 1..13 }.toSet()
        errorMessage.value = null
    }

    fun clearError() {
        errorMessage.value = null
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        startDate.value = start
        endDate.value = end
    }

    fun setStartDate(date: LocalDate) {
        startDate.value = date
        if (endDate.value.isBefore(date)) {
            endDate.value = date
        }
    }

    fun setEndDate(date: LocalDate) {
        endDate.value = date
        if (startDate.value.isAfter(date)) {
            startDate.value = date
        }
    }

    fun searchFreeRooms() = viewModelScope.launchSafe {
        val campusId = selectedCampusId.value ?: run {
            errorMessage.value = "请先选择校区"
            return@launchSafe
        }
        val allBuildings = buildings.value
        if (allBuildings.isEmpty()) {
            errorMessage.value = "当前校区暂无教学楼数据"
            return@launchSafe
        }
        val selectedBuildings = selectedBuildingIds.value
        val buildingQueries = freeClassroomBuildingQueries(selectedBuildings)
        val units = freeClassroomUnits(selectedUnits.value)
        val start = startDate.value.toString()
        val end = endDate.value.toString()
        isSearching.value = true
        hasSearched.value = true
        errorMessage.value = null
        recordDispatchedPreset(campusId, selectedBuildings.toList(), units)
        runCatching {
            val allRooms = if (AHUCache.getMockData()) {
                val mockBuildingIds = selectedBuildings.ifEmpty {
                    allBuildings.mapTo(mutableSetOf()) { it.id }
                }
                MockCampusData.freeRooms(campusId, mockBuildingIds.toList())
            } else {
                buildingQueries.flatMap { buildingId ->
                    val response = FreeClassroomGateway.freeRooms(
                        GetFreeRoomsRequest(
                            buildingId = buildingId,
                            campusId = campusId.toString(),
                            dateTimeSegmentCmd = DateTimeSegmentCmd(
                                startDateTime = start,
                                endDateTime = end,
                                units = units
                            )
                        )
                    )
                    response.roomList
                }
            }
            freeRooms.value = allRooms
                .distinctBy { "${it.id}-${it.building.id}" }
                .sortedWith(compareBy({ it.building.nameZh }, { it.floor }, { it.nameZh }))
            behaviorRuntime.onContentStateChanged(
                SemanticDomain.FREE_CLASSROOM,
                if (freeRooms.value.isEmpty()) ContentStateBucket.EMPTY else ContentStateBucket.READY,
                freshnessBucket = 0,
                resultCount = resultCountBucket(freeRooms.value.size)
            )
        }.onFailure {
            if (it is CancellationException) throw it
            errorMessage.value = if (UserNoticeHolder.reportUnexpected(it)) null else it.message ?: "查询失败"
            behaviorRuntime.onContentStateChanged(
                SemanticDomain.FREE_CLASSROOM,
                ContentStateBucket.ERROR,
                freshnessBucket = 7,
                resultCount = ResultCountBucket.ZERO,
                errorType = ErrorTypeBucket.NETWORK
            )
        }
        isSearching.value = false
    }

    fun applyPresetCandidate(candidate: PresetCandidate) = viewModelScope.launchSafe {
        val applied = behaviorRuntime.applyLocalPreset(candidate) ?: return@launchSafe
        activePresetInteraction = applied.interactionToken
        candidatesAtOpportunity = presetCandidates.value
        val decoded = runCatching { Gson().fromJson(applied.localPayloadJson, FreeClassroomPresetPayload::class.java) }.getOrNull()
            ?: return@launchSafe
        if (decoded.campusId !in campusOptions.map(CampusOption::id)) return@launchSafe
        selectedCampusId.value = decoded.campusId
        selectedBuildingIds.value = emptySet()
        loadBuildings(decoded.campusId)
        selectedBuildingIds.value = decoded.buildingIds
            .firstOrNull { candidate -> buildings.value.any { it.id == candidate } }
            ?.let(::setOf)
            .orEmpty()
        selectedUnits.value = decoded.units.toSet().filter { it in 1..13 }.toSet()
        val start = runCatching { LocalDate.parse(decoded.startDate) }.getOrNull() ?: return@launchSafe
        val end = runCatching { LocalDate.parse(decoded.endDate) }.getOrNull() ?: return@launchSafe
        setDateRange(start, end.coerceAtLeast(start))
        presetCandidates.value = emptyList()
    }

    fun onPresetCandidateVisible(candidate: PresetCandidate) = viewModelScope.launchSafe {
        val token = behaviorRuntime.markPresetRecommendationExposed(candidate) ?: return@launchSafe
        activePresetInteraction = token
        candidatesAtOpportunity = presetCandidates.value
    }

    fun onPresetSurfaceDisposed() {
        behaviorRuntime.expirePresetInteractionAsync(activePresetInteraction)
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
    }

    private suspend fun recordDispatchedPreset(campusId: Int, buildingIds: List<Int>, units: List<String>) {
        val payload = FreeClassroomPresetPayload(
            campusId,
            buildingIds.sorted(),
            units.mapNotNull(String::toIntOrNull).sorted(),
            startDate.value.toString(),
            endDate.value.toString()
        )
        val coarse = FreeClassroomCoarsePreset(
            campusCategory = if (campusId == 1) "CAMPUS_PRIMARY" else "CAMPUS_SECONDARY",
            buildingCountBucket = when (buildingIds.size) { 0 -> "ALL"; 1 -> "ONE"; in 2..4 -> "TWO_TO_FOUR"; else -> "FIVE_PLUS" },
            timeSegment = unitBucket(units.mapNotNull(String::toIntOrNull)),
            dateRange = dateBucket(startDate.value, endDate.value),
            resultCount = ResultCountBucket.UNKNOWN.name
        )
        behaviorRuntime.recordNaturalPresetSubmission(
            PresetSubmission(
                SemanticDomain.FREE_CLASSROOM,
                Gson().toJson(payload),
                Gson().toJson(coarse),
                "${payload.campusId}|${payload.buildingIds.joinToString(",")}|${payload.units.joinToString(",")}|${payload.startDate}|${payload.endDate}"
            ),
            interactionToken = activePresetInteraction,
            candidatesAtOpportunity = candidatesAtOpportunity.ifEmpty { presetCandidates.value }
        )
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
        presetCandidates.value = behaviorRuntime.rankLocalPresets(SemanticDomain.FREE_CLASSROOM)
    }

    override fun onCleared() {
        onPresetSurfaceDisposed()
        super.onCleared()
    }

    private fun resultCountBucket(count: Int): ResultCountBucket = when (count) {
        0 -> ResultCountBucket.ZERO
        in 1..5 -> ResultCountBucket.ONE_TO_FIVE
        in 6..20 -> ResultCountBucket.SIX_TO_TWENTY
        else -> ResultCountBucket.TWENTY_ONE_PLUS
    }

    private fun unitBucket(units: List<Int>): String = when {
        units.isEmpty() || units.size == 13 -> "ALL_DAY"
        units.all { it in 1..5 } -> "MORNING"
        units.all { it in 6..10 } -> "AFTERNOON"
        units.all { it in 11..13 } -> "EVENING"
        else -> "CUSTOM"
    }

    private fun dateBucket(start: LocalDate, end: LocalDate): String = when {
        start == LocalDate.now() && end == start -> "TODAY"
        start == LocalDate.now().plusDays(1) && end == start -> "TOMORROW"
        !end.isAfter(start.plusDays(7)) -> "WITHIN_SEVEN_DAYS"
        else -> "CUSTOM_RANGE"
    }

    private suspend fun loadBuildings(campusId: Int) {
        if (!AHUCache.getMockData() && buildingsCache.containsKey(campusId)) {
            buildings.value = buildingsCache[campusId] ?: emptyList()
            return
        }
        isLoadingBuildings.value = true
        runCatching {
            val data = if (AHUCache.getMockData()) {
                MockCampusData.buildings(campusId)
            } else {
                FreeClassroomGateway.buildings(campusId = campusId)
            }
            val sortedData = data.sortedBy { it.nameZh }
            buildingsCache[campusId] = sortedData
            buildings.value = sortedData
        }.onFailure {
            if (it is CancellationException) throw it
            buildings.value = emptyList()
            errorMessage.value = if (UserNoticeHolder.reportUnexpected(it)) null else it.message ?: "获取教学楼失败"
        }
        isLoadingBuildings.value = false
    }
}

internal fun freeClassroomBuildingQueries(selectedBuildingIds: Set<Int>): List<String> =
    selectedBuildingIds.sorted().map(Int::toString).ifEmpty { listOf("") }

internal fun freeClassroomUnits(selectedUnits: Set<Int>): List<String> =
    selectedUnits.filter { it in 1..13 }.sorted().map(Int::toString)
        .ifEmpty { (1..13).map(Int::toString) }

data class CampusOption(
    val id: Int,
    val name: String
)

private data class FreeClassroomPresetPayload(
    val campusId: Int,
    val buildingIds: List<Int>,
    val units: List<Int>,
    val startDate: String,
    val endDate: String
)

private data class FreeClassroomCoarsePreset(
    val campusCategory: String,
    val buildingCountBucket: String,
    val timeSegment: String,
    val dateRange: String,
    val resultCount: String
)
