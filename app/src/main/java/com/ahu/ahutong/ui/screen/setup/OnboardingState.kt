package com.ahu.ahutong.ui.screen.setup

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.dao.AHUCache

/** 隐私政策内容变更时递增。 */
internal const val CURRENT_PRIVACY_POLICY_VERSION = 3

/**
 * 首次启动引导的完成标记。
 *
 * 旧版本在 Splash 里弹隐私政策，同意后直接进登录；这些用户在升级后不应再走新引导，
 * 所以首次读取时用「是否已同意隐私政策」做一次兼容迁移。
 */
internal object OnboardingState {
    private const val PREFS = "onboarding_state"
    private const val KEY_COMPLETED = "completed_v1"
    private const val KEY_MIGRATION_DONE = "migration_v1"

    private var replayRequested = false

    private fun prefs(context: Context): android.content.SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = runCatching {
        prefs(AppEnvironmentHolder.context()).getBoolean(KEY_COMPLETED, false)
    }.getOrDefault(false)

    fun markCompleted() {
        runCatching {
            prefs(AppEnvironmentHolder.context()).edit()
                .putBoolean(KEY_COMPLETED, true)
                .putBoolean(KEY_MIGRATION_DONE, true)
                .apply()
        }
    }

    /** Debug-only：下次进入引导从欢迎页开始，不清登录与设置。 */
    fun startReplay() {
        replayRequested = true
        runCatching {
            prefs(AppEnvironmentHolder.context()).edit()
                .putBoolean(KEY_COMPLETED, false)
                .putBoolean(KEY_MIGRATION_DONE, true)
                .apply()
        }
    }

    fun consumeReplay(): Boolean {
        val requested = replayRequested
        replayRequested = false
        return requested
    }

    /** true 表示需要展示新引导。 */
    fun shouldShowOnboarding(): Boolean {
        if (isCompleted()) return false
        return runCatching {
            val values = prefs(AppEnvironmentHolder.context())
            if (!values.getBoolean(KEY_MIGRATION_DONE, false)) {
                values.edit().putBoolean(KEY_MIGRATION_DONE, true).apply()
                // 旧版本 Splash 已同意隐私政策的用户，升级后不再走新引导。
                if (AHUCache.isPrivacyAccepted()) {
                    markCompleted()
                    return@runCatching false
                }
            }
            true
        }.getOrDefault(true)
    }
}
