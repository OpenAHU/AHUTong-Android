package com.ahu.ahutong.data.mail

import android.util.Log
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import java.io.File
import okhttp3.Cookie
import okhttp3.HttpUrl

/** Debug-only connection trace without credential values, mailbox contents or account identifiers. */
internal object StudentMailDiagnostics {
    private const val FILE_NAME = "student-mail-diagnostics.log"
    @Synchronized fun begin() {
        if (!BuildConfig.DEBUG) return
        runCatching { file().writeText("") }
        record("connect.begin")
    }
    @Synchronized fun record(stage: String, detail: String = "") {
        if (!BuildConfig.DEBUG) return
        val line = "${System.currentTimeMillis()} $stage $detail"
        Log.i("StudentMailDiag", line)
        runCatching { file().appendText(line + "\n") }
    }
    fun route(url: HttpUrl): String = "${url.scheme}://${url.host}${url.encodedPath.substringBefore(';')}" +
        if (url.queryParameterNames.isEmpty()) "" else "?keys=" + url.queryParameterNames.sorted().joinToString(",")
    fun cookies(cookies: List<Cookie>): String = cookies.filter { it.name in setOf("SESSION", "CASTGC", "TGC", "JSESSIONID", "Language", "wengine_vpn_ticketwvpn_ahu_edu_cn", "route") }
        .joinToString(",") { "${it.name}@${it.domain}${it.path}[hostOnly=${it.hostOnly},secure=${it.secure}]" }.ifBlank { "none" }
    private fun file() = File(AppEnvironmentHolder.context().cacheDir, FILE_NAME)
}
