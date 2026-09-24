package com.ahu.ahutong.data.xuexiaotong

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 学习通的静态存储；同时实现 [ChaoxingReminderStore]——后台提醒只拿得到那个窄视图。 */
object Store : ChaoxingReminderStore {
    private const val PREFS = "ahutong_cx_prefs"
    private lateinit var sp: SharedPreferences

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun init(context: Context) {
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun getString(key: String, def: String = ""): String =
        sp.getString(key, def) ?: def

    private fun putString(key: String, value: String) {
        sp.edit().putString(key, value).apply()
    }

    private fun getBool(key: String, def: Boolean): Boolean =
        sp.getBoolean(key, def)

    private fun putBool(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    private fun getLong(key: String, def: Long): Long =
        sp.getLong(key, def)

    private fun putLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
    }

    private fun remove(key: String) {
        sp.edit().remove(key).apply()
    }

    fun saveCookie(cookie: String) = putString("cx_cookie", cookie)
    fun getCookie(): String = getString("cx_cookie")
    override fun hasCookie(): Boolean = getCookie().length > 20
    fun clearCookie() = remove("cx_cookie")

    fun saveCredential(phone: String, pwd: String) {
        val encryptedPhone = CredentialCrypto.encrypt(phone)
        val encryptedPassword = CredentialCrypto.encrypt(pwd)
        sp.edit()
            .putString("cx_cred_phone", encryptedPhone)
            .putString("cx_cred_pwd", encryptedPassword)
            .apply()
    }
    fun getCredential(): Pair<String, String>? {
        val p = getString("cx_cred_phone")
        val w = getString("cx_cred_pwd")
        if (p.isEmpty() || w.isEmpty()) return null
        val dp = CredentialCrypto.decrypt(p) ?: return null
        val dw = CredentialCrypto.decrypt(w) ?: return null
        return dp to dw
    }
    fun hasCredential(): Boolean = getString("cx_cred_phone").isNotEmpty()
    fun clearCredential() {
        remove("cx_cred_phone")
        remove("cx_cred_pwd")
    }

    fun getKeepLogin(): Boolean = sp.getBoolean("cx_keep_login", true)
    fun saveKeepLogin(v: Boolean) = putBool("cx_keep_login", v)

    fun saveCourses(list: List<Course>) {
        putString("cx_courses", json.encodeToString(list))
    }
    fun getCourses(): List<Course> {
        val raw = getString("cx_courses")
        if (raw.isEmpty()) return emptyList()
        return try { json.decodeFromString<List<Course>>(raw) } catch (e: Exception) { emptyList() }
    }

    fun saveWorks(list: List<Work>) {
        putString("cx_works", json.encodeToString(list))
    }
    override fun getWorks(): List<Work> {
        val raw = getString("cx_works")
        if (raw.isEmpty()) return emptyList()
        return try { json.decodeFromString<List<Work>>(raw) } catch (e: Exception) { emptyList() }
    }

    fun saveLastSync(ts: Long) = putLong("cx_last_sync", ts)
    fun getLastSync(): Long = getLong("cx_last_sync", 0)

    fun saveCourseColor(courseId: String, bg: String, text: String) {
        putString("cx_color_$courseId", "$bg|$text")
    }
    fun getCourseColorCached(courseId: String): ColorPair? {
        val raw = getString("cx_color_$courseId")
        if (raw.isEmpty()) return null
        val parts = raw.split("|")
        return if (parts.size == 2) ColorPair(parts[0], parts[1]) else null
    }

    fun getDarkMode(): Boolean = sp.getBoolean("cx_dark_mode", false)
    fun saveDarkMode(dark: Boolean) = putBool("cx_dark_mode", dark)
    fun toggleDark(): Boolean {
        val next = !getDarkMode()
        saveDarkMode(next)
        return next
    }

    fun saveRemindSetting(s: RemindSetting) {
        putString("cx_remind_setting", json.encodeToString(s))
    }
    override fun getRemindSetting(): RemindSetting {
        val raw = getString("cx_remind_setting")
        if (raw.isEmpty()) return RemindSetting()
        return try { json.decodeFromString<RemindSetting>(raw) } catch (e: Exception) { RemindSetting() }
    }

    override fun saveRemindedMap(map: Map<String, Int>) {
        putString("cx_reminded", json.encodeToString(map))
    }
    override fun getRemindedMap(): Map<String, Int> {
        val raw = getString("cx_reminded")
        if (raw.isEmpty()) return emptyMap()
        return try { json.decodeFromString<Map<String, Int>>(raw) } catch (e: Exception) { emptyMap() }
    }

    fun saveCustomEvents(list: List<CustomEvent>) {
        putString("cx_custom_events", json.encodeToString(list))
    }
    override fun getCustomEvents(): List<CustomEvent> {
        val raw = getString("cx_custom_events")
        if (raw.isEmpty()) return emptyList()
        return try { json.decodeFromString<List<CustomEvent>>(raw) } catch (e: Exception) { emptyList() }
    }

    fun getShowEmptyCourses(): Boolean = sp.getBoolean("cx_show_empty_courses", false)
    fun saveShowEmptyCourses(v: Boolean) = putBool("cx_show_empty_courses", v)

    fun getShowDone(): Boolean = sp.getBoolean("cx_show_done", true)
    fun saveShowDone(v: Boolean) = putBool("cx_show_done", v)

    fun getDoneGray(): Boolean = sp.getBoolean("cx_done_gray", true)
    fun saveDoneGray(v: Boolean) = putBool("cx_done_gray", v)

    fun saveCourseProgress(list: List<CourseProgress>) {
        putString("cx_course_progress", json.encodeToString(list))
    }
    fun getCourseProgress(): List<CourseProgress> {
        val raw = getString("cx_course_progress")
        if (raw.isEmpty()) return emptyList()
        return try { json.decodeFromString<List<CourseProgress>>(raw) } catch (e: Exception) { emptyList() }
    }

    fun clearLoginData() {
        remove("cx_cookie")
        remove("cx_courses")
        remove("cx_works")
        remove("cx_last_sync")
        remove("cx_reminded")
        remove("cx_course_progress")
        remove("cx_cred_phone")
        remove("cx_cred_pwd")
    }
}
