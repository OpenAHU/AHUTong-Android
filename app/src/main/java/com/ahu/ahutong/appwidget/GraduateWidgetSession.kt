package com.ahu.ahutong.appwidget

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.gmis.GmisScheduleClient
import com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException
import com.ahu.ahutong.data.crawler.login.AcademicPortalHttp
import com.ahu.ahutong.data.crawler.login.AcademicPortalLogin
import com.ahu.ahutong.data.crawler.login.GmisSessionVerifier
import com.ahu.ahutong.data.crawler.login.PortalPage
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.session.SessionStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request

/** Copy cookie values once; response cookies never reach the application's persistent jar. */
internal class WidgetCookieJar(seed: List<Cookie>) : CookieJar {
    private val cookies = seed.filter { permitted(it) }.toMutableList()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.filter { permitted(it) }.forEach { fresh ->
            this.cookies.removeAll {
                it.name == fresh.name && it.domain == fresh.domain && it.path == fresh.path
            }
            this.cookies.add(fresh)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }

    private fun permitted(cookie: Cookie): Boolean =
        cookie.domain in setOf("one.ahu.edu.cn", "gmis.ahu.edu.cn", "ahu.edu.cn")
}

internal object GraduateWidgetSession {
    fun client(user: User): GmisScheduleClient {
        val http = WidgetPortalReader(WidgetCookieJar(CookieManager.cookieJar.allCookies))
        fun requireSession() {
            if (SessionStore.currentUser() !== user) throw CancellationException("Widget account changed")
        }
        return GmisScheduleClient(
            openSession = {
                requireSession()
                val page = http.request(AcademicPortalLogin.GMIS_ENTRY)
                val home = GmisSessionVerifier.studentHomeUrl(page)?.let { http.request(it) }
                if (!GmisSessionVerifier.isStudentSession(page, user.xh, user.name, home)) {
                    throw GmisSessionExpiredException()
                }
                requireSession()
                page.url
            },
            query = { url, fields, referer ->
                requireSession()
                http.readGmis(url, fields, referer)
            }
        )
    }
}

/** Only GET session checks and the GMIS timetable query POST are available. No CAS login POST. */
internal class WidgetPortalReader(cookieJar: CookieJar) {
    private val client = AhuHttp.plain(
        connectTimeoutSeconds = 15,
        readTimeoutSeconds = 30,
        callTimeoutSeconds = 45,
        followRedirects = false,
        followSslRedirects = false
    ).cookieJar(cookieJar).build()

    suspend fun request(address: String): PortalPage = execute(address, null, null)

    suspend fun readGmis(address: String, fields: Map<String, String>?, referer: String): PortalPage {
        val url = AcademicPortalHttp.securePortalUrl(address)
        val source = AcademicPortalHttp.securePortalUrl(referer)
        val path = url.encodedPath.replace(Regex("(?i)/\\(S\\([^)]*\\)\\)"), "")
        require(url.host == "gmis.ahu.edu.cn" && source.host == url.host)
        require(path in setOf("/gmis5/student/pygl/xskbcx", "/gmis5/student/default/bindterm",
            "/gmis5/student/pygl/py_kbcx_ew"))
        if (fields != null) {
            require(path == "/gmis5/student/pygl/py_kbcx_ew" &&
                fields.keys == setOf("kblx", "termcode") && fields["kblx"] == "xs" &&
                Regex("[A-Za-z0-9_-]{1,40}").matches(fields["termcode"].orEmpty()))
        }
        return execute(address, fields, source.toString())
    }

    private suspend fun execute(address: String, fields: Map<String, String>?, referer: String?): PortalPage {
        var url = AcademicPortalHttp.securePortalUrl(address)
        var form = fields
        repeat(12) {
            currentCoroutineContext().ensureActive()
            val builder = Request.Builder().url(url).header("User-Agent", JwxtApi.BROWSER_USER_AGENT)
            if (referer != null && url.host == "gmis.ahu.edu.cn") {
                builder.header("Referer", referer).header("X-Requested-With", "XMLHttpRequest")
            }
            if (form != null) {
                // A 307 redirect must not turn a read-only query into a CAS credentials submission.
                if (url.host != "gmis.ahu.edu.cn" ||
                    !url.encodedPath.endsWith("/student/pygl/py_kbcx_ew")) {
                    throw IOException("Unexpected widget query redirect")
                }
                builder.post(FormBody.Builder().apply {
                    form!!.forEach { (key, value) -> add(key, value) }
                }.build())
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    val next = response.header("Location")?.let(url::resolve)
                        ?: throw IOException("Missing widget portal redirect")
                    url = AcademicPortalHttp.securePortalUrl(next.toString())
                    if (response.code !in setOf(307, 308)) form = null
                } else {
                    return PortalPage(url.toString(), response.code, response.body?.string().orEmpty())
                }
            }
        }
        throw IOException("Too many widget portal redirects")
    }
}
