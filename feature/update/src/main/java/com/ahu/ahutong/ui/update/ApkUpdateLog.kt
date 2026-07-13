package com.ahu.ahutong.ui.update

import android.content.Context
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.feature.update.R

/**
 * Loads remote changelog text for the Settings update-log dialog.
 */
suspend fun loadApkUpdateChangelog(context: Context): String {
    return runCatching {
        AhuTong.API.getApkUpdateInfo().changelog
            ?.ifBlank { context.getString(R.string.no_update_log) }
            ?: context.getString(R.string.no_update_log)
    }.getOrElse { context.getString(R.string.fetch_failed) }
}
