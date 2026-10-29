package com.ahu.ahutong.testing

import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.core.storage.StartupThemePreferences
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.personalization.bootstrap.BootstrapContributionStatus
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.recorder.BehaviorRecorder
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.MutationId
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.personalization.context.ExamDistanceBucket
import com.ahu.ahutong.personalization.settings.PersonalizationSettings
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.ui.state.AppDataReset
import com.ahu.ahutong.ui.state.AppUpdateGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 设置 feature 的 fake 适配器：内存里的一份设置 + 一份行为上报记录。
 *
 * 覆盖写 `MutableStateFlow` 而不是 `Flow`：`MutableStateFlow` 本身就是 `Flow`，
 * 于是测试既能读也能推着状态走，而 ViewModel 看到的仍然只是接口。
 */
class FakeSettingsStore : SettingsStore {

    override val appUiTheme = MutableStateFlow(AppUiTheme.RADIANT)
    override val themeColor = MutableStateFlow<String?>(null)
    override val themeMode = MutableStateFlow(AppThemeMode.FOLLOW_SYSTEM)
    override val materialFloatingBottomBar = MutableStateFlow(false)
    override val showQRCode = MutableStateFlow(false)
    override val isShowAllCourse = MutableStateFlow(false)
    override val useBuiltInSecurePasswordKeyboard = MutableStateFlow(true)
    override val courseReminderEnabled = MutableStateFlow(false)
    override val courseReminderLiveCountdownEnabled = MutableStateFlow(false)
    override val repositoryAccelerationSource = MutableStateFlow("jsdelivr")
    override val personalizationEnabled = MutableStateFlow(true)
    override val predictivePrefetchEnabled = MutableStateFlow(false)
    override val wifiOnlyPrefetch = MutableStateFlow(false)
    override val behaviorRetentionDays = MutableStateFlow(30)
    override val modelQualityTelemetryOnboardingChoice = MutableStateFlow<Boolean?>(null)
    override val bootstrapTrainingOnboardingChoice = MutableStateFlow<Boolean?>(null)
    override val bootstrapTrainingIncludeHistorical = MutableStateFlow(false)

    var startupTheme: StartupThemePreferences? = null

    private val modelQualityTelemetryProfiles = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val bootstrapTrainingProfiles = mutableMapOf<String, MutableStateFlow<Boolean>>()

    override fun getStartupThemePreferences(): StartupThemePreferences? = startupTheme

    override suspend fun rememberStartupThemePreferences(
        appUiTheme: AppUiTheme,
        themeColor: String?,
        themeMode: AppThemeMode
    ) {
        startupTheme = StartupThemePreferences(appUiTheme, themeColor, themeMode)
    }

    override suspend fun clearAll() = Unit

    override suspend fun setAppUiTheme(value: AppUiTheme) { appUiTheme.value = value }

    override suspend fun setThemeColor(value: String?) { themeColor.value = value }

    override suspend fun setThemeMode(value: AppThemeMode) { themeMode.value = value }

    override suspend fun setMaterialFloatingBottomBar(value: Boolean) {
        materialFloatingBottomBar.value = value
    }

    override suspend fun setShowQRCode(value: Boolean) { showQRCode.value = value }

    override suspend fun setIsShowAllCourse(value: Boolean) { isShowAllCourse.value = value }

    override suspend fun setUseBuiltInSecurePasswordKeyboard(value: Boolean) {
        useBuiltInSecurePasswordKeyboard.value = value
    }

    override suspend fun setCourseReminderEnabled(value: Boolean) {
        courseReminderEnabled.value = value
    }

    override suspend fun setCourseReminderLiveCountdownEnabled(value: Boolean) {
        courseReminderLiveCountdownEnabled.value = value
    }

    override suspend fun setRepositoryAccelerationSource(value: String) {
        repositoryAccelerationSource.value = value
    }

    override suspend fun setPersonalizationEnabled(value: Boolean) {
        personalizationEnabled.value = value
    }

    override suspend fun setPredictivePrefetchEnabled(value: Boolean) {
        predictivePrefetchEnabled.value = value
    }

    override suspend fun setWifiOnlyPrefetch(value: Boolean) { wifiOnlyPrefetch.value = value }

    override suspend fun setBehaviorRetentionDays(value: Int) { behaviorRetentionDays.value = value }

    override suspend fun setModelQualityTelemetryOnboardingChoice(value: Boolean) {
        modelQualityTelemetryOnboardingChoice.value = value
    }

    override fun modelQualityTelemetryEnabled(profileKey: String): Flow<Boolean> =
        modelQualityTelemetryProfiles.getOrPut(profileKey) { MutableStateFlow(false) }

    override suspend fun setModelQualityTelemetryEnabled(profileKey: String, value: Boolean) {
        modelQualityTelemetryEnabled(profileKey)
        modelQualityTelemetryProfiles.getValue(profileKey).value = value
    }

    override suspend fun setBootstrapTrainingOnboardingChoice(
        value: Boolean,
        includeHistorical: Boolean
    ) {
        bootstrapTrainingOnboardingChoice.value = value
        bootstrapTrainingIncludeHistorical.value = includeHistorical
    }

    override fun bootstrapTrainingEnabled(profileKey: String): Flow<Boolean> =
        bootstrapTrainingProfiles.getOrPut(profileKey) { MutableStateFlow(false) }

    override suspend fun setBootstrapTrainingEnabled(profileKey: String, enabled: Boolean) {
        bootstrapTrainingEnabled(profileKey)
        bootstrapTrainingProfiles.getValue(profileKey).value = enabled
    }

    override suspend fun claimBootstrapTrainingOnboardingForProfile(profileKey: String): Boolean = true
}

/** [BehaviorRecorder] 的 fake：把上报记下来，不训练也不推理。 */
class FakeBehaviorRecorder : BehaviorRecorder {

    data class Report(
        val mutationId: MutationId,
        val oldValue: Any?,
        val newValue: Any?,
        val coarseValueBucket: String?
    )

    val reports = mutableListOf<Report>()

    override fun recordOrganicAction(action: AppActionId) = Unit

    override fun recordContentState(
        domain: SemanticDomain,
        contentState: ContentStateBucket,
        freshnessBucket: Int,
        resultCount: ResultCountBucket,
        errorType: ErrorTypeBucket
    ) = Unit

    override fun recordExamDistance(bucket: ExamDistanceBucket) = Unit

    override fun reportCommittedMutation(
        mutationId: MutationId,
        oldValue: Any?,
        newValue: Any?,
        coarseValueBucket: String?
    ) {
        reports += Report(mutationId, oldValue, newValue, coarseValueBucket)
    }
}

/** [PersonalizationSettings] 的 fake：把上报记下来，不训练也不推理。 */
class FakePersonalizationSettings : PersonalizationSettings {

    val status = MutableStateFlow(BootstrapContributionStatus())

    override val contributionStatus: StateFlow<BootstrapContributionStatus> = status

    var dismissedCount = 0
        private set

    var cancelledPrefetchCount = 0
        private set

    override fun dismissSuggestion() {
        dismissedCount++
    }

    override suspend fun cancelPredictivePrefetch() {
        cancelledPrefetchCount++
    }

    override suspend fun clearLearningRecord() = Unit

    override suspend fun setBootstrapTrainingContribution(
        enabled: Boolean,
        includeHistorical: Boolean
    ) = Unit
}

/** [CourseReminderControl] 的 fake：只记下命令，不排期、不通知。 */
class FakeCourseReminderControl : CourseReminderControl {

    var rescheduleCount = 0
        private set

    var cancelCount = 0
        private set

    override fun reschedule() {
        rescheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }

    override fun cancelActiveReminder() = Unit

    override fun openSystemSettings() = Unit

    var exactAlarmAllowed = true
    var openExactAlarmSettingsCount = 0
        private set

    override fun canScheduleExactReminders(): Boolean = exactAlarmAllowed

    override fun openExactAlarmSettings() { openExactAlarmSettingsCount++ }
}

/** [AppDataReset] 的 fake：只记下被调用了几次。 */
class FakeAppDataReset : AppDataReset {

    var clearCount = 0
        private set

    override suspend fun clearAll() {
        clearCount++
    }
}

/** [AppUpdateGateway] 的 fake：固定版本名与更新说明，也可以让它失败。 */
class FakeAppUpdateGateway(
    override val currentVersionName: String? = "3.3.0",
    var changelogText: String = "更新说明",
    var failChangelog: Boolean = false
) : AppUpdateGateway {

    override suspend fun changelog(): String {
        if (failChangelog) throw IllegalStateException("offline")
        return changelogText
    }
}
