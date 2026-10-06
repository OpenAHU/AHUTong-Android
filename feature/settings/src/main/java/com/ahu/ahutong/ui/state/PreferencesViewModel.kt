package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.data.repository.RepositoryAccelerationSource
import com.ahu.ahutong.data.repository.RepositoryIndex
import com.ahu.ahutong.data.model.DEFAULT_THEME_COLOR
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.personalization.bootstrap.BootstrapContributionStatus
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.settings.PersonalizationSettings
import com.ahu.ahutong.ui.theme.pack.ComponentSlotId
import com.ahu.ahutong.ui.theme.pack.SlotSource
import com.ahu.ahutong.ui.theme.pack.parseSlotOverrides
import com.ahu.ahutong.ui.theme.pack.serializeSlotOverrides
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject

/**
 * 设置页的状态：读写用户设置（[SettingsStore]），并把改动报给个性化层（[PersonalizationSettings]）。
 *
 * 两个协作方都是接口，所以这个 ViewModel 不需要设备、不认识 DataStore、也不认识端侧模型——
 * 它第一次可以在 JVM 单测里被 fake 完整驱动（见 PreferencesViewModelTest）。
 * 界面看到的方法名与状态名保持不变，因此搬迁没有改变任何用户可见行为。
 */
@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val personalization: PersonalizationSettings,
    private val reminders: CourseReminderControl,
    private val repository: RepositoryIndex,
    private val behavior: BehaviorRecorder
) : ViewModel() {

    private val startupThemePreferences = settings.getStartupThemePreferences()

    private val _personalizationEnabled = MutableStateFlow<Boolean?>(null)
    val personalizationEnabled: StateFlow<Boolean?> = _personalizationEnabled.asStateFlow()

    private val _canteenUploadConsent = MutableStateFlow<Boolean?>(null)
    val canteenUploadConsent: StateFlow<Boolean?> = _canteenUploadConsent.asStateFlow()

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

    /** 组件槽位覆盖（Theme Park 混搭）：槽位 → 实现来源；空 = 全部跟随套装。 */
    private val _componentSlotOverrides = MutableStateFlow(
        parseSlotOverrides(startupThemePreferences?.slotOverrides)
    )
    val componentSlotOverrides: StateFlow<Map<ComponentSlotId, SlotSource>> =
        _componentSlotOverrides.asStateFlow()

    /** 设置某槽位的实现来源；source 传 null 表示恢复「跟随套装」。 */
    fun setComponentSlotOverride(slot: ComponentSlotId, source: SlotSource?) {
        val next = _componentSlotOverrides.value.toMutableMap()
        if (source == null) next.remove(slot) else next[slot] = source
        _componentSlotOverrides.value = next
        viewModelScope.launch {
            settings.setComponentSlotOverrides(next.serializeSlotOverrides())
        }
    }

    /** 一键清空全部槽位覆盖。 */
    fun clearComponentSlotOverrides() {
        _componentSlotOverrides.value = emptyMap()
        viewModelScope.launch { settings.setComponentSlotOverrides("") }
    }

    private val _courseReminderEnabled = MutableStateFlow(false)
    private val courseReminderSettingMutex = Mutex()
    val courseReminderEnabled: StateFlow<Boolean> = _courseReminderEnabled.asStateFlow()

    private val _courseReminderExactAlarmAllowed = MutableStateFlow(reminders.canScheduleExactReminders())
    val courseReminderExactAlarmAllowed: StateFlow<Boolean> = _courseReminderExactAlarmAllowed.asStateFlow()

    private val _courseReminderLiveCountdownEnabled = MutableStateFlow(false)
    val courseReminderLiveCountdownEnabled: StateFlow<Boolean> =
        _courseReminderLiveCountdownEnabled.asStateFlow()

    private val _repositoryAccelerationSource = MutableStateFlow("jsdelivr")
    val repositoryAccelerationSource: StateFlow<String> =
        _repositoryAccelerationSource.asStateFlow()

    val bootstrapContributionStatus: StateFlow<BootstrapContributionStatus> =
        personalization.contributionStatus

    /** 加速源是仓库侧的词汇，设置页只展示与选择（见 RepositoryIndex）。 */
    val accelerationSources: List<RepositoryAccelerationSource> = repository.accelerationSources

    init {
        viewModelScope.launch { settings.personalizationEnabled.collect { _personalizationEnabled.value = it } }
        viewModelScope.launch { settings.canteenUploadConsent.collect { _canteenUploadConsent.value = it } }
        viewModelScope.launch { settings.predictivePrefetchEnabled.collect { _predictivePrefetchEnabled.value = it } }
        viewModelScope.launch { settings.wifiOnlyPrefetch.collect { _wifiOnlyPrefetch.value = it } }
        viewModelScope.launch { settings.behaviorRetentionDays.collect { _behaviorRetentionDays.value = it } }
        viewModelScope.launch {
            combine(
                settings.appUiTheme,
                settings.themeColor,
                settings.themeMode
            ) { appUiTheme, themeColor, themeMode ->
                Triple(appUiTheme, themeColor, themeMode)
            }.collect { (appUiTheme, themeColor, themeMode) ->
                _appUiTheme.value = appUiTheme
                _themeColor.value = themeColor
                _appThemeMode.value = themeMode
                _isUiThemePreferenceReady.value = true
                settings.rememberStartupThemePreferences(
                    appUiTheme = appUiTheme,
                    themeColor = themeColor,
                    themeMode = themeMode
                )
            }
        }
        viewModelScope.launch {
            settings.componentSlotOverrides.collect {
                _componentSlotOverrides.value = parseSlotOverrides(it)
            }
        }
        viewModelScope.launch {
            settings.showQRCode.collect {
                _showQRCode.value = it
            }
        }
        viewModelScope.launch {
            settings.isShowAllCourse.collect {
                _isShowAllCourse.value = it
            }
        }
        viewModelScope.launch {
            settings.useBuiltInSecurePasswordKeyboard.collect {
                _useBuiltInSecurePasswordKeyboard.value = it
            }
        }
        viewModelScope.launch {
            settings.courseReminderEnabled.collect {
                _courseReminderEnabled.value = it
            }
        }
        viewModelScope.launch {
            settings.courseReminderLiveCountdownEnabled.collect {
                _courseReminderLiveCountdownEnabled.value = it
            }
        }
        viewModelScope.launch {
            settings.repositoryAccelerationSource.collect {
                _repositoryAccelerationSource.value = it
            }
        }
    }

    fun setPersonalizationEnabled(value: Boolean) {
        writeSetting {
            settings.setPersonalizationEnabled(value)
            if (!value) personalization.dismissSuggestion()
        }
    }

    fun setCanteenUploadConsent(value: Boolean) {
        writeSetting {
            settings.setCanteenUploadConsent(value)
        }
    }

    /**
     * 设置写入的统一出口。
     *
     * ADR 0003 的写策略在第二次失败后会把 IO 异常抛出来——它不该在 `viewModelScope` 里变成崩溃：
     * 记一条日志，并把两个乐观状态（UI 主题、主题色）拉回落盘值，界面不会停在「已经改了」的样子。
     */
    private fun writeSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: IOException) {
                Log.w(TAG, "setting write failed", error)
                runCatching {
                    _appUiTheme.value = settings.appUiTheme.first()
                    _themeColor.value = settings.themeColor.first()
                }
            }
        }
    }

    fun setPredictivePrefetchEnabled(value: Boolean) {
        writeSetting {
            settings.setPredictivePrefetchEnabled(value)
            if (!value) {
                settings.setWifiOnlyPrefetch(false)
                personalization.cancelPredictivePrefetch()
            }
        }
    }

    fun setWifiOnlyPrefetch(value: Boolean) {
        writeSetting {
            settings.setWifiOnlyPrefetch(
                value && _predictivePrefetchEnabled.value == true
            )
        }
    }

    fun clearPersonalizationLearning() {
        writeSetting { personalization.clearLearningRecord() }
    }

    fun setBootstrapTrainingContribution(enabled: Boolean, includeHistorical: Boolean = false) {
        writeSetting {
            personalization.setBootstrapTrainingContribution(enabled, includeHistorical)
        }
    }

    fun deleteBootstrapTrainingContribution() {
        setBootstrapTrainingContribution(false, false)
    }

    fun setBehaviorRetentionDays(value: Int) {
        writeSetting { settings.setBehaviorRetentionDays(value) }
    }

    // ── 提醒的后台动作：由后台执行，设置页只发命令（见 CourseReminderControl） ──

    fun rescheduleCourseReminders() = reminders.reschedule()

    fun cancelCourseReminders() = reminders.cancel()

    fun dismissActiveCourseReminder() = reminders.cancelActiveReminder()

    fun openCourseReminderSystemSettings() = reminders.openSystemSettings()

    fun openCourseReminderExactAlarmSettings() = reminders.openExactAlarmSettings()

    fun refreshCourseReminderAlarmAccess() {
        val allowed = reminders.canScheduleExactReminders()
        if (_courseReminderExactAlarmAllowed.value != allowed) {
            _courseReminderExactAlarmAllowed.value = allowed
            reminders.reschedule()
        }
    }

    fun setShowQRCode(value: Boolean) {
        writeSetting {
            val oldValue = _showQRCode.value
            settings.setShowQRCode(value)
            behavior.reportCommittedMutation(MutationId.HOME_DEFAULT_QR_CHANGED, oldValue, value)
        }
    }

    fun setIsShowAllCourse(value: Boolean) {
        writeSetting {
            val oldValue = _isShowAllCourse.value
            settings.setIsShowAllCourse(value)
            behavior.reportCommittedMutation(MutationId.SCHEDULE_OVERVIEW_CHANGED, oldValue, value)
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
        writeSetting {
            // The Miuix default is a real preference, not just a temporary UI selection.
            // Persist it with the theme switch so the color collector cannot restore the
            // previous system accent during a hot switch or after process recreation.
            settings.setThemeColor(nextThemeColor)
            settings.setAppUiTheme(value)
            behavior.reportCommittedMutation(
                MutationId.THEME_CHANGED,
                oldValue.storageValue,
                value.storageValue,
                coarseValueBucket = "UI_STYLE_CHANGED"
            )
        }
    }

    fun setCourseReminderEnabled(value: Boolean) {
        writeSetting {
            courseReminderSettingMutex.withLock {
                val oldValue = settings.courseReminderEnabled.first()
                settings.setCourseReminderEnabled(value)
                if (value) reminders.reschedule() else reminders.cancel()
                behavior.reportCommittedMutation(MutationId.COURSE_REMINDER_CHANGED, oldValue, value)
            }
        }
    }

    fun setUseBuiltInSecurePasswordKeyboard(value: Boolean) {
        writeSetting {
            settings.setUseBuiltInSecurePasswordKeyboard(value)
        }
    }

    fun setCourseReminderLiveCountdownEnabled(value: Boolean) {
        writeSetting {
            val oldValue = _courseReminderLiveCountdownEnabled.value
            settings.setCourseReminderLiveCountdownEnabled(value)
            if (!value) reminders.cancelActiveReminder()
            behavior.reportCommittedMutation(
                MutationId.COURSE_LIVE_COUNTDOWN_CHANGED,
                oldValue,
                value
            )
        }
    }

    fun setThemeColor(value: String?) {
        val oldValue = _themeColor.value
        _themeColor.value = value
        writeSetting {
            settings.setThemeColor(value)
            behavior.reportCommittedMutation(
                MutationId.THEME_CHANGED,
                oldValue,
                value,
                coarseValueBucket = "COLOR_CHANGED"
            )
        }
    }

    fun setAppThemeMode(value: AppThemeMode) {
        writeSetting {
            settings.setThemeMode(value)
        }
    }

    fun setRepositoryAccelerationSource(value: String) {
        writeSetting {
            val oldValue = _repositoryAccelerationSource.value
            settings.setRepositoryAccelerationSource(value)
            behavior.reportCommittedMutation(
                MutationId.REPOSITORY_ACCELERATION_CHANGED,
                oldValue,
                value,
                coarseValueBucket = "SOURCE_CHANGED"
            )
        }
    }

    private companion object {
        const val TAG = "Settings"
    }
}

