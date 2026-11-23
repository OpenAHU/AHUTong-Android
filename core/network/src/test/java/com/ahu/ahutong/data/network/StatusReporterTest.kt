package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.common.diagnostics.DiagnosticSanitizer
import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusReporterTest {

    @Test
    fun `throttle enforces error cooldown, ok cooldown, recovery bypass, and sliding window cap`() {
        var nowMs = 1_000_000L
        val throttle = StatusUploadThrottle(
            okCooldownMs = 300_000L,
            errorCooldownMs = 120_000L,
            windowMs = 60_000L,
            maxPerWindow = 2,
            nowMillis = { nowMs }
        )

        // First error allowed
        assertTrue(throttle.tryAcquire(StatusEndpoint.AHU_JW, ReportStatus.ERROR))
        // Repeated error within 120s blocked
        nowMs += 30_000L
        assertFalse(throttle.tryAcquire(StatusEndpoint.AHU_JW, ReportStatus.ERROR))
        // Recovery OK allowed immediately after error (2nd event in 60s window)
        nowMs += 5_000L
        assertTrue(throttle.tryAcquire(StatusEndpoint.AHU_JW, ReportStatus.OK))
        // 3rd event in 60s window blocked even on different status
        nowMs += 10_000L
        assertFalse(throttle.tryAcquire(StatusEndpoint.AHU_JW, ReportStatus.OK))

        // Different endpoint has independent bucket
        assertTrue(throttle.tryAcquire(StatusEndpoint.AHUTONG_COMMON, ReportStatus.OK))
        // Consecutive OK within 300s blocked
        nowMs += 70_000L
        assertFalse(throttle.tryAcquire(StatusEndpoint.AHUTONG_COMMON, ReportStatus.OK))
        // Allowed after 300s cooldown
        nowMs += 240_000L
        assertTrue(throttle.tryAcquire(StatusEndpoint.AHUTONG_COMMON, ReportStatus.OK))

        // Error allowed again after 120s error cooldown from first error
        assertTrue(throttle.tryAcquire(StatusEndpoint.AHU_JW, ReportStatus.ERROR))
    }

    @Test
    fun `buildPayloadJson emits fixed upload schema and scrubs secrets`() {
        DiagnosticSanitizer.rememberPassword("MySecretPass!99")
        val json = StatusReporter.buildPayloadJson(
            status = ReportStatus.ERROR,
            action = "getSchedule",
            message = "login fail MySecretPass!99\nline2",
            latencyMs = 142L,
            errorType = "Server",
            nowMs = 1_763_899_200_000L
        )

        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals("error", obj.get("status").asString)
        assertEquals(142L, obj.get("latency").asLong)
        assertTrue(obj.get("timestamp").asString.endsWith("Z"))
        assertFalse(obj.get("message").asString.contains("MySecretPass!99"))
        assertFalse(obj.get("message").asString.contains("\n"))

        val meta = obj.getAsJsonObject("meta")
        assertEquals("getSchedule", meta.get("action").asString)
        assertEquals("Server", meta.get("errorType").asString)
    }

    @Test
    fun `sendSyncForTest posts expected JSON over HTTP`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        server.start()
        try {
            val code = StatusReporter.sendSyncForTest(
                targetUrl = server.url("/api/upload/ahu-common").toString(),
                status = ReportStatus.OK,
                action = "apkUpdateCheck",
                message = "apkUpdateCheck ok",
                latencyMs = 58L,
                errorType = null
            )
            assertEquals(200, code)
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/api/upload/ahu-common", recorded.path)
            assertTrue(recorded.getHeader("Content-Type")?.startsWith("application/json") == true)
            val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
            assertEquals("ok", body.get("status").asString)
            assertEquals("apkUpdateCheck ok", body.get("message").asString)
            assertEquals(58L, body.get("latency").asLong)
        } finally {
            server.shutdown()
        }
    }
}
