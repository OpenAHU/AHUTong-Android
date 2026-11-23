package com.ahu.ahutong.data.crawler.api.one

import android.util.Log
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.model.DormitoryInfo
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.toAhuError
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 智慧安大「住宿信息」接口。
 *
 * POST /tp_up/up/subgroup/getQinshiInfo，空 JSON body，仅要求 one.ahu.edu.cn 的
 * SESSION cookie（中央 CAS 登录态）。复用全局 CookieManager cookie jar，与登录
 * 流程共享会话。
 *
 * 关键流程：app 登录的是中央 CAS（CASTGC 在 one.ahu.edu.cn 域下），但平时只换取
 * 教务等服务票据，jar 里未必有智慧安大门户的 SESSION；因此拉取前先走一遍门户
 * CAS 入口（GET one.ahu.edu.cn/cas/login?service=tp_up/view），让 CASTGC 自动
 * 换发门户 SESSION（cookie jar 自动收下）。TGC 失效则落回 CAS 登录页，映射为
 * Unauthorized，交由上层静默跳过——不触发任何交互式重新登录。
 */
object QinshiInfoApi {

    private const val TAG = "QinshiInfoApi"
    private const val PORTAL_VIEW = "https://one.ahu.edu.cn/tp_up/view?m=up"
    private const val PORTAL_CAS_LOGIN = "https://one.ahu.edu.cn/cas/login"

    private val client by lazy {
        AhuHttp.plain(
            connectTimeoutSeconds = 15,
            readTimeoutSeconds = 30,
            callTimeoutSeconds = 45,
            retryOnConnectionFailure = false,
            followRedirects = false,
            followSslRedirects = false
        )
            .cookieJar(CookieManager.cookieJar)
            .build()
    }

    /**
     * 拉取住宿信息。不负责缓存与刷新策略，由调用方（AHURepository）决定。
     *
     * 流程：
     * 1. ensurePortalSession() — GET 门户 CAS 入口，激活 TGC→SESSION 换发。
     * 2. POST getQinshiInfo。
     */
    suspend fun fetch(): AhuResult<DormitoryInfo> = withContext(Dispatchers.IO) {
        when (val prepared = ensurePortalSession()) {
            is PortalPrep.Failure -> return@withContext prepared.result
            PortalPrep.Ready -> Unit
        }
        val request = Request.Builder()
            .url("https://one.ahu.edu.cn/tp_up/up/subgroup/getQinshiInfo")
            .header("User-Agent", JwxtApi.BROWSER_USER_AGENT)
            .header("Origin", "https://one.ahu.edu.cn")
            .header("Referer", PORTAL_VIEW)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .post("{}".toRequestBody("application/json;charset=UTF-8".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "getQinshiInfo HTTP ${response.code}")
                val result: AhuResult<DormitoryInfo> = when {
                    response.code == 302 -> AhuResult.Failure(AhuError.Unauthorized("智慧安大登录已过期"))
                    !response.isSuccessful -> AhuResult.Failure(
                        AhuError.Unknown("住宿信息接口不可用（HTTP ${response.code}）")
                    )
                    else -> {
                        val body = response.body?.string().orEmpty()
                        Log.d(TAG, "getQinshiInfo body: ${body.take(300)}")
                        val parsed = parse(body)
                        if (parsed != null) AhuResult.Success(parsed)
                        else AhuResult.Failure(AhuError.ProtocolChanged("住宿信息响应格式异常"))
                    }
                }
                result
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.w(TAG, "getQinshiInfo request failed", error)
            AhuResult.Failure(error.toAhuError())
        }
    }

    /** 容错解析：字段缺失、非 JSON、平铺结构不符都返回 null。 */
    internal fun parse(body: String): DormitoryInfo? {
        return runCatching {
            val obj = JsonParser.parseString(body).asJsonObject
            val building = obj.get("NAME")?.asString.orEmpty()
            if (building.isBlank()) return null
            DormitoryInfo(
                campus = obj.get("XQ")?.asString.orEmpty(),
                building = building,
                room = obj.get("SSFJH")?.asString.orEmpty(),
                resident = obj.get("SFZS")?.asString == "1",
                cachedAt = System.currentTimeMillis()
            )
        }.getOrNull()
    }

    private sealed interface PortalPrep {
        object Ready : PortalPrep
        data class Failure(val result: AhuResult<DormitoryInfo>) : PortalPrep
    }

    /**
     * GET 门户 CAS 入口。若 jar 中的 CASTGC 有效，CAS 会 302 → ticket → 门户收下
     * SESSION；followRedirects=true 让 OkHttp 自动跟完这条链。最终落点是 CAS 登录页
     * 即视为 TGC 失效。
     */
    private suspend fun ensurePortalSession(): PortalPrep = withContext(Dispatchers.IO) {
        val url = PORTAL_CAS_LOGIN.toHttpUrl().newBuilder()
            .addQueryParameter("service", PORTAL_VIEW)
            .build()
        try {
            val redirectClient = client.newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            redirectClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val finalUrl = response.request.url
                Log.d(TAG, "portal entry: HTTP ${response.code}, final=${finalUrl}")
                response.body?.close()
                if (response.code != 200) {
                    return@withContext PortalPrep.Failure(
                        AhuResult.Failure(AhuError.Unknown("门户入口 HTTP ${response.code}"))
                    )
                }
                if (finalUrl.encodedPath.substringBefore(';') == "/cas/login") {
                    Log.d(TAG, "portal entry landed on CAS login page — TGC invalid")
                    PortalPrep.Failure(
                        AhuResult.Failure(AhuError.Unauthorized("智慧安大登录已过期"))
                    )
                } else {
                    PortalPrep.Ready
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.w(TAG, "portal entry request failed", error)
            PortalPrep.Failure(AhuResult.Failure(error.toAhuError()))
        }
    }
}
