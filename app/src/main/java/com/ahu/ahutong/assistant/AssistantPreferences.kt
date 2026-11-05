package com.ahu.ahutong.assistant

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder

enum class AssistantPersonality(
    val id: String,
    val label: String,
    val description: String,
) {
    STANDARD(
        id = "standard",
        label = "标准",
        description = "清楚、稳、轻度关心。适合大多数查询和办事场景。",
    ),
    LIVELY(
        id = "lively",
        label = "活泼",
        description = "轻快、热情，偶尔口语和少量 emoji；不油腻、不刷屏。",
    ),
    CONCISE(
        id = "concise",
        label = "简洁",
        description = "只给结论和关键数据，不寒暄、不重复工具明细。",
    ),
    COMPANION(
        id = "companion",
        label = "陪伴",
        description = "更亲近、更软，有一点偏爱感，但保持分寸。",
    );

    companion object {
        fun fromId(value: String?): AssistantPersonality =
            entries.firstOrNull { it.id == value } ?: STANDARD
    }
}

/**
 * AI 入口的非敏感偏好：人格、联网搜索与模型默认值。
 * API Key 仍然只在 [AssistantModelConfigStore] 的 Keystore 安全存储里。
 */
object AssistantPreferences {

    const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
    const val DEFAULT_MODEL = "deepseek-flash"

    private const val PREFS = "assistant_preferences"
    private const val KEY_PERSONALITY = "personality"
    private const val KEY_SEARCH = "search_enabled"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_AI_PRIVACY_SUMMARY_SEEN = "ai_privacy_summary_seen"

    private val prefs by lazy {
        AppEnvironmentHolder.context().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val soulMarkdown by lazy {
        runCatching {
            AppEnvironmentHolder.context().assets.open("soul.md")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrDefault("")
    }

    fun personality(): AssistantPersonality = runCatching {
        AssistantPersonality.fromId(prefs.getString(KEY_PERSONALITY, null))
    }.getOrDefault(AssistantPersonality.STANDARD)

    fun setPersonality(personality: AssistantPersonality) {
        prefs.edit().putString(KEY_PERSONALITY, personality.id).apply()
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

    fun setSearchEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH, enabled).apply()
    }

    fun soulFor(personality: AssistantPersonality): String {
        val lines = runCatching { soulMarkdown }.getOrDefault("").lines()
        val start = lines.indexOfFirst { it.trim() == "## ${personality.label}" }
        if (start < 0) return ""
        return lines.drop(start + 1)
            .takeWhile { !it.trimStart().startsWith("## ") }
            .joinToString("\n")
            .trim()
    }
}
