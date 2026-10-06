package com.ahu.ahutong.core.storage

import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import kotlinx.coroutines.flow.Flow

/**
 * 用户设置的唯一读写入口（计划 §3.2 的 SettingsStore，ADR 0003 的第一档）。
 *
 * 设置属于「不能丢、丢了回默认值」的一类数据：读取一律是流，写入一律落盘。
 * 实现是 :app 的 PreferencesManager（DataStore + 启动镜像），
 * 因此 feature 只认识这个接口——不认识 Context，也不认识 DataStore。
 *
 * 为什么把整套设置放在一个接口里，而不是泛化成 String key/value：调用点已经按语义命名
 * （showQRCode、courseReminderEnabled……），泛化成键值对会把它们全部改写，
 * 而收益只是「少写一个文件」。设置的语义在这里，键名与默认值留给实现。
 */
interface SettingsStore {

    fun getStartupThemePreferences(): StartupThemePreferences?

    suspend fun rememberStartupThemePreferences(
        appUiTheme: AppUiTheme,
        themeColor: String?,
        themeMode: AppThemeMode
    )

    /** 清除全部设置（「清除所有数据」走的就是这条路径）。 */
    suspend fun clearAll()

    /** Theme Park：组件槽位覆盖（序列化字符串）。默认空实现，保证既有 fake/测试不炸。 */
    val componentSlotOverrides: Flow<String>
        get() = kotlinx.coroutines.flow.flowOf("")

    suspend fun setComponentSlotOverrides(value: String) {}

    val appUiTheme: Flow<AppUiTheme>
    suspend fun setAppUiTheme(value: AppUiTheme)

    val themeColor: Flow<String?>
    suspend fun setThemeColor(value: String?)

    val themeMode: Flow<AppThemeMode>
    suspend fun setThemeMode(value: AppThemeMode)

    val showQRCode: Flow<Boolean>
    suspend fun setShowQRCode(value: Boolean)

    val isShowAllCourse: Flow<Boolean>
    suspend fun setIsShowAllCourse(value: Boolean)

    val useBuiltInSecurePasswordKeyboard: Flow<Boolean>
    suspend fun setUseBuiltInSecurePasswordKeyboard(value: Boolean)

    val courseReminderEnabled: Flow<Boolean>
    suspend fun setCourseReminderEnabled(value: Boolean)

    val courseReminderLiveCountdownEnabled: Flow<Boolean>
    suspend fun setCourseReminderLiveCountdownEnabled(value: Boolean)

    val repositoryAccelerationSource: Flow<String>
    suspend fun setRepositoryAccelerationSource(value: String)

    // ── 个性化与遥测的开关：它们同样是用户设置，只是名字里带领域词汇 ──

    val personalizationEnabled: Flow<Boolean>
    suspend fun setPersonalizationEnabled(value: Boolean)

    /**
     * 必吃榜匿名数据上传同意：null=未表态（首页弹窗询问一次），true/false=已选。
     * 上传内容仅为去标识交易（POS 终端码+时间+金额+食堂），详见 docs/canteen-server-api.md。
     * 默认实现保护既有 fake 与测试。
     */
    val canteenUploadConsent: Flow<Boolean?>
        get() = kotlinx.coroutines.flow.flowOf(null)

    suspend fun setCanteenUploadConsent(value: Boolean) {}

    val predictivePrefetchEnabled: Flow<Boolean>
    suspend fun setPredictivePrefetchEnabled(value: Boolean)

    val wifiOnlyPrefetch: Flow<Boolean>
    suspend fun setWifiOnlyPrefetch(value: Boolean)

    val behaviorRetentionDays: Flow<Int>
    suspend fun setBehaviorRetentionDays(value: Int)

    val modelQualityTelemetryOnboardingChoice: Flow<Boolean?>
    suspend fun setModelQualityTelemetryOnboardingChoice(value: Boolean)

    fun modelQualityTelemetryEnabled(profileKey: String): Flow<Boolean>
    suspend fun setModelQualityTelemetryEnabled(profileKey: String, value: Boolean)

    val bootstrapTrainingOnboardingChoice: Flow<Boolean?>
    val bootstrapTrainingIncludeHistorical: Flow<Boolean>
    suspend fun setBootstrapTrainingOnboardingChoice(value: Boolean, includeHistorical: Boolean)

    fun bootstrapTrainingEnabled(profileKey: String): Flow<Boolean>
    suspend fun setBootstrapTrainingEnabled(profileKey: String, enabled: Boolean)

    /** 认领本档位的首次训练引导；返回 true 表示这次调用是第一次。 */
    suspend fun claimBootstrapTrainingOnboardingForProfile(profileKey: String): Boolean
}
