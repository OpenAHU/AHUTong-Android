package com.ahu.ahutong.testing

import com.ahu.ahutong.core.storage.ElectricityAlertSettings
import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import kotlinx.coroutines.flow.MutableStateFlow

class FakeElectricityAlertSettings : ElectricityAlertSettings {
    override val configuration = MutableStateFlow(ElectricityAlertConfiguration())

    override suspend fun setEnabled(enabled: Boolean) {
        configuration.value = configuration.value.copy(enabled = enabled)
    }

    override suspend fun saveRoom(room: ElectricityAlertRoom) {
        val current = configuration.value
        configuration.value = current.copy(
            rooms = current.rooms.filterNot { it.key == room.key } + room
        )
    }

    override suspend fun removeRoom(key: String) {
        val current = configuration.value
        configuration.value = current.copy(rooms = current.rooms.filterNot { it.key == key })
    }
}
