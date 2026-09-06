package com.ahu.ahutong.data.server

import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class ApkDownloadCancellationTest {
    @Test
    fun `cancelling downloads leaves ordinary and gray calls running`() {
        val started = CountDownLatch(3)
        val releaseResponses = CountDownLatch(1)
        val completed = CountDownLatch(3)
        val successfulCalls = Collections.synchronizedSet(mutableSetOf<String>())
        val ordinaryClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                started.countDown()
                check(releaseResponses.await(10, TimeUnit.SECONDS)) { "Test response was not released" }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody())
                    .build()
            }
            .build()
        val grayClient = ordinaryClient.newBuilder().build()
        val downloadClient = createApkDownloadClient(ordinaryClient)
        val clients = listOf(ordinaryClient, grayClient, downloadClient)

        fun enqueue(client: OkHttpClient, name: String): Call {
            // The application interceptor provides the response; no external server is contacted.
            val call = client.newCall(Request.Builder().url("https://example.test/$name").build())
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    completed.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { successfulCalls += name }
                    completed.countDown()
                }
            })
            return call
        }

        try {
            assertSame(ordinaryClient.dispatcher, grayClient.dispatcher)
            assertNotSame(ordinaryClient.dispatcher, downloadClient.dispatcher)
            val ordinaryCall = enqueue(ordinaryClient, "ordinary")
            val grayCall = enqueue(grayClient, "gray")
            val downloadCall = enqueue(downloadClient, "download")
            assertTrue(started.await(5, TimeUnit.SECONDS))

            downloadClient.dispatcher.cancelAll()

            assertTrue(downloadCall.isCanceled())
            assertFalse(ordinaryCall.isCanceled())
            assertFalse(grayCall.isCanceled())
            releaseResponses.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(setOf("ordinary", "gray"), successfulCalls.toSet())
        } finally {
            releaseResponses.countDown()
            clients.map { it.dispatcher }.distinct().forEach { dispatcher ->
                dispatcher.cancelAll()
                dispatcher.executorService.shutdownNow()
            }
            ordinaryClient.connectionPool.evictAll()
        }
    }
}
