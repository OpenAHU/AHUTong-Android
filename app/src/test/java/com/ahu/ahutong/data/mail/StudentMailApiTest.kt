package com.ahu.ahutong.data.mail

import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudentMailApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: StudentMailApi

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false).readTimeout(3, TimeUnit.SECONDS).build()
        api = StudentMailApi(client, "synthetic-session", server.url("/"), "synthetic-device")
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    @Test fun sendsCapturedRpcShapeAndKeepsSessionInQuery() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_OK","var":[],"total":0}"""))
        api.rpc("mbox:listMessages", StudentMailProtocol.listRequest(1, 30))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/js6/s", request.requestUrl!!.encodedPath)
        assertEquals("mbox:listMessages", request.requestUrl!!.queryParameter("func"))
        assertEquals("synthetic-session", request.requestUrl!!.queryParameter("sid"))
        assertEquals("mail.stu.ahu.edu.cn", request.requestUrl!!.queryParameter("_host"))
        assertEquals("zh", request.getHeader("lingxi-language"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(30, body["start"].asInt)
        assertEquals(1, body["fid"].asInt)
    }

    @Test fun deliveryIsNeverReplayedAfterRetryableHttpResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("""{"code":"S_OK","var":{},"savedSent":{"mid":"fixture"}}"""))
        val failure = runCatching {
            api.rpc("mbox:compose", StudentMailProtocol.composeRequest(MailDraft(to = "recipient@example.test"), "sender@example.test", true),
                mapOf("action" to "deliver", "l" to "compose"))
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("QIYE_MAIL", request.getHeader("mail-server-type"))
    }

    @Test fun reportsUnauthorizedWithoutFollowingLoginOrRetrying() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("private-response"))
        val failure = runCatching { api.request("cowork/api/biz/enter/accountInfo") }.exceptionOrNull()
        assertTrue(failure is MailSessionExpired)
        assertEquals(1, server.requestCount)
        assertFalse(failure!!.message.orEmpty().contains("private-response"))
    }

    @Test fun expiredReadUsesNewSidAfterOneExplicitRenewal() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_INVALID_SESSION"}"""))
        server.enqueue(MockResponse().setBody("""{"code":"S_OK","var":[],"total":0}"""))
        var current = api
        var renewals = 0
        recoverMailRead(renew = {
            renewals++
            current = StudentMailApi(client, "renewed-synthetic-session", server.url("/"))
        }) { current.rpc("mbox:listMessages", StudentMailProtocol.listRequest(1)) }
        assertEquals(1, renewals)
        assertEquals(2, server.requestCount)
        assertEquals("synthetic-session", server.takeRequest().requestUrl!!.queryParameter("sid"))
        assertEquals("renewed-synthetic-session", server.takeRequest().requestUrl!!.queryParameter("sid"))
    }

    @Test fun malformedJsonNeverLeaksResponseText() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>private-session-value</html>"))
        val failure = runCatching { api.request("cowork/api/biz/enter/accountInfo") }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(failure!!.message.orEmpty().contains("private-session-value"))
    }

    @Test fun cancellationClosesThePendingSocket() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(start = CoroutineStart.UNDISPATCHED) { api.request("cowork/api/biz/enter/accountInfo") }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        withTimeout(2_000) {
            while (client.dispatcher.runningCallsCount() != 0) delay(10)
        }
        assertTrue(pending.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectsOldIdentityBeforeRequestLeavesDevice() = runBlocking {
        val guarded = StudentMailApi(client, "synthetic-session", server.url("/"), guard = { throw MailSessionExpired() })
        assertTrue(runCatching { guarded.request("cowork/api/biz/enter/accountInfo") }.exceptionOrNull() is MailSessionExpired)
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsIdentityChangedWhileResponseWasLoading() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_OK","var":[]}"""))
        var checks = 0
        val guarded = StudentMailApi(client, "synthetic-session", server.url("/"), guard = {
            if (++checks > 1) throw MailSessionExpired()
        })
        assertTrue(runCatching { guarded.rpc("mbox:getAllFolders", StudentMailProtocol.foldersRequest()) }.exceptionOrNull() is MailSessionExpired)
        assertEquals(1, server.requestCount)
    }
}
