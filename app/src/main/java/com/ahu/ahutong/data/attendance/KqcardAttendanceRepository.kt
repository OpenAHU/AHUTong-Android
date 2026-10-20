package com.ahu.ahutong.data.attendance

import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.network.AhuHttp
import java.net.URLDecoder
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 教务考勤（kqcard）读取。
 *
 * 认证不走 kqcard 自己：先在 ADWMH 应用列表里找“教务考勤”，跟随它的 CAS OAuth2
 * 链接，回调地址里会带 kqcard 的 JWT。token 只放内存，不落盘、不打日志。
 */
object KqcardAttendanceRepository {

    private const val ADWMH = "https://adwmh.ahu.edu.cn"
    private const val PAGE_URL =
        "https://kqcard.ahu.edu.cn/attendance-student/classWater/getClassWaterPage"
    private const val BASIC_AUTH = "Basic YWN0aXZpdHk6YWN0aXZpdHlfc2VjcmV0"
    private const val PAGE_SIZE = 10
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val jsonMedia = "application/json".toMediaType()

    private val client: OkHttpClient by lazy {
        AhuHttp.plain(connectTimeoutSeconds = 15, readTimeoutSeconds = 30)
            .cookieJar(CookieManager.cookieJar)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    private data class CachedToken(val value: String, val expiresAtMs: Long)

    @Volatile
    private var cachedToken: CachedToken? = null
    private val tokenMutex = Mutex()

    suspend fun loadPage(status: Int, page: Int): AttendancePage = withContext(Dispatchers.IO) {
        var token = token()
        var (code, body) = requestPage(token, status, page)
        if (code == 401) {
            cachedToken = null
            token = token()
            val retried = requestPage(token, status, page)
            code = retried.first
            body = retried.second
        }
        if (code !in 200..299) throw AttendanceException("考勤接口 HTTP $code")
        val root = JSONObject(body)
        if (root.optInt("code", 0) != 200) {
            throw AttendanceException(root.optString("msg").ifBlank { "考勤接口返回异常" })
        }
        AttendanceJsonParser.parsePage(body)
    }

    private suspend fun token(): String {
        cachedToken?.takeIf { it.expiresAtMs > System.currentTimeMillis() + 60_000 }
            ?.let { return it.value }
        return tokenMutex.withLock {
            cachedToken?.takeIf { it.expiresAtMs > System.currentTimeMillis() + 60_000 }
                ?.let { return@withLock it.value }
            acquireToken()
        }
    }

    private suspend fun acquireToken(): String {
        val apps = try {
            AdwmhApi.API.getMyApps().use { it.string() }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            throw AttendanceAuthException("智慧安大登录状态已失效，请重新登录安大通")
        }
        val root = JSONObject(apps)
        if (root.optInt("code", 0) != 10000) {
            throw AttendanceAuthException("智慧安大登录状态已失效，请重新登录安大通")
        }
        val app = findAttendanceApp(root.opt("object"))
            ?: throw AttendanceException("ADWMH 中未找到“教务考勤”入口")
        val rawLink = app.optString("lk")
            .ifBlank { app.optString("url") }
            .ifBlank { app.optString("appUrl") }
        if (rawLink.isBlank()) throw AttendanceException("“教务考勤”入口缺少链接")
        val link = rawLink.replace("&amp;", "&")
        val resolved = link.toHttpUrlOrNull()
            ?: "$ADWMH/".toHttpUrl().resolve(link)
            ?: throw AttendanceException("“教务考勤”入口无法解析")
        // 门户给出的 OAuth2 入口可能是 http；one/adwmh 都支持 https，统一升级，
        // 否则会被宿主的 network security policy 拦成 CLEARTEXT。
        val url = if (resolved.host.endsWith("ahu.edu.cn") && resolved.scheme == "http") {
            resolved.newBuilder().scheme("https").port(443).build()
        } else resolved

        val finalUrl = followPortal(url)
        val token = extractToken(finalUrl)
            ?: throw AttendanceAuthException("教务登录已过期，请先在安大通中重新登录")
        cachedToken = CachedToken(token, jwtExpiry(token))
        return token
    }

    private fun followPortal(initial: HttpUrl): HttpUrl {
        var url = initial
        repeat(10) {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val location = response.header("Location")
                if (response.code in 300..399 && location != null) {
                    val next = url.resolve(location)
                        ?: throw AttendanceException("教务跳转地址无法解析")
                    url = if (next.host.endsWith("ahu.edu.cn") && next.scheme == "http") {
                        next.newBuilder().scheme("https").port(443).build()
                    } else next
                } else {
                    return url
                }
            }
        }
        throw AttendanceException("教务跳转次数过多")
    }

    private fun extractToken(url: HttpUrl): String? {
        url.queryParameter("token")?.takeIf { it.isNotBlank() }?.let { return it }
        val fragment = url.fragment ?: return null
        val query = fragment.substringAfter('?', "")
        if (query.isBlank()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("token=") }
            ?.substringAfter('=')
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
    }

    private fun jwtExpiry(token: String): Long {
        val fallback = System.currentTimeMillis() + 10 * 60_000
        return runCatching {
            val payload = token.split('.').getOrNull(1) ?: return fallback
            val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            val exp = JSONObject(json).optLong("exp", 0L)
            if (exp > 0) exp * 1000 else fallback
        }.getOrDefault(fallback)
    }

    private fun requestPage(token: String, status: Int, page: Int): Pair<Int, String> {
        val payload = JSONObject().apply {
            put("classWaterBean", JSONObject().put("status", status))
            put("current", page)
            put("pageSize", PAGE_SIZE)
            put("subjectBean", JSONObject().put("sCode", ""))
            put("timeCondition", "")
        }
        val request = Request.Builder()
            .url(PAGE_URL)
            .header("Content-Type", "application/json")
            .header("synjones-auth", "bearer $token")
            .header("Authorization", BASIC_AUTH)
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        return client.newCall(request).execute().use { response ->
            response.code to (response.body?.string().orEmpty())
        }
    }

    private fun findAttendanceApp(value: Any?): JSONObject? {
        when (value) {
            is JSONObject -> {
                if (value.optString("name") == "教务考勤" || value.optInt("id", 0) == 2035) {
                    return value
                }
                for (key in value.keys()) {
                    findAttendanceApp(value.opt(key))?.let { return it }
                }
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    findAttendanceApp(value.opt(i))?.let { return it }
                }
            }
        }
        return null
    }
}
