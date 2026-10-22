package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 政策内容变更时递增，已有用户需要重新同意。 */
internal const val CURRENT_PRIVACY_POLICY_VERSION = 3

sealed interface TelemetryOnboardingState {
    data object Loading : TelemetryOnboardingState
    data class Ready(val choice: Boolean?) : TelemetryOnboardingState
}

sealed interface BootstrapTrainingOnboardingState {
    data object Loading : BootstrapTrainingOnboardingState
    data class Ready(val choice: Boolean?) : BootstrapTrainingOnboardingState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {
    private val _privacyPolicyAccepted = MutableStateFlow<Boolean?>(null)
    val privacyPolicyAccepted: StateFlow<Boolean?> = _privacyPolicyAccepted.asStateFlow()

    private val _telemetryOnboardingState =
        MutableStateFlow<TelemetryOnboardingState>(TelemetryOnboardingState.Loading)
    val telemetryOnboardingState: StateFlow<TelemetryOnboardingState> =
        _telemetryOnboardingState.asStateFlow()
    private val _bootstrapTrainingOnboardingState =
        MutableStateFlow<BootstrapTrainingOnboardingState>(BootstrapTrainingOnboardingState.Loading)
    val bootstrapTrainingOnboardingState: StateFlow<BootstrapTrainingOnboardingState> =
        _bootstrapTrainingOnboardingState.asStateFlow()

    init {
        viewModelScope.launch {
            _privacyPolicyAccepted.value = withContext(Dispatchers.IO) {
                try {
                    AHUCache.isAgreementAccepted() &&
                        AHUCache.privacyPolicyVersion() >= CURRENT_PRIVACY_POLICY_VERSION &&
                        AHUCache.isBusinessAccepted()
                } catch (_: Exception) {
                    false
                }
            }
            _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(
                preferencesManager.modelQualityTelemetryOnboardingChoice.first()
            )
            _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Ready(
                preferencesManager.bootstrapTrainingOnboardingChoice.first()
            )
        }
    }

    fun chooseModelQualityTelemetry(enabled: Boolean) {
        val current = _telemetryOnboardingState.value as? TelemetryOnboardingState.Ready ?: return
        if (current.choice != null) return
        _telemetryOnboardingState.value = TelemetryOnboardingState.Loading
        viewModelScope.launch {
            preferencesManager.setModelQualityTelemetryOnboardingChoice(enabled)
            runCatching { behaviorRuntime.setTelemetryConsent(enabled) }
            _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(enabled)
        }
    }


    fun chooseBootstrapTraining(enabled: Boolean, includeHistorical: Boolean) {
        val current = _bootstrapTrainingOnboardingState.value as?
            BootstrapTrainingOnboardingState.Ready ?: return
        if (current.choice != null) return
        _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Loading
        viewModelScope.launch {
            preferencesManager.setBootstrapTrainingOnboardingChoice(enabled, includeHistorical)
            runCatching { behaviorRuntime.setBootstrapTrainingConsent(enabled, includeHistorical) }
            _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Ready(enabled)
        }
    }

    fun acceptUnifiedPrivacyPolicy() {
        val previousTelemetry = _telemetryOnboardingState.value as? TelemetryOnboardingState.Ready
            ?: return
        val previousTraining = _bootstrapTrainingOnboardingState.value as?
            BootstrapTrainingOnboardingState.Ready ?: return
        _telemetryOnboardingState.value = TelemetryOnboardingState.Loading
        _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Loading
        viewModelScope.launchSafe {
            try {
                // Android Keystore and synchronous disk commits must not run in the touch callback.
                withContext(Dispatchers.IO) {
                    AHUCache.setAgreementAccepted()
                    AHUCache.setPrivacyAccepted()
                    AHUCache.savePrivacyPolicyVersion(CURRENT_PRIVACY_POLICY_VERSION)
                    AHUCache.setBusinessAccepted()
                }
                preferencesManager.setModelQualityTelemetryOnboardingChoice(true)
                preferencesManager.setBootstrapTrainingOnboardingChoice(
                    value = true,
                    includeHistorical = true
                )
                runCatching { behaviorRuntime.setTelemetryConsent(true) }
                runCatching {
                    behaviorRuntime.setBootstrapTrainingConsent(
                        enabled = true,
                        includeHistorical = true
                    )
                }
                _telemetryOnboardingState.value = TelemetryOnboardingState.Ready(true)
                _bootstrapTrainingOnboardingState.value = BootstrapTrainingOnboardingState.Ready(true)
                _privacyPolicyAccepted.value = true
            } catch (error: Throwable) {
                _telemetryOnboardingState.value = previousTelemetry
                _bootstrapTrainingOnboardingState.value = previousTraining
                throw error
            }
        }
    }
}
