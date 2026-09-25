package com.ahu.ahutong.data.adapter

import android.content.Context
import com.ahu.ahutong.data.update.ApkUpdateSkipStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppApkUpdateSkipStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ApkUpdateSkipStore {
    private val preferences by lazy {
        context.getSharedPreferences("apk_update_skip", Context.MODE_PRIVATE)
    }

    override fun skippedVersionCode(): Int? =
        preferences.getInt(KEY_VERSION_CODE, 0).takeIf { it > 0 }

    override fun saveSkippedVersionCode(versionCode: Int): Boolean =
        versionCode > 0 && preferences.edit().putInt(KEY_VERSION_CODE, versionCode).commit()

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().clear().commit()) { "Unable to clear skipped APK version" }
    }

    private companion object {
        const val KEY_VERSION_CODE = "skipped_version_code"
    }
}
