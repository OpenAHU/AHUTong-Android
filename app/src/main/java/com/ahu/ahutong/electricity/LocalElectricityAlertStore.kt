package com.ahu.ahutong.electricity

import android.content.Context
import com.ahu.ahutong.core.storage.ElectricityAlertSettings
import com.ahu.ahutong.data.model.ElectricityAlertConfiguration
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LocalElectricityAlertStore internal constructor(internal val files: ElectricityAlertFiles) :
    ElectricityAlertSettings {
    @Inject constructor(@ApplicationContext context: Context) :
        this(ElectricityAlertFiles(File(context.filesDir, "electricity-alerts")))
    private val mutex = Mutex()
    private var activeProfile: String? = null
    private val _configuration = MutableStateFlow(ElectricityAlertConfiguration())
    override val configuration: StateFlow<ElectricityAlertConfiguration> = _configuration.asStateFlow()

    suspend fun activateProfile(profile: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (activeProfile == profile) return@withLock
            activeProfile = profile
            _configuration.value = profile?.let(files::configuration) ?: ElectricityAlertConfiguration()
        }
    }

    override suspend fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    override suspend fun saveRoom(room: ElectricityAlertRoom) {
        require(room.thresholdDays in 1..30)
        update { config ->
            config.copy(rooms = config.rooms.filterNot { it.key == room.key } + room)
        }
    }

    override suspend fun removeRoom(key: String) = update { config ->
        config.copy(rooms = config.rooms.filterNot { it.key == key })
    }

    private suspend fun update(transform: (ElectricityAlertConfiguration) -> ElectricityAlertConfiguration) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val profile = activeProfile ?: error("请先登录后设置电费预警")
                val updated = transform(_configuration.value)
                files.saveConfiguration(profile, updated)
                _configuration.value = updated
            }
        }
}
