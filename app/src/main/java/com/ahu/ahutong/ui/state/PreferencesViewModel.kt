package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.UiStyle
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.bootstrap.BootstrapContributionStatus
import com.ahu.ahutong.personalization.semantic.MutationId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    private val _personalizationEnabled = MutableStateFlow<Boolean?>(null)
    val personalizationEnabled: StateFlow<Boolean?> = _personalizationEnabled.asStateFlow()

    private val _predictivePrefetchEnabled = MutableStateFlow<Boolean?>(null)
    val predictivePrefetchEnabled: StateFlow<Boolean?> = _predictivePrefetchEnabled.asStateFlow()

    private val _wifiOnlyPrefetch = MutableStateFlow<Boolean?>(null)
    val wifiOnlyPrefetch: StateFlow<Boolean?> = _wifiOnlyPrefetch.asStateFlow()

    private val _behaviorRetentionDays = MutableStateFlow(30)
    val behaviorRetentionDays: StateFlow<Int> = _behaviorRetentionDays.asStateFlow()

    private val _showQRCode = MutableStateFlow(false)
    val showQRCode: StateFlow<Boolean> = _showQRCode.asStateFlow()

    private val _useCmbCardRecharge = MutableStateFlow(AHUCache.isCmbCardRechargePreferred())
    val useCmbCardRecharge: StateFlow<Boolean> = _useCmbCardRecharge.asStateFlow()

    private val _isShowAllCourse = MutableStateFlow(false)
    val isShowAllCourse: StateFlow<Boolean> = _isShowAllCourse.asStateFlow()

    private val _useLiquidGlass = MutableStateFlow(true)
    val useLiquidGlass: StateFlow<Boolean> = _useLiquidGlass.asStateFlow()

    private val _uiStyle = MutableStateFlow(UiStyle.RADIANT_UI)
    val uiStyle: StateFlow<UiStyle> = _uiStyle.asStateFlow()

    private val _themeColor = MutableStateFlow<String?>(null)
    val themeColor: StateFlow<String?> = _themeColor.asStateFlow()

    private val _appThemeMode = MutableStateFlow(AppThemeMode.FOLLOW_SYSTEM)
    val appThemeMode: StateFlow<AppThemeMode> = _appThemeMode.asStateFlow()

    private val _courseReminderEnabled = MutableStateFlow(false)
    val courseReminderEnabled: StateFlow<Boolean> = _courseReminderEnabled.asStateFlow()

    private val _courseReminderLiveCountdownEnabled = MutableStateFlow(false)
    val courseReminderLiveCountdownEnabled: StateFlow<Boolean> =
        _courseReminderLiveCountdownEnabled.asStateFlow()

    private val _repositoryAccelerationSource = MutableStateFlow("jsdelivr")
    val repositoryAccelerationSource: StateFlow<String> =
        _repositoryAccelerationSource.asStateFlow()

    val bootstrapContributionStatus: StateFlow<BootstrapContributionStatus> =
        behaviorRuntime.bootstrapContributionStatus

    init {
        viewModelScope.launch { preferencesManager.personalizationEnabled.collect { _personalizationEnabled.value = it } }
        viewModelScope.launch { preferencesManager.predictivePrefetchEnabled.collect { _predictivePrefetchEnabled.value = it } }
        viewModelScope.launch { preferencesManager.wifiOnlyPrefetch.collect { _wifiOnlyPrefetch.value = it } }
        viewModelScope.launch { preferencesManager.behaviorRetentionDays.collect { _behaviorRetentionDays.value = it } }
        viewModelScope.launch { preferencesManager.themeMode.collect { _appThemeMode.value = it } }
        viewModelScope.launch {
            preferencesManager.themeColor.collect {
                _themeColor.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.showQRCode.collect {
                _showQRCode.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.isShowAllCourse.collect {
                _isShowAllCourse.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.useLiquidGlass.collect {
                _useLiquidGlass.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.uiStyle.collect {
                _uiStyle.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.courseReminderEnabled.collect {
                _courseReminderEnabled.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.courseReminderLiveCountdownEnabled.collect {
                _courseReminderLiveCountdownEnabled.value = it
            }
        }
        viewModelScope.launch {
            preferencesManager.repositoryAccelerationSource.collect {
                _repositoryAccelerationSource.value = it
            }
        }
    }

    fun setPersonalizationEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPersonalizationEnabled(value)
            if (!value) behaviorRuntime.hideSuggestion()
        }
    }

    fun setPredictivePrefetchEnabled(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setPredictivePrefetchEnabled(value)
            if (!value) {
                preferencesManager.setWifiOnlyPrefetch(false)
                behaviorRuntime.cancelPredictivePrefetch()
            }
        }
    }

    fun setWifiOnlyPrefetch(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWifiOnlyPrefetch(
                value && _predictivePrefetchEnabled.value == true
            )
        }
    }

    fun clearPersonalizationLearning() {
        viewModelScope.launch { behaviorRuntime.clearLearningRecord() }
    }

    fun setBootstrapTrainingContribution(enabled: Boolean, includeHistorical: Boolean = false) {
        viewModelScope.launch {
            behaviorRuntime.setBootstrapTrainingConsent(enabled, includeHistorical)
        }
    }

    fun deleteBootstrapTrainingContribution() {
        setBootstrapTrainingContribution(false, false)
    }

    fun setBehaviorRetentionDays(value: Int) {
        viewModelScope.launch { preferencesManager.setBehaviorRetentionDays(value) }
    }

    fun setShowQRCode(value: Boolean) {
        viewModelScope.launch {
            val oldValue = _showQRCode.value
            preferencesManager.setShowQRCode(value)
            behaviorRuntime.recordCommittedMutation(MutationId.HOME_DEFAULT_QR_CHANGED, oldValue, value)
        }
    }

    fun setIsShowAllCourse(value: Boolean) {
        viewModelScope.launch {
            val oldValue = _isShowAllCourse.value
            preferencesManager.setIsShowAllCourse(value)
            behaviorRuntime.recordCommittedMutation(MutationId.SCHEDULE_OVERVIEW_CHANGED, oldValue, value)
        }
    }

    fun setUseLiquidGlass(value: Boolean) {
        viewModelScope.launch {
            val oldValue = _useLiquidGlass.value
            preferencesManager.setUseLiquidGlass(value)
            behaviorRuntime.recordCommittedMutation(MutationId.LIQUID_GLASS_CHANGED, oldValue, value)
        }
    }

    fun setUiStyle(value: UiStyle) {
        viewModelScope.launch {
            val oldValue = _uiStyle.value
            preferencesManager.setUiStyle(value)
            if (oldValue != value) {
                behaviorRuntime.recordCommittedMutation(
                    MutationId.LIQUID_GLASS_CHANGED,
                    oldValue.storageValue,
                    value.storageValue,
                    coarseValueBucket = value.storageValue
                )
            }
        }
    }

    fun setCourseReminderEnabled(value: Boolean) {
        viewModelScope.launch {
            val oldValue = _courseReminderEnabled.value
            preferencesManager.setCourseReminderEnabled(value)
            behaviorRuntime.recordCommittedMutation(MutationId.COURSE_REMINDER_CHANGED, oldValue, value)
        }
    }

    fun setUseCmbCardRecharge(value: Boolean) {
        viewModelScope.launch {
            val oldValue = AHUCache.isCmbCardRechargePreferred()
            if (oldValue == value) {
                _useCmbCardRecharge.value = oldValue
                return@launch
            }
            AHUCache.setCmbCardRechargePreferred(value)
            val committedValue = AHUCache.isCmbCardRechargePreferred()
            _useCmbCardRecharge.value = committedValue
            if (committedValue == value) {
                behaviorRuntime.recordCommittedMutation(
                    MutationId.CMB_RECHARGE_PREFERENCE_CHANGED,
                    oldValue,
                    committedValue,
                    coarseValueBucket = if (committedValue) "ENABLED" else "DISABLED"
                )
            }
        }
    }

    fun setCourseReminderLiveCountdownEnabled(value: Boolean) {
        viewModelScope.launch {
            val oldValue = _courseReminderLiveCountdownEnabled.value
            preferencesManager.setCourseReminderLiveCountdownEnabled(value)
            behaviorRuntime.recordCommittedMutation(MutationId.COURSE_LIVE_COUNTDOWN_CHANGED, oldValue, value)
        }
    }

    fun setThemeColor(value: String?) {
        viewModelScope.launch {
            val oldValue = _themeColor.value
            preferencesManager.setThemeColor(value)
            behaviorRuntime.recordCommittedMutation(MutationId.THEME_CHANGED, oldValue, value, coarseValueBucket = "COLOR_CHANGED")
        }
    }

    fun setAppThemeMode(value: AppThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(value)
        }
    }

    fun setRepositoryAccelerationSource(value: String) {
        viewModelScope.launch {
            val oldValue = _repositoryAccelerationSource.value
            preferencesManager.setRepositoryAccelerationSource(value)
            behaviorRuntime.recordCommittedMutation(MutationId.REPOSITORY_ACCELERATION_CHANGED, oldValue, value, coarseValueBucket = "SOURCE_CHANGED")
        }
    }

}
