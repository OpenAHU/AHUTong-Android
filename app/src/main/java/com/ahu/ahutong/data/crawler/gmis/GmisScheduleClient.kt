package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.schedule.gmis.*

import com.ahu.ahutong.data.crawler.login.PortalPage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Per-account, in-memory session only. Every URL is derived from a fresh GMIS login. */
internal class GmisScheduleClient(
    private val openSession: suspend () -> String,
    private val query: suspend (String, Map<String, String>?, String) -> PortalPage
) {
    private val mutex = Mutex()
    private var schedulePage: HttpUrl? = null

    suspend fun terms(): List<GmisTerm> = mutex.withLock {
        GmisTimetableParser.terms(load("../default/bindterm", null))
    }

    suspend fun timetable(term: GmisTerm): GmisTimetable = mutex.withLock {
        require(Regex("[A-Za-z0-9_-]{1,40}").matches(term.code))
        GmisTimetableParser.timetable(load("py_kbcx_ew", mapOf("kblx" to "xs", "termcode" to term.code)))
    }

    private suspend fun load(relative: String, fields: Map<String, String>?): com.google.gson.JsonElement {
        repeat(2) { attempt ->
            val page = schedulePage ?: timetablePage(openSession()).also { schedulePage = it }
            val url = page.resolve(relative) ?: throw GmisProtocolException("研究生课表地址无效")
            try {
                val response = query(url.toString(), fields, page.toString())
                val finalUrl = response.url.toHttpUrlOrNull()
                if (response.status in setOf(401, 403, 412) || finalUrl?.host != "gmis.ahu.edu.cn" ||
                    finalUrl.encodedPath.contains("login", ignoreCase = true)
                ) throw GmisSessionExpiredException()
                if (response.status !in 200..299) throw GmisProtocolException("研究生教务查询失败（HTTP ${response.status}）")
                return GmisResponseCodec.decode(response.html)
            } catch (e: GmisSessionExpiredException) {
                schedulePage = null
                if (attempt == 1) throw e
            }
        }
        throw GmisSessionExpiredException()
    }

    companion object {
        internal fun timetablePage(sessionUrl: String): HttpUrl {
            val url = sessionUrl.toHttpUrlOrNull() ?: throw GmisProtocolException("研究生会话地址无效")
            val suffix = "/student/default/index"
            if (url.scheme != "https" || url.host != "gmis.ahu.edu.cn" ||
                !url.encodedPath.startsWith("/gmis5/") || !url.encodedPath.endsWith(suffix)
            ) throw GmisProtocolException("研究生会话未进入学生首页")
            return url.newBuilder()
                .encodedPath(url.encodedPath.removeSuffix(suffix) + "/student/pygl/xskbcx")
                .query(null).fragment(null).build()
        }
    }
}
