package com.ahu.ahutong.data.mail

import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudentMailMoveTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val account = "reader@example.test"
    private val mid = "persistent-message-fixture"

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        // API must enforce mutation restrictions even when its supplied client allows retries/redirects.
        client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun api(guard: () -> Unit = {}) =
        StudentMailApi(client, "current-synthetic-session", server.url("/"), guard = guard)

    private suspend fun move(service: StudentMailApi = api(), folder: Int = 4) =
        service.rpc("mbox:updateMessageInfos", StudentMailProtocol.moveMessageRequest(mid, folder, account))

    @Test fun deletionUsesExactlyOnePersistentMessageAndTrashFolder() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        move()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/js6/s", request.requestUrl!!.encodedPath)
        assertEquals("mbox:updateMessageInfos", request.requestUrl!!.queryParameter("func"))
        assertEquals("current-synthetic-session", request.requestUrl!!.queryParameter("sid"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("ids", "attrs", "needFilter", "_account", "riskHitIntercept"), body.keySet())
        assertEquals(1, body.getAsJsonArray("ids").size())
        assertTrue(body.getAsJsonArray("ids").single().asJsonPrimitive.isString)
        assertEquals(mid, body.getAsJsonArray("ids").single().asString)
        assertEquals(4, body.getAsJsonObject("attrs")["fid"].asInt)
        assertEquals(account, body["_account"].asString)
        assertFalse(body["needFilter"].asBoolean)
        assertTrue(body["riskHitIntercept"].asBoolean)
    }

    @Test fun movingDeletedMailToInboxNeverCallsPermanentDelete() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        move(folder = 1)
        val request = server.takeRequest()
        assertEquals("mbox:updateMessageInfos", request.requestUrl!!.queryParameter("func"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(1, body.getAsJsonObject("attrs")["fid"].asInt)
        assertFalse(body.has("id"))
    }

    @Test fun invalidIdentifierOrAccountCannotCreateAnUnboundedMutation() {
        for (invalid in listOf("", " ", "a\n", "a\u0000b", "a\u007fb", "a".repeat(513))) {
            assertThrows(IllegalArgumentException::class.java) {
                StudentMailProtocol.moveMessageRequest(invalid, 4, account)
            }
        }
        for (invalid in listOf("", " ", "reader\n@example.test", "reader\u0000@example.test", "a".repeat(321))) {
            assertThrows(IllegalArgumentException::class.java) {
                StudentMailProtocol.moveMessageRequest(mid, 4, invalid)
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun onlyInboxAndTrashCanBeTargets() {
        for (folder in listOf(-1, 0, 2, 3, 5, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                StudentMailProtocol.moveMessageRequest(mid, folder, account)
            }
        }
    }

    @Test fun explicitServerFailureKeepsResponseAndPersonalFieldsPrivate() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"FS_DAO_BUSINESS_MAIL_NOT_EXIST",
            "message":"private-account-and-session-fixture","var":"private-mail-fixture"}"""))
        val error = runCatching { move() }.exceptionOrNull()
        assertTrue(error is MailServiceFailure)
        assertFalse(error!!.message.orEmpty().contains("private"))
        assertFalse(error.message.orEmpty().contains("FS_DAO"))
        assertEquals(1, server.requestCount)
    }

    @Test fun onlyExplicitStringSOkConfirmsAMove() = runBlocking {
        for (body in listOf("{}", "[]", "not-json", "{\"code\":200,\"success\":true}",
            "{\"code\":\"200\",\"success\":true}", "{\"code\":true}", "{\"code\":null}",
            "{\"code\":{\"result\":\"S_OK\"}}", "{\"code\":\"s_ok\"}")) {
            server.enqueue(MockResponse().setBody(body))
            assertTrue(runCatching { move() }.exceptionOrNull() is MailMoveUncertain)
        }
    }

    @Test fun retryableHttpFailureDoesNotReplayTheMove() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        assertTrue(runCatching { move() }.exceptionOrNull() is MailMoveUncertain)
        assertEquals(1, server.requestCount)
    }

    @Test fun redirectNeverForwardsOrRepeatsAMutation() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/must-not-follow")))
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        assertTrue(runCatching { move() }.exceptionOrNull() is MailSessionExpired)
        assertEquals(1, server.requestCount)
    }

    @Test fun lostAcknowledgementRemainsUncertainAndIsNotReplayed() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_OK","padding":"${"x".repeat(4096)}"}""")
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        assertTrue(runCatching { move() }.exceptionOrNull() is MailMoveUncertain)
        assertEquals(1, server.requestCount)
    }

    @Test fun mutationBodyIsOneShot() = runBlocking {
        val oneShot = AtomicBoolean(false)
        client = client.newBuilder().addInterceptor { chain ->
            oneShot.set(chain.request().body?.isOneShot() == true)
            chain.proceed(chain.request())
        }.build()
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        move()
        assertTrue(oneShot.get())
    }

    @Test fun expiredMailboxDoesNotRetryOrRenewAMove() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_INVALID_SID","message":"private-fixture"}"""))
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        val error = runCatching { move() }.exceptionOrNull()
        assertTrue(error is MailSessionExpired)
        assertFalse(error!!.message.orEmpty().contains("private"))
        assertEquals(1, server.requestCount)
    }

    @Test fun changedAccountPreventsTheRequest() = runBlocking {
        assertTrue(runCatching { move(api { throw MailSessionExpired() }) }.exceptionOrNull() is MailSessionExpired)
        assertEquals(0, server.requestCount)
    }

    @Test fun changedAccountAfterResponseCannotPublishSuccess() = runBlocking {
        var calls = 0
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}"""))
        assertTrue(runCatching { move(api { if (++calls == 2) throw MailSessionExpired() }) }
            .exceptionOrNull() is MailSessionExpired)
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationClosesThePendingMoveWithoutRepeating() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":"S_OK"}""").setBodyDelay(3, TimeUnit.SECONDS))
        val pending = async { move() }
        withTimeout(2_000) {
            while (server.requestCount == 0) delay(10)
            delay(100)
        }
        withTimeout(1_000) { pending.cancelAndJoin() }
        assertTrue(pending.isCancelled)
        assertEquals(1, server.requestCount)
    }
}
