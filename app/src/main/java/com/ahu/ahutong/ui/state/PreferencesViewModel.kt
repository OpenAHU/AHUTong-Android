package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.dao.DEFAULT_THEME_COLOR
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.bootstrap.BootstrapContributionStatus
import com.ahu.ahutong.personalization.semantic.MutationId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    private val startupThemePreferences = preferencesManager.getStartupThemePreferences()

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

    private val _isShowAllCourse = MutableStateFlow(false)
    val isShowAllCourse: StateFlow<Boolean> = _isShowAllCourse.asStateFlow()

    private val _appUiTheme = MutableStateFlow(
        startupThemePreferences?.appUiTheme ?: AppUiTheme.RADIANT
    )
    val appUiTheme: StateFlow<AppUiTheme> = _appUiTheme.asStateFlow()

    private val _useBuiltInSecurePasswordKeyboard = MutableStateFlow(true)
    val useBuiltInSecurePasswordKeyboard: StateFlow<Boolean> =
        _useBuiltInSecurePasswordKeyboard.asStateFlow()

    private val _isUiThemePreferenceReady = MutableStateFlow(startupThemePreferences != null)
    val isUiThemePreferenceReady: StateFlow<Boolean> =
        _isUiThemePreferenceReady.asStateFlow()

    private val _themeColor = MutableStateFlow(startupThemePreferences?.themeColor)
    val themeColor: StateFlow<String?> = _themeColor.asStateFlow()

    private val _appThemeMode = MutableStateFlow(
        startupThemePreferences?.themeMode ?: AppThemeMode.FOLLOW_SYSTEM
    )
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
        viewModelScope.launch {
            combine(
                preferencesManager.appUiTheme,
                preferencesManager.themeColor,
                preferencesManager.themeMode
            ) { appUiTheme, themeColor, themeMode ->
                Triple(appUiTheme, themeColor, themeMode)
            }.collect { (appUiTheme, themeColor, themeMode) ->
                _appUiTheme.value = appUiTheme
                _themeColor.value = themeColor
                _appThemeMode.value = themeMode
                _isUiThemePreferenceReady.value = true
                preferencesManager.rememberStartupThemePreferences(
                    appUiTheme = appUiTheme,
                    themeColor = themeColor,
                    themeMode = themeMode
                )
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
            preferencesManager.useBuiltInSecurePasswordKeyboard.collect {
                _useBuiltInSecurePasswordKeyboard.value = it
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

    fun setAppUiTheme(value: AppUiTheme) {
        val oldValue = _appUiTheme.value
        _appUiTheme.value = value
        val nextThemeColor = when {
            value == AppUiTheme.MIUIX -> DEFAULT_THEME_COLOR
            _themeColor.value == DEFAULT_THEME_COLOR -> null
            else -> _themeColor.value
        }
        _themeColor.value = nextThemeColor
        viewModelScope.launch {
            // The Miuix default is a real preference, not just a temporary UI selection.
            // Persist it with the theme switch so the color collector cannot restore the
            // previous system accent during a hot switch or after process recreation.
            preferencesManager.setThemeColor(nextThemeColor)
            preferencesManager.setAppUiTheme(value)
            behaviorRuntime.recordCommittedMutation(
                MutationId.THEME_CHANGED,
                oldValue.storageValue,
                value.storageValue,
                coarseValueBucket = "UI_STYLE_CHANGED"
            )
        }
    }

    fun setCourseReminderEnabled(value: Boolean) {
        viewModelScope.launch {
            val oldValue = _courseReminderEnabled.value
            preferencesManager.setCourseReminderEnabled(value)
            behaviorRuntime.recordCommittedMutation(MutationId.COURSE_REMINDER_CHANGED, oldValue, value)
        }
    }

    fun setUseBuiltInSecurePasswordKeyboard(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUseBuiltInSecurePasswordKeyboard(value)
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
        val oldValue = _themeColor.value
        _themeColor.value = value
        viewModelScope.launch {
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
