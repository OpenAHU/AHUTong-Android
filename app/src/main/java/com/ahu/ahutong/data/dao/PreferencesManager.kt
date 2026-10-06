package com.ahu.ahutong.data.dao

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ahu.ahutong.core.storage.PaymentKeyboardSetting
import com.ahu.ahutong.core.storage.CourseReminderSettings
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.core.storage.StartupThemePreferences
import com.ahu.ahutong.core.storage.fallbackToDefaultOnReadFailure
import com.ahu.ahutong.core.storage.retryOnceOnWriteFailure
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.DEFAULT_THEME_COLOR
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

object PreferencesKeys {
    val SHOW_QR_CODE = booleanPreferencesKey("show_qr_code")
    val IS_SHOW_ALL_COURSE = booleanPreferencesKey("is_show_all_course")
    val USE_LIQUID_GLASS = booleanPreferencesKey("use_liquid_glass")
    val UI_THEME = stringPreferencesKey("ui_theme")
    val COMPONENT_SLOT_OVERRIDES = stringPreferencesKey("component_slot_overrides")
    val UI_STYLE = stringPreferencesKey("ui_style")
    val USE_BUILT_IN_SECURE_PASSWORD_KEYBOARD =
        booleanPreferencesKey("use_built_in_secure_password_keyboard")
    val COURSE_REMINDER_ENABLED = booleanPreferencesKey("course_reminder_enabled")
    val COURSE_REMINDER_LIVE_COUNTDOWN_ENABLED =
        booleanPreferencesKey("course_reminder_live_countdown_enabled")
    val THEME_COLOR = stringPreferencesKey("theme_color_hex")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val REPOSITORY_ACCELERATION_SOURCE = stringPreferencesKey("repository_acceleration_source")
    val PERSONALIZATION_ENABLED = booleanPreferencesKey("personalization_enabled")
    val CANTEEN_UPLOAD_CONSENT = booleanPreferencesKey("canteen_upload_consent")
    val PREDICTIVE_PREFETCH_ENABLED = booleanPreferencesKey("predictive_prefetch_enabled")
    val WIFI_ONLY_PREFETCH = booleanPreferencesKey("wifi_only_prefetch")
    val MODEL_QUALITY_TELEMETRY_PROFILES = stringSetPreferencesKey("model_quality_telemetry_profiles")
    val MODEL_QUALITY_TELEMETRY_ONBOARDING_CHOICE =
        booleanPreferencesKey("model_quality_telemetry_onboarding_choice")
    val MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION =
        intPreferencesKey("model_quality_telemetry_consent_schema_version")
    val BOOTSTRAP_TRAINING_ONBOARDING_CHOICE =
        booleanPreferencesKey("bootstrap_training_onboarding_choice")
    val BOOTSTRAP_TRAINING_INCLUDE_HISTORICAL =
        booleanPreferencesKey("bootstrap_training_include_historical")
    val BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION =
        intPreferencesKey("bootstrap_training_consent_schema_version")
    val BOOTSTRAP_TRAINING_ENABLED_PROFILES =
        stringSetPreferencesKey("bootstrap_training_enabled_profiles")
    val BOOTSTRAP_TRAINING_ONBOARDING_CLAIMED =
        booleanPreferencesKey("bootstrap_training_onboarding_claimed")
    val BEHAVIOR_RETENTION_DAYS = intPreferencesKey("behavior_retention_days")
}

private val Context.dataStore by preferencesDataStore(name = "user_pref")

class PreferencesManager @Inject constructor(@param:ApplicationContext private val context: Context) :
    SettingsStore, PaymentKeyboardSetting, CourseReminderSettings {


    private val startupThemeMirror by lazy {
        context.getSharedPreferences("startup_theme_mirror", Context.MODE_PRIVATE)
    }

    /**
     * ADR 0003 的读策略：DataStore 读失败回默认值——一条设置读不出来，不该让整个页面崩掉。
     * 默认值就是"空设置"，因此各条设置自己写的 `?: 默认` 会照常生效。
     */
    private fun <T> preferences(read: (Preferences) -> T): Flow<T> =
        context.dataStore.data
            .fallbackToDefaultOnReadFailure(emptyPreferences()) { error ->
                Log.w(TAG, "settings read failed, falling back to defaults", error)
            }
            .map(read)

    /**
     * 隐私与网络开关的读策略：读失败时倒向保守的一侧（个性化关、预取关、仅 Wi-Fi 开），
     * 而不是把「默认开」当成读失败时的答案——一次读取故障不该放开数据收集或计费流量。
     * 文件正常但没有这一项时，仍然走各自的默认值。
     */
    private fun <T> preferencesFailingClosed(
        failureDefault: T,
        read: (Preferences) -> T
    ): Flow<T> =
        context.dataStore.data
            .map(read)
            // 先映射再兜底：读失败时给出的是这条设置自己的保守值，而不是"空设置"再走默认值。
            .fallbackToDefaultOnReadFailure(failureDefault) { error ->
                Log.w(TAG, "settings read failed, using the conservative default", error)
            }

    /** ADR 0003 的写策略：写失败重试一次，再失败就把异常抛给调用方。 */
    private suspend fun editPreferences(block: suspend (MutablePreferences) -> Unit) =
        retryOnceOnWriteFailure({ error ->
            Log.w(TAG, "settings write failed, retrying once", error)
        }) {
            context.dataStore.edit(block)
        }

    override fun getStartupThemePreferences(): StartupThemePreferences? = runCatching {
        if (!startupThemeMirror.getBoolean("initialized", false)) return@runCatching null
        StartupThemePreferences(
            appUiTheme = AppUiTheme.fromStorage(
                startupThemeMirror.getString("ui_theme", null),
                legacyUseLiquidGlass = null
            ),
            themeColor = startupThemeMirror.getString("theme_color", null),
            themeMode = AppThemeMode.fromStorage(
                startupThemeMirror.getString("theme_mode", null)
            ),
            slotOverrides = startupThemeMirror.getString("slot_overrides", null).orEmpty()
        )
    }.onFailure { error ->
        Log.w(TAG, "startup theme mirror is unreadable; using DataStore defaults", error)
    }.getOrNull()

    override suspend fun rememberStartupThemePreferences(
        appUiTheme: AppUiTheme,
        themeColor: String?,
        themeMode: AppThemeMode
    ) {
        updateStartupThemeMirror {
            putBoolean("initialized", true)
            putString("ui_theme", appUiTheme.storageValue)
            putString("theme_color", themeColor)
            putString("theme_mode", themeMode.storageValue)
        }
    }

    override suspend fun clearAll() {
        editPreferences { preferences -> preferences.clear() }
        updateStartupThemeMirror { clear() }
    }

    /** 镜像只避免启动闪色；权威值在 DataStore，因此两次失败后记录并安全回退即可。 */
    private suspend fun updateStartupThemeMirror(update: SharedPreferences.Editor.() -> Unit) =
        withContext(Dispatchers.IO) {
            fun commit(): Boolean = startupThemeMirror.edit().apply(update).commit()
            if (commit()) return@withContext
            Log.w(TAG, "startup theme mirror write failed, retrying once")
            if (!commit()) Log.w(TAG, "startup theme mirror write failed after retry")
        }

    override val personalizationEnabled: Flow<Boolean> =
        preferencesFailingClosed(failureDefault = false) { prefs ->
            prefs[PreferencesKeys.PERSONALIZATION_ENABLED] ?: true
        }

    override suspend fun setPersonalizationEnabled(value: Boolean) {
        editPreferences { it[PreferencesKeys.PERSONALIZATION_ENABLED] = value }
    }

    override val predictivePrefetchEnabled: Flow<Boolean> =
        preferencesFailingClosed(failureDefault = false) { prefs ->
            prefs[PreferencesKeys.PREDICTIVE_PREFETCH_ENABLED] ?: true
        }

    override suspend fun setPredictivePrefetchEnabled(value: Boolean) {
        editPreferences { it[PreferencesKeys.PREDICTIVE_PREFETCH_ENABLED] = value }
    }

    override val wifiOnlyPrefetch: Flow<Boolean> =
        preferencesFailingClosed(failureDefault = true) { prefs ->
            prefs[PreferencesKeys.WIFI_ONLY_PREFETCH] ?: false
        }

    override suspend fun setWifiOnlyPrefetch(value: Boolean) {
        editPreferences { it[PreferencesKeys.WIFI_ONLY_PREFETCH] = value }
    }

    override fun modelQualityTelemetryEnabled(profileKey: String): Flow<Boolean> = preferences { prefs ->
        profileKey in prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_PROFILES].orEmpty()
    }

    override suspend fun setModelQualityTelemetryEnabled(profileKey: String, value: Boolean) {
        editPreferences { prefs ->
            val profiles = prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_PROFILES].orEmpty().toMutableSet()
            if (value) profiles += profileKey else profiles -= profileKey
            prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_PROFILES] = profiles
        }
    }

    override val modelQualityTelemetryOnboardingChoice: Flow<Boolean?> = preferences { prefs ->
        prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_ONBOARDING_CHOICE]
            ?.takeIf {
                prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION] ==
                    MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION
            }
    }

    override suspend fun setModelQualityTelemetryOnboardingChoice(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_ONBOARDING_CHOICE] = value
            prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION] =
                MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION
        }
    }

    override val bootstrapTrainingOnboardingChoice: Flow<Boolean?> = preferences { prefs ->
        prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CHOICE]
            ?.takeIf {
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION] ==
                    BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION
            }
    }

    override val bootstrapTrainingIncludeHistorical: Flow<Boolean> = preferences { prefs ->
        prefs[PreferencesKeys.BOOTSTRAP_TRAINING_INCLUDE_HISTORICAL] ?: false
    }

    override fun bootstrapTrainingEnabled(profileKey: String): Flow<Boolean> = preferences { prefs ->
        profileKey in prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES].orEmpty()
    }

    override suspend fun setBootstrapTrainingEnabled(profileKey: String, enabled: Boolean) {
        editPreferences { prefs ->
            val profiles = prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES]
                .orEmpty()
                .toMutableSet()
            if (enabled) profiles += profileKey else profiles -= profileKey
            prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES] = profiles
        }
    }

    override suspend fun claimBootstrapTrainingOnboardingForProfile(profileKey: String): Boolean {
        var claimed = false
        editPreferences { prefs ->
            if (
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CHOICE] == true &&
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION] ==
                    BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION &&
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CLAIMED] != true
            ) {
                val profiles = prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES]
                    .orEmpty()
                    .toMutableSet()
                profiles += profileKey
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES] = profiles
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CLAIMED] = true
                claimed = true
            }
        }
        return claimed
    }

    override suspend fun setBootstrapTrainingOnboardingChoice(value: Boolean, includeHistorical: Boolean) {
        editPreferences { prefs ->
            if (
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION] !=
                BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION
            ) {
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CLAIMED] = false
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES] = emptySet()
            }
            prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CHOICE] = value
            prefs[PreferencesKeys.BOOTSTRAP_TRAINING_INCLUDE_HISTORICAL] = value && includeHistorical
            prefs[PreferencesKeys.BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION] =
                BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION
        }
    }

    companion object {
        private const val TAG = "Settings"

        const val MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION = 3
        const val BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION = 1
    }

    override val behaviorRetentionDays: Flow<Int> = preferences { prefs ->
        (prefs[PreferencesKeys.BEHAVIOR_RETENTION_DAYS] ?: 30).coerceIn(7, 30)
    }

    override suspend fun setBehaviorRetentionDays(value: Int) {
        editPreferences { it[PreferencesKeys.BEHAVIOR_RETENTION_DAYS] = value.coerceIn(7, 30) }
    }

    override val themeMode: Flow<AppThemeMode> = preferences { prefs ->
        AppThemeMode.fromStorage(prefs[PreferencesKeys.THEME_MODE])
    }

    override suspend fun setThemeMode(value: AppThemeMode) {
        editPreferences { prefs ->
            if (value == AppThemeMode.FOLLOW_SYSTEM) {
                prefs.remove(PreferencesKeys.THEME_MODE)
            } else {
                prefs[PreferencesKeys.THEME_MODE] = value.storageValue
            }
        }
    }

    override val themeColor: Flow<String?> = preferences { prefs ->
        prefs[PreferencesKeys.THEME_COLOR]
    }

    override suspend fun setThemeColor(value: String?) {
        editPreferences { prefs ->
            if (value == null) {
                prefs.remove(PreferencesKeys.THEME_COLOR)
            } else {
                prefs[PreferencesKeys.THEME_COLOR] = value
            }
        }
    }

    override val repositoryAccelerationSource: Flow<String> = preferences { prefs ->
        prefs[PreferencesKeys.REPOSITORY_ACCELERATION_SOURCE] ?: "jsdelivr"
    }

    override suspend fun setRepositoryAccelerationSource(value: String) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.REPOSITORY_ACCELERATION_SOURCE] = value
        }
    }

    override val showQRCode: Flow<Boolean> = preferences { prefs ->
        prefs[PreferencesKeys.SHOW_QR_CODE] ?: false
    }

    /** 必吃榜匿名上传同意：键不存在 = 未表态（null），首页弹窗据此询问一次。 */
    override val canteenUploadConsent: Flow<Boolean?> = preferences { prefs ->
        prefs[PreferencesKeys.CANTEEN_UPLOAD_CONSENT]
    }

    override suspend fun setCanteenUploadConsent(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.CANTEEN_UPLOAD_CONSENT] = value
        }
    }

    override suspend fun setShowQRCode(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.SHOW_QR_CODE] = value
        }
    }

    override val isShowAllCourse: Flow<Boolean> = preferences { prefs ->
        prefs[PreferencesKeys.IS_SHOW_ALL_COURSE] ?: false
    }

    override suspend fun setIsShowAllCourse(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.IS_SHOW_ALL_COURSE] = value
        }
    }

    override val appUiTheme: Flow<AppUiTheme> = preferences { prefs ->
        AppUiTheme.fromStorage(
            value = prefs[PreferencesKeys.UI_THEME],
            legacyUseLiquidGlass = prefs[PreferencesKeys.USE_LIQUID_GLASS],
            legacyUiStyle = prefs[PreferencesKeys.UI_STYLE]
        )
    }

    /** 组件槽位覆盖（序列化字符串，解析在 core/designsystem 的 ComponentSlots.kt）。 */
    override val componentSlotOverrides: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.COMPONENT_SLOT_OVERRIDES].orEmpty()
    }

    override suspend fun setComponentSlotOverrides(serialized: String) {
        editPreferences { prefs ->
            if (serialized.isEmpty()) {
                prefs.remove(PreferencesKeys.COMPONENT_SLOT_OVERRIDES)
            } else {
                prefs[PreferencesKeys.COMPONENT_SLOT_OVERRIDES] = serialized
            }
        }
        startupThemeMirror.edit().putString("slot_overrides", serialized).apply()
    }

    override suspend fun setAppUiTheme(value: AppUiTheme) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.UI_THEME] = value.storageValue
            if (value == AppUiTheme.MIUIX) {
                prefs[PreferencesKeys.THEME_COLOR] = DEFAULT_THEME_COLOR
            } else if (prefs[PreferencesKeys.THEME_COLOR] == DEFAULT_THEME_COLOR) {
                // "默认"是 Miuix 自己的蓝色，不应泄漏成 Material/LiquidGlass 的颜色。
                prefs.remove(PreferencesKeys.THEME_COLOR)
            }
            prefs.remove(PreferencesKeys.USE_LIQUID_GLASS)
            prefs.remove(PreferencesKeys.UI_STYLE)
        }
    }

    override val useBuiltInSecurePasswordKeyboard: Flow<Boolean> = preferences { prefs ->
        prefs[PreferencesKeys.USE_BUILT_IN_SECURE_PASSWORD_KEYBOARD] ?: true
    }

    override suspend fun setUseBuiltInSecurePasswordKeyboard(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.USE_BUILT_IN_SECURE_PASSWORD_KEYBOARD] = value
        }
    }

    override val courseReminderEnabled: Flow<Boolean> = preferences { prefs ->
        prefs[PreferencesKeys.COURSE_REMINDER_ENABLED] ?: false
    }

    override suspend fun setCourseReminderEnabled(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.COURSE_REMINDER_ENABLED] = value
        }
    }

    override val courseReminderLiveCountdownEnabled: Flow<Boolean> = preferences { prefs ->
        prefs[PreferencesKeys.COURSE_REMINDER_LIVE_COUNTDOWN_ENABLED] ?: false
    }

    override suspend fun setCourseReminderLiveCountdownEnabled(value: Boolean) {
        editPreferences { prefs ->
            prefs[PreferencesKeys.COURSE_REMINDER_LIVE_COUNTDOWN_ENABLED] = value
        }
    }

}
