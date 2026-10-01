package com.ahu.ahutong.data.mail

import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudentMailBodyImagesTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val gif = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7")
    private val imageAttachment = MailAttachment("3", "fixture.gif", 42, messageId = "message", partId = "3",
        contentId = "<image+fixture@example.test>", contentLocation = "image-fixture.gif",
        contentType = "image/gif", inlined = true)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).cookieJar(CookieJar.NO_COOKIES).build()
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdownNow()
        server.shutdown()
    }

    private fun loader(
        allowExternal: Boolean = false,
        guard: () -> Unit = {},
        attachments: List<MailAttachment> = listOf(imageAttachment),
        maxBytes: Int = 12 * 1024 * 1024,
        maxDocumentBytes: Long = 32L * 1024 * 1024,
        maxResources: Int = 40,
        externalAllowed: (HttpUrl) -> Boolean = { it.host == "localhost" && it.port == server.port }
    ) = StudentMailBodyImages(client, "runtime-session", server.url("/"), "message", attachments,
        allowExternal, guard, client, externalAllowed, maxBytes, maxDocumentBytes, maxResources)

    private fun image() = MockResponse().setHeader("Content-Type", "image/gif;charset=UTF-8")
        .setBody(Buffer().write(gif))

    @Test fun rendersRasterDataImageWithoutExternalOptInOrNetwork() {
        val source = "data:image/gif;base64," + Base64.getEncoder().encodeToString(gif)
        loader().use { assertArrayEquals(gif, it.load(source)!!.bytes) }
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsSvgMalformedOversizeAndMislabeledDataImages() {
        loader().use { images ->
            assertNull(images.load("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg/>".toByteArray())))
            assertNull(images.load("data:image/png;base64," + Base64.getEncoder().encodeToString("<svg/>".toByteArray())))
            assertNull(images.load("data:image/gif;base64,invalid!"))
            assertNull(images.load("data:image/gif,GIF89a"))
            val oversized = ByteArray(2 * 1024 * 1024 + 1).also { gif.copyInto(it) }
            assertNull(images.load("data:image/gif;base64," + Base64.getEncoder().encodeToString(oversized)))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun dataImagesRespectDocumentBudgetAndAccountIdentity() {
        val source = "data:image/gif;base64," + Base64.getEncoder().encodeToString(gif)
        loader(maxDocumentBytes = gif.size.toLong()).use { images ->
            assertNotNull(images.load(source))
            assertNull(images.load(source))
        }
        loader(guard = { error("changed account") }).use { assertNull(it.load(source)) }
        assertEquals(0, server.requestCount)
    }

    @Test fun loadsCidFromMetadataUsingRuntimeSessionAndKnownMessagePart() {
        server.enqueue(image())
        loader().use { images -> assertArrayEquals(gif, images.load("cid:image%2Bfixture@example.test")!!.bytes) }
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/js6/s", request.requestUrl!!.encodedPath)
        assertEquals("mbox:getMessageData", request.requestUrl!!.queryParameter("func"))
        assertEquals("runtime-session", request.requestUrl!!.queryParameter("sid"))
        assertEquals("message", request.requestUrl!!.queryParameter("mid"))
        assertEquals("3", request.requestUrl!!.queryParameter("part"))
        assertEquals("inline", request.requestUrl!!.queryParameter("mode"))
    }

    @Test fun resolvesExplicitContentLocationAndLiteralPlusCid() {
        server.enqueue(image())
        server.enqueue(image())
        loader().use { images ->
            assertNotNull(images.load("image-fixture.gif"))
            assertNotNull(images.load("CID:<image+fixture@example.test>"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun rewritesMailboxImageUrlDiscardingSuppliedSid() {
        server.enqueue(image())
        loader().use { images ->
            assertNotNull(images.load("http://mail.stu.ahu.edu.cn/js6/s?func=mbox:getMessageData&mid=message&part=3&sid=obsolete-session"))
        }
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("runtime-session", request.requestUrl!!.queryParameter("sid"))
        assertFalse(request.path.orEmpty().contains("obsolete-session"))
    }

    @Test fun neverFetchesUnmatchedCidDifferentMessageOrArbitraryMailboxUrl() {
        loader(allowExternal = true).use { images ->
            assertNull(images.load("cid:missing"))
            assertNull(images.load("https://mail.stu.ahu.edu.cn/js6/s?func=mbox:getMessageData&mid=other&part=3"))
            assertNull(images.load("https://mail.stu.ahu.edu.cn/private/path?sid=untrusted"))
            assertNull(images.load("file:///sdcard/private.png"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsAttachmentOwnedByOtherMessageAndNonImagePart() {
        loader(attachments = listOf(imageAttachment.copy(messageId = "other"))).use { images ->
            assertNull(images.load("cid:image+fixture@example.test"))
        }
        loader(attachments = listOf(imageAttachment.copy(contentType = "text/html"))).use { images ->
            assertNull(images.load("cid:image+fixture@example.test"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun externalImagesRequireExplicitOptInAndAreAnonymous() {
        loader().use { assertNull(it.load(server.url("/image.gif").toString())) }
        assertEquals(0, server.requestCount)
        server.enqueue(image().addHeader("Set-Cookie", "tracking=fixture; Path=/"))
        server.enqueue(image())
        loader(allowExternal = true).use { images ->
            assertNotNull(images.load(server.url("/image.gif").toString()))
            assertNotNull(images.load(server.url("/another.gif").toString()))
        }
        repeat(2) {
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Referer"))
            assertNull(request.requestUrl!!.queryParameter("sid"))
            assertEquals("no-store", request.getHeader("Cache-Control"))
        }
    }

    @Test fun redirectsStayAnonymousAndRejectEmbeddedCredentials() {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/final.gif")))
        server.enqueue(image())
        loader(allowExternal = true).use { assertNotNull(it.load(server.url("/start.gif").toString())) }
        assertEquals(2, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", server.url("/private.gif").newBuilder().username("user").password("password").build()))
        loader(allowExternal = true).use { assertNull(it.load(server.url("/credentials.gif").toString())) }
        assertEquals(3, server.requestCount)
    }

    @Test fun redirectsToMailboxNeverCarryRuntimeCredentials() {
        server.enqueue(MockResponse().setResponseCode(302)
            .setHeader("Location", "https://mail.stu.ahu.edu.cn/js6/s?func=mbox:getMessageData&mid=message&part=3"))
        loader(allowExternal = true).use { assertNull(it.load(server.url("/start.gif").toString())) }
        assertEquals(1, server.requestCount)
    }

    @Test fun blocksPrivateNetworkByDefaultPolicy() {
        StudentMailBodyImages(client, "runtime-session", server.url("/"), "message", emptyList(),
            true, {}, client).use { images ->
            assertNull(images.load(server.url("/private.gif").toString()))
            assertNull(images.load("http://127.0.0.1/image.gif"))
            assertNull(images.load("http://192.168.1.1/image.gif"))
            assertNull(images.load("http://[::1]/image.gif"))
            assertNull(images.load("http://[fc00::1]/image.gif"))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun rejectsSvgHtmlAndMislabeledActiveContent() {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/svg+xml").setBody("<svg/>"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<form>login</form>"))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("<svg/>"))
        loader(allowExternal = true).use { images ->
            repeat(3) { assertNull(images.load(server.url("/untrusted-$it").toString())) }
        }
    }

    @Test fun limitsDeclaredAndStreamingSizeAndWholeDocumentBudget() {
        server.enqueue(image())
        server.enqueue(MockResponse().setHeader("Content-Type", "image/gif").setChunkedBody(Buffer().write(gif), 3))
        loader(allowExternal = true, maxBytes = 12).use { images ->
            assertNull(images.load(server.url("/declared.gif").toString()))
            assertNull(images.load(server.url("/streamed.gif").toString()))
        }
        server.enqueue(image())
        server.enqueue(image())
        loader(allowExternal = true, maxDocumentBytes = gif.size.toLong()).use { images ->
            assertNotNull(images.load(server.url("/first.gif").toString()))
            assertNull(images.load(server.url("/second.gif").toString()))
        }
    }

    @Test fun capsResourceCountAndDoesNotRetryFailedLoads() {
        server.enqueue(MockResponse().setResponseCode(503))
        loader(allowExternal = true, maxResources = 1).use { images ->
            assertNull(images.load(server.url("/failed.gif").toString()))
            assertNull(images.load(server.url("/never.gif").toString()))
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun checksIdentityBeforeAndAfterResponseWithoutReturningOldAccountBytes() {
        val active = AtomicBoolean(true)
        server.enqueue(image())
        var checks = 0
        loader(allowExternal = true, guard = {
            checks += 1
            check(active.get())
            if (checks == 4) active.set(false)
        }).use { images -> assertNull(images.load(server.url("/image.gif").toString())) }
        assertEquals(1, server.requestCount)
        loader(allowExternal = true, guard = { error("changed account") }).use { images ->
            assertNull(images.load(server.url("/never.gif").toString()))
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun closingLoaderCancelsSocketDuringBodyAndPreventsFurtherRequests() {
        server.enqueue(image().throttleBody(1, 500, TimeUnit.MILLISECONDS))
        val images = loader(allowExternal = true)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<MailBodyImage?> { images.load(server.url("/slow.gif").toString()) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            images.close()
            assertNull(future.get(2, TimeUnit.SECONDS))
            assertNull(images.load(server.url("/after-close.gif").toString()))
            assertEquals(1, server.requestCount)
        } finally { images.close(); executor.shutdownNow() }
    }
}
