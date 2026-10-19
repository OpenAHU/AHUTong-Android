package com.ahu.ahutong.data.network

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PaymentQrQueueTest {
    @Test fun `QR reaches the server even when background dispatcher capacity is occupied`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody("qr"))
            server.start()
            val background = AhuHttp.plain().dispatcher(Dispatcher().apply {
                maxRequests = 1; maxRequestsPerHost = 1
            }).build()
            val foreground = background.newBuilder().foregroundPaymentQueue().build()
            fun enqueue(path: String, foregroundRequest: Boolean): CompletableFuture<String> {
                val result = CompletableFuture<String>()
                (if (foregroundRequest) foreground else background)
                    .newCall(Request.Builder().url(server.url(path)).build()).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) { result.completeExceptionally(e) }
                        override fun onResponse(call: Call, response: Response) {
                            response.use { result.complete(it.body!!.string()) }
                        }
                    })
                return result
            }
            try {
                enqueue("/slow-background", false)
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                enqueue("/queued-background", false)
                assertEquals("qr", enqueue("/qr", true).get(2, TimeUnit.SECONDS))
                assertEquals("/qr", server.takeRequest(2, TimeUnit.SECONDS)?.path)
                assertEquals(1, background.dispatcher.queuedCallsCount())
            } finally {
                background.dispatcher.cancelAll()
                foreground.dispatcher.cancelAll()
            }
        }
    }
}
