package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.core.common.toUserMessage
import com.ahu.ahutong.core.designsystem.RefreshState
import com.ahu.ahutong.data.status.ServiceStatusGateway
import com.ahu.ahutong.data.status.ServiceStatusSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ServiceStatusViewModel @Inject constructor(
    private val gateway: ServiceStatusGateway
) : ViewModel() {

    sealed interface UiState {
        data object Loading : UiState

        data class Ready(
            val snapshot: ServiceStatusSnapshot,
            val refreshState: RefreshState = RefreshState.IDLE,
            val refreshError: String? = null,
            val updatedAtLabel: String? = null
        ) : UiState

        data class Failed(val message: String) : UiState
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    fun retry() {
        loadInitial()
    }

    fun refresh() {
        val current = _uiState.value
        if (current !is UiState.Ready) {
            loadInitial()
            return
        }
        if (current.refreshState == RefreshState.LOADING) return

        _uiState.value = current.copy(
            refreshState = RefreshState.LOADING,
            refreshError = null
        )
        viewModelScope.launch {
            when (val result = gateway.fetchStatus()) {
                is AhuResult.Success -> {
                    _uiState.value = UiState.Ready(
                        snapshot = result.value,
                        refreshState = RefreshState.UPDATED,
                        refreshError = null,
                        updatedAtLabel = resolveLatestCheckLabel(result.value)
                    )
                    delay(UPDATED_BADGE_HOLD_MS)
                    val afterHold = _uiState.value
                    if (afterHold is UiState.Ready && afterHold.refreshState == RefreshState.UPDATED) {
                        _uiState.value = afterHold.copy(refreshState = RefreshState.IDLE)
                    }
                }
                is AhuResult.Failure -> {
                    _uiState.value = current.copy(
                        refreshState = RefreshState.IDLE,
                        refreshError = result.error.toUserMessage()
                    )
                }
            }
        }
    }

    private fun loadInitial() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val result = gateway.fetchStatus()) {
                is AhuResult.Success -> UiState.Ready(
                    snapshot = result.value,
                    updatedAtLabel = resolveLatestCheckLabel(result.value)
                )
                is AhuResult.Failure -> UiState.Failed(result.error.toUserMessage())
            }
        }
    }

    internal companion object {
        private const val UPDATED_BADGE_HOLD_MS = 1200L

        private val UTC_DATETIME_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
        private val UTC_DATETIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        private val LOCAL_TIME_SHORT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        fun resolveLatestCheckLabel(
            snapshot: ServiceStatusSnapshot,
            targetZone: ZoneId = ZoneId.systemDefault()
        ): String? {
            val latestRaw = snapshot.allMonitors
                .mapNotNull { it.lastCheckTime?.trim()?.takeIf(String::isNotEmpty) }
                .maxOrNull()
                ?: return null
            return formatUtcTimestampToLocal(latestRaw, targetZone)
        }

        fun formatUtcTimestampToLocal(
            raw: String,
            targetZone: ZoneId = ZoneId.systemDefault()
        ): String {
            val instant = parseUtcInstant(raw) ?: return raw
            return LOCAL_TIME_SHORT.format(instant.atZone(targetZone))
        }

        private fun parseUtcInstant(raw: String): Instant? {
            runCatching { return Instant.parse(raw) }
            runCatching {
                return LocalDateTime.parse(raw, UTC_DATETIME_MILLIS).toInstant(ZoneOffset.UTC)
            }
            runCatching {
                return LocalDateTime.parse(raw, UTC_DATETIME_SECONDS).toInstant(ZoneOffset.UTC)
            }
            return null
        }

        fun formatPercent(ratio: Double): String {
            val roundedTenths = kotlin.math.round((ratio * 100.0).coerceIn(0.0, 100.0) * 10.0) / 10.0
            return if (roundedTenths % 1.0 == 0.0) {
                "${roundedTenths.toInt()}%"
            } else {
                String.format(Locale.US, "%.1f%%", roundedTenths)
            }
        }
    }
}
