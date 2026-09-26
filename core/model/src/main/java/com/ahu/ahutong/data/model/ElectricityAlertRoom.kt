package com.ahu.ahutong.data.model

data class ElectricityAlertRoom(
    val selection: RoomSelectionInfo,
    val thresholdDays: Int = 3
) {
    val key: String
        get() = listOf(
            (selection.controller ?: ElectricityController.C).name,
            selection.campus?.value.orEmpty(),
            selection.building?.value.orEmpty(),
            selection.floor?.value.orEmpty(),
            selection.room?.value.orEmpty()
        ).joinToString("|")

    val label: String
        get() = listOfNotNull(
            (selection.controller ?: ElectricityController.C).displayName,
            selection.campus?.name,
            selection.building?.name,
            selection.room?.name
        ).filter(String::isNotBlank).joinToString(" · ")
}

data class ElectricityAlertConfiguration(
    val enabled: Boolean = false,
    val rooms: List<ElectricityAlertRoom> = emptyList()
)
