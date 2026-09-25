package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.network.AhuHttp
import java.io.IOException
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

internal object AcademicPortalHttp {
    private val client by lazy {
        AhuHttp.plain(
            connectTimeoutSeconds = 15,
            readTimeoutSeconds = 30,
            callTimeoutSeconds = 45,
            followRedirects = false,
            followSslRedirects = false
        )
            .cookieJar(CookieManager.cookieJar)
            .build()
    }

    suspend fun request(address: String, fields: Map<String, String>?): PortalPage {
        return execute(address, fields, null)
    }

    suspend fun readGmis(address: String, fields: Map<String, String>?, referer: String): PortalPage {
        val url = securePortalUrl(address)
        val source = securePortalUrl(referer)
        val path = url.encodedPath.replace(Regex("(?i)/\\(S\\([^)]*\\)\\)"), "")
        require(url.host == "gmis.ahu.edu.cn" && source.host == url.host)
        require(path in setOf(
            "/gmis5/student/pygl/xskbcx",
            "/gmis5/student/default/bindterm",
            "/gmis5/student/pygl/py_kbcx_ew"
        ))
        if (fields != null) {
            require(path == "/gmis5/student/pygl/py_kbcx_ew" &&
                fields.keys == setOf("kblx", "termcode") && fields["kblx"] == "xs" &&
                Regex("[A-Za-z0-9_-]{1,40}").matches(fields["termcode"].orEmpty()))
        }
        return execute(address, fields, source.toString())
    }

    private suspend fun execute(address: String, fields: Map<String, String>?, gmisReferer: String?): PortalPage {
        var url = securePortalUrl(address)
        var form = fields
        repeat(12) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val builder = Request.Builder().url(url).header("User-Agent", JwxtApi.BROWSER_USER_AGENT)
            if (gmisReferer != null && url.host == "gmis.ahu.edu.cn") {
                builder.header("Referer", gmisReferer).header("X-Requested-With", "XMLHttpRequest")
            }
            if (form != null) {
                val permitted = if (gmisReferer == null) {
                    url.host == "one.ahu.edu.cn" && url.encodedPath.startsWith("/cas/")
                } else {
                    url.host == "gmis.ahu.edu.cn" && url.encodedPath.endsWith("/student/pygl/py_kbcx_ew")
                }
                if (!permitted) {
                    throw IOException("Refusing credentials outside central CAS")
                }
                val body = FormBody.Builder()
                form.forEach { (key, value) -> body.add(key, value) }
                builder.post(body.build())
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    val next = response.header("Location")?.let(url::resolve)
                        ?: throw IOException("Missing portal redirect")
                    url = securePortalUrl(next.toString())
                    if (response.code !in listOf(307, 308)) form = null
                } else {
                    return PortalPage(url.toString(), response.code, response.body?.string().orEmpty())
                }
            }
        }
        throw IOException("Too many portal redirects")
    }

    internal fun securePortalUrl(address: String): HttpUrl {
        val url = address.toHttpUrlOrNull() ?: throw IOException("Invalid portal URL")
        if (url.host !in setOf("one.ahu.edu.cn", "jw.ahu.edu.cn", "gmis.ahu.edu.cn")) {
            throw IOException("Unexpected portal host")
        }
        // GMIS advertises an HTTP CAS service identifier. Keep that service parameter
        // intact, but transport its returned ticket over HTTPS, never cleartext HTTP.
        if (url.scheme == "http" && url.host == "gmis.ahu.edu.cn") {
            return url.newBuilder().scheme("https").build()
        }
        if (url.scheme != "https") throw IOException("Insecure portal redirect")
        return url
    }
}
