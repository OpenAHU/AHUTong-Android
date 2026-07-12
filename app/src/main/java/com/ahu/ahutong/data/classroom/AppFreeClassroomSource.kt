package com.ahu.ahutong.data.classroom

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.DateTimeSegmentCmd
import com.ahu.ahutong.data.crawler.model.jwxt.FreeRoom
import com.ahu.ahutong.data.crawler.model.jwxt.GetBuildingsResponseItem
import com.ahu.ahutong.data.crawler.model.jwxt.GetFreeRoomsRequest
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.mock.MockCampusData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppFreeClassroomSource @Inject constructor() : FreeClassroomSource {
    override fun isMockMode(): Boolean = AHUCache.getMockData()

    override suspend fun getBuildings(campusId: Int): List<GetBuildingsResponseItem> {
        return if (isMockMode()) {
            MockCampusData.buildings(campusId)
        } else {
            JwxtApi.API.getBuildings(campusId = campusId)
        }
    }

    override suspend fun getFreeRooms(
        campusId: Int,
        buildingIds: List<Int>,
        startDate: String,
        endDate: String,
        units: List<String>,
    ): List<FreeRoom> {
        if (isMockMode()) {
            return MockCampusData.freeRooms(campusId, buildingIds)
        }
        val remoteRooms = mutableListOf<FreeRoom>()
        buildingIds.forEach { buildingId ->
            val response = JwxtApi.API.getFreeRooms(
                GetFreeRoomsRequest(
                    buildingId = buildingId.toString(),
                    campusId = campusId.toString(),
                    dateTimeSegmentCmd = DateTimeSegmentCmd(
                        startDateTime = startDate,
                        endDateTime = endDate,
                        units = units,
                    ),
                ),
            )
            remoteRooms += response.roomList
        }
        return remoteRooms
    }
}
