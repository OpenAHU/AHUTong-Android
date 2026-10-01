package com.ahu.ahutong.sdk

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalServiceQrTest {
    @Test
    fun `HTTP errors cannot be displayed as a QR payload`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            server.start()
            val client = LocalServiceClient(server.url("/").toString().trimEnd('/'), "test-token")
            assertTrue(client.getQrcode().isFailure)
            val request = server.takeRequest()
            assertEquals("/ycard/qrcode", request.path)
            assertEquals("test-token", request.getHeader("X-AHUTONG-TOKEN"))
        }
    }

    @Test
    fun `cancelling a hanging Rust request allows the next request to finish`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody("""{"code":10000,"object":"qr"}"""))
            server.start()
            val client = LocalServiceClient(server.url("/").toString().trimEnd('/'), "test-token")
            val hanging = async { client.getQrcode() }
            withTimeout(5_000) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { server.takeRequest() }
            }
            hanging.cancel()
            withTimeout(2_000) { hanging.join() }
            assertTrue(hanging.isCancelled)
            assertTrue(withTimeout(5_000) { client.getQrcode() }.isSuccess)
        }
    }
}
