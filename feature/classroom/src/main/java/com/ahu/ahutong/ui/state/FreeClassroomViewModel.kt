package com.ahu.ahutong.ui.state

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.classroom.FreeClassroomSource
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.feature.classroom.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@HiltViewModel
class FreeClassroomViewModel @Inject constructor(
    private val freeClassroomSource: FreeClassroomSource,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val campusOptions = listOf(
        CampusOption(id = 1, name = context.getString(R.string.campus_qingyuan)),
        CampusOption(id = 2, name = context.getString(R.string.campus_longhe))
    )
    val selectedCampusId = MutableStateFlow<Int?>(null)
    val buildings = MutableStateFlow<List<GetBuildingsResponseItem>>(emptyList())
    val selectedBuildingIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedUnits = MutableStateFlow<Set<Int>>(emptySet())
    val startDate = MutableStateFlow(LocalDate.now())
    val endDate = MutableStateFlow(LocalDate.now())
    val isLoadingBuildings = MutableStateFlow(false)
    val isSearching = MutableStateFlow(false)
    val freeRooms = MutableStateFlow<List<FreeRoom>>(emptyList())
    val errorMessage = MutableStateFlow<String?>(null)
    private val buildingsCache = mutableMapOf<Int, List<GetBuildingsResponseItem>>()

    init {
        selectCampus(1)
    }

    fun isMockMode(): Boolean = freeClassroomSource.isMockMode()

    fun selectCampus(campusId: Int) = viewModelScope.launchSafe {
        if (selectedCampusId.value == campusId) return@launchSafe
        selectedCampusId.value = campusId
        selectedBuildingIds.value = emptySet()
        freeRooms.value = emptyList()
        loadBuildings(campusId)
    }

    fun refreshMockData() = viewModelScope.launchSafe {
        if (!freeClassroomSource.isMockMode()) return@launchSafe
        buildingsCache.clear()
        selectedCampusId.value?.let { campusId ->
            loadBuildings(campusId)
            if (freeRooms.value.isNotEmpty()) {
                searchFreeRooms()
            }
        }
    }

    fun toggleBuilding(buildingId: Int) {
        selectedBuildingIds.value = selectedBuildingIds.value.toMutableSet().apply {
            if (contains(buildingId)) remove(buildingId) else add(buildingId)
        }
    }

    fun toggleUnit(unit: Int) {
        selectedUnits.value = selectedUnits.value.toMutableSet().apply {
            if (contains(unit)) remove(unit) else add(unit)
        }
    }

    fun toggleUnitsRange(start: Int, end: Int) {
        val range = (start..end).toSet()
        val current = selectedUnits.value
        selectedUnits.value = if (range.all { it in current }) current - range else current + range
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
            errorMessage.value = context.getString(R.string.select_campus_first)
            return@launchSafe
        }
        val allBuildings = buildings.value
        if (allBuildings.isEmpty()) {
            errorMessage.value = context.getString(R.string.no_buildings_data)
            return@launchSafe
        }
        val buildingIds = if (selectedBuildingIds.value.isEmpty()) {
            allBuildings.map { it.id }
        } else {
            selectedBuildingIds.value.toList()
        }
        val units = if (selectedUnits.value.isEmpty()) {
            (1..13).map { it.toString() }
        } else {
            selectedUnits.value.sorted().map { it.toString() }
        }
        val start = startDate.value.toString()
        val end = endDate.value.toString()
        isSearching.value = true
        errorMessage.value = null
        runCatching {
            val allRooms = freeClassroomSource.getFreeRooms(
                campusId = campusId,
                buildingIds = buildingIds,
                startDate = start,
                endDate = end,
                units = units,
            )
            freeRooms.value = allRooms
                .distinctBy { "${it.id}-${it.building.id}" }
                .sortedWith(compareBy({ it.building.nameZh }, { it.floor }, { it.nameZh }))
        }.onFailure {
            errorMessage.value = it.message ?: context.getString(R.string.search_failed)
        }
        isSearching.value = false
    }

    private suspend fun loadBuildings(campusId: Int) {
        if (!freeClassroomSource.isMockMode() && buildingsCache.containsKey(campusId)) {
            buildings.value = buildingsCache[campusId] ?: emptyList()
            return
        }
        isLoadingBuildings.value = true
        runCatching {
            val data = freeClassroomSource.getBuildings(campusId)
            val sortedData = data.sortedBy { it.nameZh }
            buildingsCache[campusId] = sortedData
            buildings.value = sortedData
        }.onFailure {
            buildings.value = emptyList()
            errorMessage.value = it.message
                ?: context.getString(R.string.load_buildings_failed)
        }
        isLoadingBuildings.value = false
    }
}

data class CampusOption(
    val id: Int,
    val name: String
)
