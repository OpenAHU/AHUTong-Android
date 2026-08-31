package com.ahu.ahutong.data.dao

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.UiStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

object PreferencesKeys {
    val SHOW_QR_CODE = booleanPreferencesKey("show_qr_code")
    val IS_SHOW_ALL_COURSE = booleanPreferencesKey("is_show_all_course")
    val USE_LIQUID_GLASS = booleanPreferencesKey("use_liquid_glass")
    val UI_STYLE = stringPreferencesKey("ui_style")
    val COURSE_REMINDER_ENABLED = booleanPreferencesKey("course_reminder_enabled")
    val COURSE_REMINDER_LIVE_COUNTDOWN_ENABLED =
        booleanPreferencesKey("course_reminder_live_countdown_enabled")
    val THEME_COLOR = stringPreferencesKey("theme_color_hex")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val REPOSITORY_ACCELERATION_SOURCE = stringPreferencesKey("repository_acceleration_source")
    val PERSONALIZATION_ENABLED = booleanPreferencesKey("personalization_enabled")
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

class PreferencesManager @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun clearAll() {
        context.dataStore.edit { preferences -> preferences.clear() }
    }

    val personalizationEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.PERSONALIZATION_ENABLED] ?: true
    }

    suspend fun setPersonalizationEnabled(value: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PERSONALIZATION_ENABLED] = value }
    }

    val predictivePrefetchEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.PREDICTIVE_PREFETCH_ENABLED] ?: true
    }

    suspend fun setPredictivePrefetchEnabled(value: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PREDICTIVE_PREFETCH_ENABLED] = value }
    }

    val wifiOnlyPrefetch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.WIFI_ONLY_PREFETCH] ?: false
    }

    suspend fun setWifiOnlyPrefetch(value: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.WIFI_ONLY_PREFETCH] = value }
    }

    fun modelQualityTelemetryEnabled(profileKey: String): Flow<Boolean> = context.dataStore.data.map { prefs ->
        profileKey in prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_PROFILES].orEmpty()
    }

    suspend fun setModelQualityTelemetryEnabled(profileKey: String, value: Boolean) {
        context.dataStore.edit { prefs ->
            val profiles = prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_PROFILES].orEmpty().toMutableSet()
            if (value) profiles += profileKey else profiles -= profileKey
            prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_PROFILES] = profiles
        }
    }

    val modelQualityTelemetryOnboardingChoice: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_ONBOARDING_CHOICE]
            ?.takeIf {
                prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION] ==
                    MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION
            }
    }

    suspend fun setModelQualityTelemetryOnboardingChoice(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_ONBOARDING_CHOICE] = value
            prefs[PreferencesKeys.MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION] =
                MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION
        }
    }

    val bootstrapTrainingOnboardingChoice: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ONBOARDING_CHOICE]
            ?.takeIf {
                prefs[PreferencesKeys.BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION] ==
                    BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION
            }
    }

    val bootstrapTrainingIncludeHistorical: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BOOTSTRAP_TRAINING_INCLUDE_HISTORICAL] ?: false
    }

    fun bootstrapTrainingEnabled(profileKey: String): Flow<Boolean> = context.dataStore.data.map { prefs ->
        profileKey in prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES].orEmpty()
    }

    suspend fun setBootstrapTrainingEnabled(profileKey: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val profiles = prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES]
                .orEmpty()
                .toMutableSet()
            if (enabled) profiles += profileKey else profiles -= profileKey
            prefs[PreferencesKeys.BOOTSTRAP_TRAINING_ENABLED_PROFILES] = profiles
        }
    }

    suspend fun claimBootstrapTrainingOnboardingForProfile(profileKey: String): Boolean {
        var claimed = false
        context.dataStore.edit { prefs ->
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

    suspend fun setBootstrapTrainingOnboardingChoice(value: Boolean, includeHistorical: Boolean) {
        context.dataStore.edit { prefs ->
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
        const val MODEL_QUALITY_TELEMETRY_CONSENT_SCHEMA_VERSION = 3
        const val BOOTSTRAP_TRAINING_CONSENT_SCHEMA_VERSION = 1
    }

    val behaviorRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[PreferencesKeys.BEHAVIOR_RETENTION_DAYS] ?: 30).coerceIn(7, 30)
    }

    suspend fun setBehaviorRetentionDays(value: Int) {
        context.dataStore.edit { it[PreferencesKeys.BEHAVIOR_RETENTION_DAYS] = value.coerceIn(7, 30) }
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        AppThemeMode.fromStorage(prefs[PreferencesKeys.THEME_MODE])
    }

    suspend fun setThemeMode(value: AppThemeMode) {
        context.dataStore.edit { prefs ->
            if (value == AppThemeMode.FOLLOW_SYSTEM) {
                prefs.remove(PreferencesKeys.THEME_MODE)
            } else {
                prefs[PreferencesKeys.THEME_MODE] = value.storageValue
            }
        }
    }

    val themeColor: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.THEME_COLOR]
    }

    suspend fun setThemeColor(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) {
                prefs.remove(PreferencesKeys.THEME_COLOR)
            } else {
                prefs[PreferencesKeys.THEME_COLOR] = value
            }
        }
    }

    val repositoryAccelerationSource: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.REPOSITORY_ACCELERATION_SOURCE] ?: "jsdelivr"
    }

    suspend fun setRepositoryAccelerationSource(value: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.REPOSITORY_ACCELERATION_SOURCE] = value
        }
    }

    val showQRCode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SHOW_QR_CODE] ?: false
    }

    suspend fun setShowQRCode(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SHOW_QR_CODE] = value
        }
    }

    val isShowAllCourse: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.IS_SHOW_ALL_COURSE] ?: false
    }

    suspend fun setIsShowAllCourse(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_SHOW_ALL_COURSE] = value
        }
    }

    val useLiquidGlass: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.USE_LIQUID_GLASS] ?: true
    }

    suspend fun setUseLiquidGlass(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.USE_LIQUID_GLASS] = value
        }
    }

    val uiStyle: Flow<UiStyle> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.UI_STYLE]?.let(UiStyle::fromStorage) ?: UiStyle.RADIANT_UI
    }

    suspend fun setUiStyle(value: UiStyle) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.UI_STYLE] = value.storageValue
            // 同步旧的液态玻璃开关，避免两套状态分叉（RADIANT_UI 与 LIQUID_GLASS 都用玻璃）
            prefs[PreferencesKeys.USE_LIQUID_GLASS] = value != UiStyle.ORIGINAL
        }
    }

    val courseReminderEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.COURSE_REMINDER_ENABLED] ?: false
    }

    suspend fun setCourseReminderEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.COURSE_REMINDER_ENABLED] = value
        }
    }

    val courseReminderLiveCountdownEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.COURSE_REMINDER_LIVE_COUNTDOWN_ENABLED] ?: false
    }

    suspend fun setCourseReminderLiveCountdownEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.COURSE_REMINDER_LIVE_COUNTDOWN_ENABLED] = value
        }
    }

}
