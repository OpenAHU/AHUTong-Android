package com.ahu.ahutong.ui.update

import com.ahu.ahutong.data.server.AhuTong

/**
 * Loads remote changelog text for the Settings "更新说明" dialog.
 */
suspend fun loadApkUpdateChangelog(): String {
    return runCatching {
        AhuTong.API.getApkUpdateInfo().changelog
            ?.ifBlank { "暂无更新说明" }
            ?: "暂无更新说明"
    }.getOrElse { "获取失败" }
}
