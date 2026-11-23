package com.ahu.ahutong.data.network

import com.ahu.ahutong.data.status.ServiceHealth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

class StatusPageRemoteSourceTest {

    @Test
    fun `loadSnapshot parses monitor names from raw HTML preloadData and merges heartbeat telemetry`() = runBlocking {
        val html = """
            <!DOCTYPE html><html lang="en"><head>
            <title>安大通服务状态</title>
            <script id="preload-data" data-json="{}">
                window.preloadData = {'config':{'slug':'ahutong','title':'\u5B89\u5927\u901A\u670D\u52A1\u72B6\u6001','autoRefreshInterval':300,'footerText':'<div style="color: #666;">\'test\' \x3Cspan>ok\x3C/span></div>'},'incidents':[{'id':7,'title':'\u7EF4\u62A4\u901A\u77E5','content':'14:00 "升级" \'维护\'','style':'warning','pin':true,'createdDate':'2026-11-23 05:00:00'}],'publicGroupList':[{'id':4,'name':'\u8F6F\u4EF6\u72B6\u6001\u9065\u6D4B','weight':4,'monitorList':[{'id':15,'name':'\u5B89\u5927\u901A-\u4E00\u822C\u4E1A\u52A1','type':'upload'},{'id':16,'name':'\u5B89\u5927\u901A-\u767B\u5F55\u670D\u52A1','type':'upload'}]},{'id':1,'name':'\u5B89\u5927\u901A-\u8F6F\u4EF6\u4E1A\u52A1\u670D\u52A1','weight':2,'monitorList':[{'id':2,'name':'\u8F6F\u4EF6\u66F4\u65B0\u670D\u52A1\u5668','type':'http'},{'id':7,'name':'AI\u670D\u52A1','type':'http'}]}],'maintenanceList':[]};
            </script>
            </head><body></body></html>
        """.trimIndent()

        val heartbeatJson = """
            {
              "heartbeatList": {
                "2": [
                  {"status": 0, "time": "2026-11-23 05:10:00.000", "msg": "old", "ping": 120},
                  {"status": 1, "time": "2026-11-23 05:12:07.682", "msg": "", "ping": 18.4}
                ],
                "7": [
                  {"status": 0, "time": "2026-11-23 05:11:00.000", "msg": "timeout", "ping": null}
                ],
                "15": [
                  {"status": 1, "time": "2026-11-23 05:12:05.000", "msg": "", "ping": 42}
                ],
                "16": []
              },
              "uptimeList": {
                "2_24": 1.0,
                "7_24": 0.92,
                "15_24": 0.995,
                "16_24": 1.0
              },
              "errorRateList": {
                "2": 0.5,
                "15": 0.032
              }
            }
        """.trimIndent()

        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/status/ahutong" -> MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html; charset=utf-8")
                    .setBody(html)
                "/api/status-page/heartbeat/ahutong" -> MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(heartbeatJson)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()

        try {
            val source = StatusPageRemoteSource(baseUrl = server.url("/").toString(), slug = "ahutong")
            val snapshot = source.loadSnapshot()

            assertEquals("安大通服务状态", snapshot.title)
            assertEquals(300, snapshot.autoRefreshSeconds)
            assertEquals(1, snapshot.incidents.size)
            assertEquals("维护通知", snapshot.incidents.first().title)
            assertEquals("""14:00 "升级" '维护'""", snapshot.incidents.first().content)

            // Groups ordered by weight ascending (weight 2 before weight 4)
            assertEquals(2, snapshot.groups.size)
            assertEquals("安大通-软件业务服务", snapshot.groups[0].name)
            assertEquals("软件状态遥测", snapshot.groups[1].name)

            val updater = snapshot.groups[0].monitors[0]
            assertEquals("软件更新服务器", updater.name)
            assertEquals(ServiceHealth.UP, updater.health)
            assertEquals(18, updater.latestPingMs)
            assertEquals(1.0, updater.uptime24h)
            // Non-upload monitors must not expose errorRate even if errorRateList contains an entry
            assertNull(updater.errorRate)

            val ai = snapshot.groups[0].monitors[1]
            assertEquals("AI服务", ai.name)
            assertEquals(ServiceHealth.DOWN, ai.health)
            assertEquals(0.92, ai.uptime24h)

            val commonTelemetry = snapshot.groups[1].monitors[0]
            assertEquals("安大通-一般业务", commonTelemetry.name)
            assertEquals(ServiceHealth.UP, commonTelemetry.health)
            assertEquals(0.032, commonTelemetry.errorRate)

            val loginTelemetry = snapshot.groups[1].monitors[1]
            assertEquals("安大通-登录服务", loginTelemetry.name)
            // Monitor with empty heartbeat list defaults safely to PENDING rather than DOWN
            assertEquals(ServiceHealth.PENDING, loginTelemetry.health)
            assertNull(loginTelemetry.errorRate)

            assertEquals(ServiceHealth.DOWN, snapshot.overallHealth)
            assertEquals(2, snapshot.upCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `parsePreloadDataFromHtml fails cleanly when preloadData marker is missing`() {
        val source = StatusPageRemoteSource(baseUrl = "https://example.com/")
        assertFailsWith<IllegalStateException> {
            source.parsePreloadDataFromHtml("<html><body>no preload</body></html>")
        }
    }
}
