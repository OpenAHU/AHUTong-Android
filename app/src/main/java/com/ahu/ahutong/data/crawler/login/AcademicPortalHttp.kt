package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.manager.CookieManager
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

internal object AcademicPortalHttp {
    private val client by lazy {
        OkHttpClient.Builder()
            .cookieJar(CookieManager.cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    suspend fun request(address: String, fields: Map<String, String>?): PortalPage {
        var url = securePortalUrl(address)
        var form = fields
        repeat(12) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val builder = Request.Builder().url(url).header("User-Agent", JwxtApi.BROWSER_USER_AGENT)
            if (form != null) {
                if (url.host != "one.ahu.edu.cn" || !url.encodedPath.startsWith("/cas/")) {
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
