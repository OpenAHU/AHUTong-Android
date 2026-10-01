package com.ahu.ahutong.data.crawler

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentQrRequestTest {
    @Test
    fun `existing first party session returns without touching Rust`() = runTest {
        val request = PaymentQrRequest(
            firstParty = { Result.success("qr") },
            rustHttp = { error("fallback must not run") }
        )
        assertEquals("qr", request.execute().getOrThrow())
    }

    @Test
    fun `invalid first party response still tries the Rust session`() = runTest {
        val request = PaymentQrRequest(
            firstParty = { Result.success(" ") },
            rustHttp = { Result.success("rust-qr") }
        )
        assertEquals("rust-qr", request.execute().getOrThrow())
    }

    @Test
    fun `both failed transports are followed by another first party attempt`() = runTest {
        val calls = mutableListOf<String>()
        val request = PaymentQrRequest(
            firstParty = {
                calls += "first"
                if (calls.size == 1) throw IOException("weak network")
                Result.success("recovered-qr")
            },
            rustHttp = { calls += "rust"; Result.failure(IOException()) }
        )
        assertEquals("recovered-qr", request.execute().getOrThrow())
        assertEquals(listOf("first", "rust", "first"), calls)
    }

    @Test
    fun `slow first party request yields to another usable session`() = runTest {
        val request = PaymentQrRequest(
            firstParty = { delay(10_000); Result.success("late-qr") },
            rustHttp = { Result.success("rust-qr") },
            attemptTimeoutMs = 100
        )
        assertEquals("rust-qr", request.execute().getOrThrow())
    }

    @Test
    fun `failed attempts do not disable a later display request`() = runTest {
        var recovered = false
        val request = PaymentQrRequest(
            firstParty = { if (recovered) Result.success("qr") else Result.failure(IOException()) },
            rustHttp = { Result.failure(IOException()) }
        )
        repeat(6) { assertTrue(request.execute().isFailure) }
        recovered = true
        assertEquals("qr", request.execute().getOrThrow())
    }

    @Test
    fun `closing the QR cancels work without starting fallback or retry`() = runTest {
        val started = CompletableDeferred<Unit>()
        var fallbackCalled = false
        val request = PaymentQrRequest(
            firstParty = { started.complete(Unit); awaitCancellation() },
            rustHttp = { fallbackCalled = true; Result.success("qr") }
        )
        val result = async { request.execute() }
        started.await()
        result.cancel()
        result.join()
        assertTrue(result.isCancelled)
        assertFalse(fallbackCalled)
    }
}
