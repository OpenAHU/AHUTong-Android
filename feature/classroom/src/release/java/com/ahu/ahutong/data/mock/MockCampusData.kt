package com.ahu.ahutong.data.mock

import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem

object MockCampusData {
    fun buildings(campusId: Int): List<GetBuildingsResponseItem> = emptyList()

    fun freeRooms(
        campusId: Int,
        buildingIds: List<Int>,
    ): List<FreeRoom> = emptyList()
}
