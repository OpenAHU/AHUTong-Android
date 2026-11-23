package com.ahu.ahutong.data.network

import com.ahu.ahutong.data.status.ServiceHealth
import com.ahu.ahutong.data.status.ServiceStatusGroup
import com.ahu.ahutong.data.status.ServiceStatusIncident
import com.ahu.ahutong.data.status.ServiceStatusMonitor
import com.ahu.ahutong.data.status.ServiceStatusSnapshot
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path

internal interface StatusPageRetrofitApi {
    @GET("status/{slug}")
    suspend fun getStatusPageHtml(@Path("slug") slug: String): ResponseBody

    @GET("api/status-page/heartbeat/{slug}")
    suspend fun getHeartbeat(@Path("slug") slug: String): StatusHeartbeatResponseDto
}

internal data class StatusPageResponseDto(
    @SerializedName("config") val config: StatusPageConfigDto? = null,
    @SerializedName("incidents") val incidents: List<StatusPageIncidentDto>? = null,
    @SerializedName("incident") val incident: StatusPageIncidentDto? = null,
    @SerializedName("publicGroupList") val publicGroupList: List<StatusPageGroupDto>? = null
)

internal data class StatusPageConfigDto(
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("autoRefreshInterval") val autoRefreshInterval: Int? = null
)

internal data class StatusPageIncidentDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("content") val content: String? = null,
    @SerializedName("style") val style: String? = null,
    @SerializedName("pin") val pin: Boolean? = null,
    @SerializedName("createdDate") val createdDate: String? = null,
    @SerializedName("lastUpdatedDate") val lastUpdatedDate: String? = null
)

internal data class StatusPageGroupDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("weight") val weight: Int? = null,
    @SerializedName("monitorList") val monitorList: List<StatusPageMonitorDto>? = null
)

internal data class StatusPageMonitorDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null
)

internal data class StatusHeartbeatResponseDto(
    @SerializedName("heartbeatList") val heartbeatList: Map<String, List<StatusHeartbeatPointDto>>? = null,
    @SerializedName("uptimeList") val uptimeList: Map<String, Double>? = null,
    @SerializedName("errorRateList") val errorRateList: Map<String, Double>? = null
)

internal data class StatusHeartbeatPointDto(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("time") val time: String? = null,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("ping") val ping: Double? = null
)

/**
 * Fetches monitor metadata/names from the status-page HTML (`window.preloadData`) and live
 * telemetry/heartbeats from `/api/status-page/heartbeat/{slug}` via [AhuHttp.retrofit].
 */
class StatusPageRemoteSource(
    baseUrl: String = DEFAULT_BASE_URL,
    private val slug: String = DEFAULT_SLUG
) {
    private val api: StatusPageRetrofitApi = AhuHttp.retrofit(baseUrl) {
        connectTimeout(10, TimeUnit.SECONDS)
        readTimeout(15, TimeUnit.SECONDS)
    }.create(StatusPageRetrofitApi::class.java)

    private val gson = Gson()

    suspend fun loadSnapshot(): ServiceStatusSnapshot = withContext(Dispatchers.IO) {
        coroutineScope {
            val htmlDeferred = async { api.getStatusPageHtml(slug).use { it.string() } }
            val heartbeatDeferred = async { api.getHeartbeat(slug) }
            val page = parsePreloadDataFromHtml(htmlDeferred.await())
            assembleSnapshot(
                page = page,
                heartbeat = heartbeatDeferred.await()
            )
        }
    }

    internal fun parsePreloadDataFromHtml(html: String): StatusPageResponseDto {
        val objectLiteral = extractPreloadObjectLiteral(html)
        val normalizedJson = jsObjectLiteralToJson(objectLiteral)
        return gson.fromJson(normalizedJson, StatusPageResponseDto::class.java)
            ?: throw IllegalStateException("状态页 HTML 预加载数据为空")
    }

    internal fun assembleSnapshot(
        page: StatusPageResponseDto,
        heartbeat: StatusHeartbeatResponseDto
    ): ServiceStatusSnapshot {
        val rawGroups = page.publicGroupList
            ?: throw IllegalStateException("状态页响应缺少服务分组")
        val heartbeatsById = heartbeat.heartbeatList
            ?: throw IllegalStateException("心跳响应缺少状态数据")
        val uptimesByKey = heartbeat.uptimeList.orEmpty()
        val errorRatesById = heartbeat.errorRateList.orEmpty()

        val rawIncidents = buildList {
            page.incidents?.let { addAll(it) }
            if (page.incidents.isNullOrEmpty() && page.incident != null) {
                add(page.incident)
            }
        }
        val incidents = rawIncidents.mapNotNull { item ->
            val title = item.title?.trim().orEmpty()
            val content = item.content?.trim().orEmpty()
            if (title.isEmpty() && content.isEmpty()) {
                null
            } else {
                ServiceStatusIncident(
                    id = item.id ?: 0,
                    title = title,
                    content = content,
                    style = item.style.orEmpty().ifBlank { "info" },
                    pin = item.pin ?: true,
                    createdDate = item.createdDate.orEmpty(),
                    lastUpdatedDate = item.lastUpdatedDate
                )
            }
        }

        val groups = rawGroups
            .sortedWith(compareBy<StatusPageGroupDto> { it.weight ?: Int.MAX_VALUE }.thenBy { it.id ?: Int.MAX_VALUE })
            .mapNotNull { group ->
                val groupId = group.id ?: return@mapNotNull null
                val monitors = group.monitorList.orEmpty().mapNotNull { monitor ->
                    val monitorId = monitor.id ?: return@mapNotNull null
                    val idKey = monitorId.toString()
                    val latestPoint = heartbeatsById[idKey]?.lastOrNull()
                    val monitorType = monitor.type.orEmpty().ifBlank { "unknown" }
                    val errorRate = if (monitorType == UPLOAD_MONITOR_TYPE) {
                        errorRatesById[idKey]
                    } else {
                        null
                    }
                    ServiceStatusMonitor(
                        id = monitorId,
                        name = monitor.name?.trim().orEmpty().ifEmpty { "服务 #$monitorId" },
                        type = monitorType,
                        health = ServiceHealth.fromCode(latestPoint?.status),
                        uptime24h = uptimesByKey["${idKey}_24"],
                        latestPingMs = latestPoint?.ping?.roundToInt()?.takeIf { it >= 0 },
                        errorRate = errorRate,
                        lastCheckTime = latestPoint?.time
                    )
                }
                if (monitors.isEmpty()) {
                    null
                } else {
                    ServiceStatusGroup(
                        id = groupId,
                        name = group.name?.trim().orEmpty().ifEmpty { "服务" },
                        monitors = monitors
                    )
                }
            }

        return ServiceStatusSnapshot(
            title = page.config?.title?.trim().orEmpty().ifEmpty { DEFAULT_TITLE },
            autoRefreshSeconds = (page.config?.autoRefreshInterval ?: DEFAULT_REFRESH_SECONDS).coerceAtLeast(30),
            incidents = incidents,
            groups = groups
        )
    }

    internal companion object {
        const val DEFAULT_BASE_URL = "https://ahustatus.muxyang.com/"
        const val DEFAULT_SLUG = "ahutong"
        private const val DEFAULT_TITLE = "安大通服务状态"
        private const val DEFAULT_REFRESH_SECONDS = 300
        private const val UPLOAD_MONITOR_TYPE = "upload"
        private const val PRELOAD_MARKER = "window.preloadData"

        internal fun extractPreloadObjectLiteral(html: String): String {
            val markerIndex = html.indexOf(PRELOAD_MARKER)
            if (markerIndex < 0) {
                throw IllegalStateException("状态页 HTML 中未找到 preloadData")
            }
            val eqIndex = html.indexOf('=', markerIndex + PRELOAD_MARKER.length)
            val startBrace = if (eqIndex >= 0) html.indexOf('{', eqIndex) else -1
            if (startBrace < 0) {
                throw IllegalStateException("状态页 preloadData 格式无效")
            }

            var depth = 0
            var quote: Char? = null
            var escaped = false
            for (index in startBrace until html.length) {
                val ch = html[index]
                if (quote != null) {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == quote) {
                        quote = null
                    }
                    continue
                }
                when (ch) {
                    '\'', '"' -> quote = ch
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return html.substring(startBrace, index + 1)
                        }
                    }
                }
            }
            throw IllegalStateException("状态页 preloadData 对象未闭合")
        }

        /**
         * Uptime Kuma embeds `window.preloadData` via `jsesc(..., { isScriptContext: true })`,
         * which emits single-quoted keys/values and `\xHH` escapes that strict JSON rejects.
         */
        internal fun jsObjectLiteralToJson(jsObject: String): String {
            val out = StringBuilder(jsObject.length + 32)
            var i = 0
            while (i < jsObject.length) {
                val ch = jsObject[i]
                when (ch) {
                    '\'' -> {
                        out.append('"')
                        i++
                        while (i < jsObject.length && jsObject[i] != '\'') {
                            val c = jsObject[i]
                            if (c == '\\' && i + 1 < jsObject.length) {
                                val next = jsObject[i + 1]
                                when {
                                    next == '\'' -> {
                                        out.append('\'')
                                        i += 2
                                    }
                                    next == '"' -> {
                                        out.append("\\\"")
                                        i += 2
                                    }
                                    next == 'x' && i + 3 < jsObject.length &&
                                        isHex(jsObject[i + 2]) && isHex(jsObject[i + 3]) -> {
                                        out.append("\\u00")
                                            .append(jsObject[i + 2])
                                            .append(jsObject[i + 3])
                                        i += 4
                                    }
                                    else -> {
                                        out.append('\\').append(next)
                                        i += 2
                                    }
                                }
                            } else if (c == '"') {
                                out.append("\\\"")
                                i++
                            } else {
                                out.append(c)
                                i++
                            }
                        }
                        if (i >= jsObject.length) {
                            throw IllegalStateException("状态页字符串字面量未闭合")
                        }
                        out.append('"')
                        i++
                    }
                    '"' -> {
                        out.append('"')
                        i++
                        var esc = false
                        while (i < jsObject.length) {
                            val c = jsObject[i]
                            out.append(c)
                            i++
                            if (esc) {
                                esc = false
                            } else if (c == '\\') {
                                esc = true
                            } else if (c == '"') {
                                break
                            }
                        }
                    }
                    else -> {
                        out.append(ch)
                        i++
                    }
                }
            }
            return out.toString()
        }

        private fun isHex(ch: Char): Boolean =
            ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'
    }
}
