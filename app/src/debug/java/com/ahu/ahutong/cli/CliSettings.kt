package com.ahu.ahutong.cli

import android.content.Context
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import kotlinx.coroutines.flow.first

/** Explicit public preference contract, never an arbitrary SharedPreferences/credential editor. */
internal class CliSettings(context: Context) {
    private val settings = PreferencesManager(context)

    suspend fun read(): Map<String, Any?> = mapOf(
        "appUiTheme" to settings.appUiTheme.first().name,
        "themeMode" to settings.themeMode.first().name,
        "themeColor" to settings.themeColor.first(),
        "componentSlotOverrides" to settings.componentSlotOverrides.first(),
        "materialFloatingBottomBar" to settings.materialFloatingBottomBar.first(),
        "hapticFeedbackEnabled" to settings.hapticFeedbackEnabled.first(),
        "showQRCode" to settings.showQRCode.first(),
        "isShowAllCourse" to settings.isShowAllCourse.first(),
        "useBuiltInSecurePasswordKeyboard" to settings.useBuiltInSecurePasswordKeyboard.first(),
        "personalizationEnabled" to settings.personalizationEnabled.first(),
        "predictivePrefetchEnabled" to settings.predictivePrefetchEnabled.first(),
        "wifiOnlyPrefetch" to settings.wifiOnlyPrefetch.first(),
        "behaviorRetentionDays" to settings.behaviorRetentionDays.first(),
        "repositoryAccelerationSource" to settings.repositoryAccelerationSource.first(),
        "canteenUploadConsent" to settings.canteenUploadConsent.first()
    )

    suspend fun set(name: String, value: String) {
        fun bool(): Boolean = value.toBooleanStrict()
        when (name) {
            "appUiTheme" -> settings.setAppUiTheme(AppUiTheme.valueOf(value))
            "themeMode" -> settings.setThemeMode(AppThemeMode.valueOf(value))
            "themeColor" -> { require(Regex("#[A-Fa-f0-9]{6}([A-Fa-f0-9]{2})?").matches(value)); settings.setThemeColor(value) }
            "componentSlotOverrides" -> settings.setComponentSlotOverrides(value)
            "materialFloatingBottomBar" -> settings.setMaterialFloatingBottomBar(bool())
            "hapticFeedbackEnabled" -> settings.setHapticFeedbackEnabled(bool())
            "showQRCode" -> settings.setShowQRCode(bool())
            "isShowAllCourse" -> settings.setIsShowAllCourse(bool())
            "useBuiltInSecurePasswordKeyboard" -> settings.setUseBuiltInSecurePasswordKeyboard(bool())
            "personalizationEnabled" -> settings.setPersonalizationEnabled(bool())
            "predictivePrefetchEnabled" -> settings.setPredictivePrefetchEnabled(bool())
            "wifiOnlyPrefetch" -> settings.setWifiOnlyPrefetch(bool())
            "behaviorRetentionDays" -> { val days = value.toInt(); require(days in 1..365); settings.setBehaviorRetentionDays(days) }
            "repositoryAccelerationSource" -> settings.setRepositoryAccelerationSource(value)
            "canteenUploadConsent" -> settings.setCanteenUploadConsent(bool())
            else -> error("Unknown preference")
        }
    }
}
