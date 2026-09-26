package com.ahu.ahutong.core.storage

import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import kotlinx.coroutines.flow.Flow

/** 当前账号的电费预警配置；切换账号时配置流同步更新。 */
interface ElectricityAlertSettings {
    val configuration: Flow<ElectricityAlertConfiguration>

    suspend fun setEnabled(enabled: Boolean)
    suspend fun saveRoom(room: ElectricityAlertRoom)
    suspend fun removeRoom(key: String)
}
