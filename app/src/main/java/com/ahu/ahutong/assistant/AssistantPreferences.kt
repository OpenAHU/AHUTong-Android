package com.ahu.ahutong.assistant

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder

/**
 * AI 入口的非敏感偏好：联网搜索与模型默认值。
 * API Key 仍然只在 [AssistantModelConfigStore] 的 Keystore 安全存储里。
 */
object AssistantPreferences {

    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-flash"

    private const val PREFS = "assistant_preferences"
    private const val KEY_SEARCH = "search_enabled"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_AI_PRIVACY_SUMMARY_SEEN = "ai_privacy_summary_seen"
    private const val KEY_SWIPE_HINT_SEEN = "assistant_swipe_hint_seen"

    private val prefs by lazy {
        AppEnvironmentHolder.context().getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { values ->
            // 旧版本的人格偏好已不再使用。
            values.edit().remove("personality").apply()
        }
    }

    fun searchEnabled(): Boolean = runCatching {
        prefs.getBoolean(KEY_SEARCH, true)
    }.getOrDefault(true)

    fun serviceEnabled(): Boolean = runCatching {
        prefs.getBoolean(KEY_SERVICE_ENABLED, true)
    }.getOrDefault(true)

    fun setServiceEnabled(enabled: Boolean) {
        runCatching { prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply() }
    }

    fun aiPrivacySummarySeen(): Boolean = runCatching {
        prefs.getBoolean(KEY_AI_PRIVACY_SUMMARY_SEEN, false)
    }.getOrDefault(false)

    fun markAiPrivacySummarySeen() {
        runCatching { prefs.edit().putBoolean(KEY_AI_PRIVACY_SUMMARY_SEEN, true).apply() }
    }

    fun swipeHintSeen(): Boolean = runCatching {
        prefs.getBoolean(KEY_SWIPE_HINT_SEEN, false)
    }.getOrDefault(false)

    fun markSwipeHintSeen() {
        runCatching { prefs.edit().putBoolean(KEY_SWIPE_HINT_SEEN, true).apply() }
    }

    fun setSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH, enabled).apply()
    }
}
