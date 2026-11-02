package com.ahu.ahutong.ui.screen.camera

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.session.SessionStore

/** 相机页两种模式。退出时记住上次模式，下次进入直接落回。 */
enum class CameraMode(val key: String) {
    SCAN("scan"),
    NOTE("note");

    companion object {
        fun fromKey(key: String?): CameraMode =
            entries.firstOrNull { it.key == key } ?: SCAN
    }
}

object CameraModeStore {
    private const val PREFS = "camera_ui"
    private const val KEY_MODE = "mode"
    private const val KEY_NOTE_GALLERY_INTRO_SEEN = "note_gallery_intro_seen"

    private fun key(): String = "mode_" + (SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() } ?: "local")

    private fun prefs() = AppEnvironmentHolder.context()
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastMode(): CameraMode = CameraMode.fromKey(prefs().getString(key(), null))

    fun saveMode(mode: CameraMode) {
        prefs().edit().putString(key(), mode.key).apply()
    }

    fun hasSeenNoteGalleryIntro(): Boolean = prefs().getBoolean(KEY_NOTE_GALLERY_INTRO_SEEN, false)

    fun markNoteGalleryIntroSeen() {
        prefs().edit().putBoolean(KEY_NOTE_GALLERY_INTRO_SEEN, true).apply()
    }

    /** 登出/换账号时清掉，避免上一个人的使用习惯带过来。 */
    fun clear() {
        prefs().edit().remove(key()).apply()
    }
}
