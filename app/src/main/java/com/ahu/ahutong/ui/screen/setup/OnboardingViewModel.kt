package com.ahu.ahutong.ui.screen.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.preview.EarlyAccessChoice
import com.ahu.ahutong.data.preview.EarlyAccessManager
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val behavior: BehaviorPredictionRuntime,
) : ViewModel() {

    val personalizationEnabled = settings.personalizationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val predictivePrefetchEnabled = settings.predictivePrefetchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val canteenConsent = settings.canteenUploadConsent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _earlyAccessBusy = MutableStateFlow(false)
    val earlyAccessBusy = _earlyAccessBusy.asStateFlow()
    private val _earlyAccessError = MutableStateFlow<String?>(null)
    val earlyAccessError = _earlyAccessError.asStateFlow()

    fun setPersonalizationEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { settings.setPersonalizationEnabled(enabled) } }
    }

    fun setPredictivePrefetchEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { settings.setPredictivePrefetchEnabled(enabled) } }
    }

    fun setCanteenConsent(enabled: Boolean) {
        viewModelScope.launch { runCatching { settings.setCanteenUploadConsent(enabled) } }
    }

    fun setEarlyAccess(enabled: Boolean) {
        if (_earlyAccessBusy.value) return
        _earlyAccessBusy.value = true
        _earlyAccessError.value = null
        viewModelScope.launch {
            try {
                if (enabled) {
                    EarlyAccessManager.join().onFailure {
                        _earlyAccessError.value = "加入失败，请检查网络后重试。"
                    }
                } else if (EarlyAccessManager.state.value.joined == true) {
                    EarlyAccessManager.leave().onFailure {
                        _earlyAccessError.value = "退出失败，请稍后重试。"
                    }
                } else {
                    EarlyAccessManager.decline()
                }
            } finally {
                _earlyAccessBusy.value = false
            }
        }
    }

    /** 勾选隐私政策后、真正提交登录前调用：补齐版本号与遥测/训练偏好。 */
    fun acceptLegalConsent() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                AHUCache.setAgreementAccepted()
                AHUCache.setPrivacyAccepted()
                AHUCache.savePrivacyPolicyVersion(CURRENT_PRIVACY_POLICY_VERSION)
                AHUCache.setBusinessAccepted()
                settings.setModelQualityTelemetryOnboardingChoice(true)
                settings.setBootstrapTrainingOnboardingChoice(
                    value = true,
                    includeHistorical = true,
                )
                behavior.setTelemetryConsent(true)
                behavior.setBootstrapTrainingConsent(enabled = true, includeHistorical = true)
            }
        }
    }

    /** 完成引导：未表态的共建项按不参与落盘，避免以后又弹一次。 */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (settings.canteenUploadConsent.first() == null) {
                        settings.setCanteenUploadConsent(false)
                    }
                    val state = EarlyAccessManager.state.value
                    if (state.joined != true && state.choice == EarlyAccessChoice.Unknown) {
                        EarlyAccessManager.decline()
                    }
                }
                OnboardingState.markCompleted()
            }
            onDone()
        }
    }
}
