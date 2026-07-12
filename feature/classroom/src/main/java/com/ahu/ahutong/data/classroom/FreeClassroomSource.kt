package com.ahu.ahutong.data.classroom

import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem

/**
 * Free-classroom data access. App binds JwxtApi + mock implementations.
 */
interface FreeClassroomSource {
    fun isMockMode(): Boolean

    suspend fun getBuildings(campusId: Int): List<GetBuildingsResponseItem>

    suspend fun getFreeRooms(
        campusId: Int,
        buildingIds: List<Int>,
        startDate: String,
        endDate: String,
        units: List<String>,
    ): List<FreeRoom>
}
