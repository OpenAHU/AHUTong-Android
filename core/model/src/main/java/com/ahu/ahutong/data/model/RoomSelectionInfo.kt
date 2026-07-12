package com.ahu.ahutong.data.model

import java.io.Serializable

data class RoomSelectionInfo(
    val campus: CampusDataItem?,
    val building: CampusDataItem?,
    val floor: CampusDataItem?,
    val room: CampusDataItem?,
) : Serializable