package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.google.gson.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.EnumMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class StatusEndpoint(val slug: String) {
    AHUTONG_COMMON("ahu-common"),
    AHUTONG_LOGIN("ahutong-login"),
    AHU_ADWMH("ahu-adwmh"),
    AHU_JW("ahu-jw"),
    AHU_GMISJW("ahu-gmisjw"),
    AHU_YCARD("ahu-ycard")
}

internal enum class ReportStatus(val wire: String) {
    OK("ok"),
    ERROR("error")
}

/**
 * Pure per-endpoint cooldown + sliding-window throttle so client telemetry stays well below
 * the server's 5 req / 60s limit and avoids error storms.
 */
internal class StatusUploadThrottle(
    private val okCooldownMs: Long = DEFAULT_OK_COOLDOWN_MS,
    private val errorCooldownMs: Long = DEFAULT_ERROR_COOLDOWN_MS,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxPerWindow: Int = DEFAULT_MAX_PER_WINDOW,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private data class EndpointState(
        var lastStatus: ReportStatus? = null,
        var lastOkAtMs: Long = Long.MIN_VALUE,
        var lastErrorAtMs: Long = Long.MIN_VALUE,
        val recentTimestamps: ArrayDeque<Long> = ArrayDeque()
    )

    private val states = EnumMap<StatusEndpoint, EndpointState>(StatusEndpoint::class.java)

    @Synchronized
    fun tryAcquire(endpoint: StatusEndpoint, status: ReportStatus): Boolean {
        val now = nowMillis()
        val state = states.getOrPut(endpoint) { EndpointState() }

        val cutoff = now - windowMs
        while (state.recentTimestamps.isNotEmpty() && state.recentTimestamps.first() <= cutoff) {
            state.recentTimestamps.removeFirst()
        }
        if (state.recentTimestamps.size >= maxPerWindow) {
            return false
        }

        val allowedByCooldown = when (status) {
            ReportStatus.OK ->
                state.lastStatus == ReportStatus.ERROR ||
                    state.lastOkAtMs == Long.MIN_VALUE ||
                    now - state.lastOkAtMs >= okCooldownMs
            ReportStatus.ERROR ->
                state.lastErrorAtMs == Long.MIN_VALUE ||
                    now - state.lastErrorAtMs >= errorCooldownMs
        }
        if (!allowedByCooldown) {
            return false
        }

        state.lastStatus = status
        state.recentTimestamps.addLast(now)
        if (status == ReportStatus.OK) {
            state.lastOkAtMs = now
        } else {
            state.lastErrorAtMs = now
        }
        return true
    }

    @Synchronized
    fun reset() {
        states.clear()
    }

    companion object {
        const val DEFAULT_OK_COOLDOWN_MS = 5 * 60_000L
        const val DEFAULT_ERROR_COOLDOWN_MS = 120_000L
        const val DEFAULT_WINDOW_MS = 60_000L
        const val DEFAULT_MAX_PER_WINDOW = 2
    }
}

/**
 * Asynchronous, rate-limited reporter for AHUTong status upload endpoints.
 * Telemetry failures are silently ignored so user operations are never impacted.
 */
object StatusReporter {

    const val BASE_UPLOAD_URL = "https://ahustatus.muxyang.com/api/upload/"
    private const val MAX_MESSAGE_LENGTH = 240
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val throttle = StatusUploadThrottle()

    private val client by lazy {
        AhuHttp.plain(
            connectTimeoutSeconds = 4,
            readTimeoutSeconds = 4,
            writeTimeoutSeconds = 4,
            callTimeoutSeconds = 5,
            retryOnConnectionFailure = false,
            captureDiagnostics = false
        ).build()
    }

    fun reportOk(
        endpoint: StatusEndpoint,
        action: String,
        latencyMs: Long? = null
    ) {
        enqueue(
            endpoint = endpoint,
            status = ReportStatus.OK,
            action = action,
            message = "$action ok",
            latencyMs = latencyMs,
            errorType = null
        )
    }

    internal fun sendSyncForTest(
        targetUrl: String,
        status: ReportStatus,
        action: String,
        message: String,
        latencyMs: Long?,
        errorType: String?
    ): Int {
        val payload = buildPayloadJson(
            status = status,
            action = action,
            message = message,
            latencyMs = latencyMs,
            errorType = errorType,
            nowMs = System.currentTimeMillis()
        )
        val request = Request.Builder()
            .url(targetUrl)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return client.newCall(request).execute().use { it.code }
    }

    fun reportError(
        endpoint: StatusEndpoint,
        action: String,
        error: AhuError,
        latencyMs: Long? = null
    ) {
        val errorType = error::class.java.simpleName.ifBlank { "Error" }
        val detail = when (error) {
            is AhuError.Network -> "network error"
            is AhuError.Timeout -> "timeout"
            is AhuError.Unauthorized -> return // Never report session/credential expiry as service error.
            is AhuError.ProtocolChanged -> "protocol changed: ${error.detail}"
            is AhuError.Server -> "server(${error.code}): ${error.message}"
            is AhuError.Unknown -> error.message
        }
        enqueue(
            endpoint = endpoint,
            status = ReportStatus.ERROR,
            action = action,
            message = "$action failed [$errorType]: $detail",
            latencyMs = latencyMs,
            errorType = errorType
        )
    }

    fun reportFailureMessage(
        endpoint: StatusEndpoint,
        action: String,
        errorType: String,
        detail: String,
        latencyMs: Long? = null
    ) {
        enqueue(
            endpoint = endpoint,
            status = ReportStatus.ERROR,
            action = action,
            message = "$action failed [$errorType]: $detail",
            latencyMs = latencyMs,
            errorType = errorType
        )
    }

    private fun enqueue(
        endpoint: StatusEndpoint,
        status: ReportStatus,
        action: String,
        message: String,
        latencyMs: Long?,
        errorType: String?
    ) {
        if (!throttle.tryAcquire(endpoint, status)) {
            return
        }
        val payload = buildPayloadJson(
            status = status,
            action = action,
            message = message,
            latencyMs = latencyMs,
            errorType = errorType,
            nowMs = System.currentTimeMillis()
        )
        scope.launch {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE_UPLOAD_URL${endpoint.slug}")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().close()
            }
        }
    }

    internal fun buildPayloadJson(
        status: ReportStatus,
        action: String,
        message: String,
        latencyMs: Long?,
        errorType: String?,
        nowMs: Long
    ): String {
        val sanitizedMessage = DiagnosticSanitizer.safe(message)
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .take(MAX_MESSAGE_LENGTH)

        val meta = JsonObject().apply {
            addProperty("action", action.take(64))
            if (!errorType.isNullOrBlank()) {
                addProperty("errorType", errorType.take(48))
            }
        }

        return JsonObject().apply {
            addProperty("status", status.wire)
            addProperty("message", sanitizedMessage)
            if (latencyMs != null && latencyMs >= 0L) {
                addProperty("latency", latencyMs)
            }
            addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(nowMs)))
            add("meta", meta)
        }.toString()
    }
}
