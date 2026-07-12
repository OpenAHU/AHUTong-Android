package com.ahu.ahutong.data.mock

import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object MockCampusData {
    private val gson = Gson()

    fun buildings(campusId: Int): List<GetBuildingsResponseItem> {
        val override = MockOverrideStore.get(MockEditableEndpoint.ClassroomBuildings)?.let { raw ->
            runCatching {
                gson.fromJson<Map<Int, List<GetBuildingsResponseItem>>>(
                    raw,
                    object : TypeToken<Map<Int, List<GetBuildingsResponseItem>>>() {}.type,
                )
            }.getOrNull()
        }
        return (override ?: MockScenarioController.activeScenario().campus.classroomBuildings)[campusId]
            .orEmpty()
    }

    fun freeRooms(
        campusId: Int,
        buildingIds: List<Int>,
    ): List<FreeRoom> {
        val override = MockOverrideStore.get(MockEditableEndpoint.ClassroomRooms)?.let { raw ->
            runCatching {
                gson.fromJson<Map<Int, List<FreeRoom>>>(
                    raw,
                    object : TypeToken<Map<Int, List<FreeRoom>>>() {}.type,
                )
            }.getOrNull()
        }
        return (override ?: MockScenarioController.activeScenario().campus.classroomRooms)[campusId]
            .orEmpty()
            .filter { buildingIds.isEmpty() || it.building.id in buildingIds }
    }
}
