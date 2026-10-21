package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.payment.PaymentQrFixture
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PaymentQrLoaderTest {
    @Test fun `cached QR is visible while a slow refresh and repeated taps are pending`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(ageMs = 50_000)
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val repository = fixture.repository { calls++; finish.await(); Result.success("new-qr") }
        val loader = PaymentQrLoader(repository, backgroundScope)
        loader.start()
        runCurrent()
        assertEquals("cached-qr", loader.state.value.snapshot?.payload)
        assertTrue(loader.state.value.loading)
        repeat(8) { loader.start(forceRefresh = true) }
        runCurrent()
        assertEquals(1, calls)
        finish.complete(Unit)
        runCurrent()
        assertEquals("new-qr", loader.state.value.snapshot?.payload)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun `weak network failure keeps the previous QR and retries automatically`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(ageMs = 50_000)
        var calls = 0
        val repository = fixture.repository {
            if (++calls == 1) Result.failure(IOException()) else Result.success("recovered-qr")
        }
        val loader = PaymentQrLoader(repository, backgroundScope)
        loader.start()
        runCurrent()
        assertEquals("cached-qr", loader.state.value.snapshot?.payload)
        assertTrue(loader.state.value.failed)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals("recovered-qr", loader.state.value.snapshot?.payload)
        assertEquals(2, calls)
        assertFalse(loader.state.value.failed)
    }

    @Test fun `fresh cached QR renders without waiting for a request or account profile`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(ageMs = 20_000)
        var calls = 0
        val loader = PaymentQrLoader(fixture.repository { calls++; Result.success("new") }, backgroundScope)
        loader.start()
        runCurrent()
        assertEquals("cached-qr", loader.state.value.snapshot?.payload)
        assertEquals(0, calls)
        advanceTimeBy(25_000)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun `returning to the page reuses the pending request and restores its result`() = runTest {
        val fixture = PaymentQrFixture(this)
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val repository = fixture.repository { calls++; finish.await(); Result.success("qr") }
        val loader = PaymentQrLoader(repository, backgroundScope)
        loader.start()
        runCurrent()
        loader.stop()
        assertNull(loader.state.value.snapshot)
        loader.start()
        runCurrent()
        assertEquals(1, calls)
        finish.complete(Unit)
        runCurrent()
        assertEquals("qr", loader.state.value.snapshot?.payload)
    }

    @Test fun `balance work starts only after the first QR attempt has completed`() = runTest {
        val fixture = PaymentQrFixture(this)
        val finish = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val repository = fixture.repository { events += "qr"; finish.await(); Result.success("qr") }
        val loader = PaymentQrLoader(repository, backgroundScope) { events += "balance" }
        loader.start()
        runCurrent()
        assertEquals(listOf("qr"), events)
        finish.complete(Unit)
        runCurrent()
        assertEquals(listOf("qr", "balance"), events)
    }

    @Test fun `manual refresh requests balance after the QR request completes`() = runTest {
        val fixture = PaymentQrFixture(this)
        val events = mutableListOf<String>()
        var calls = 0
        val repository = fixture.repository { calls++; Result.success("qr-$calls") }
        val loader = PaymentQrLoader(
            repository,
            backgroundScope,
            onFirstRefreshFinished = { events += "first" },
            onManualRefreshFinished = { events += "manual-balance" }
        )
        loader.start()
        runCurrent()
        assertEquals(listOf("first"), events)

        loader.start(forceRefresh = true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(listOf("first", "manual-balance"), events)
    }

    @Test fun `manual first refresh counts as the balance trigger instead of firing twice`() = runTest {
        val fixture = PaymentQrFixture(this)
        val events = mutableListOf<String>()
        val repository = fixture.repository { Result.success("qr") }
        val loader = PaymentQrLoader(
            repository,
            backgroundScope,
            onFirstRefreshFinished = { events += "first" },
            onManualRefreshFinished = { events += "manual-balance" }
        )
        loader.start(forceRefresh = true)
        runCurrent()
        assertEquals(listOf("manual-balance"), events)
    }

    @Test fun `known expired cached code is removed even while the network keeps failing`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(expiresInMs = 8_000)
        val loader = PaymentQrLoader(fixture.repository { Result.failure(IOException()) }, backgroundScope)
        loader.start(forceRefresh = true)
        runCurrent()
        assertEquals("cached-qr", loader.state.value.snapshot?.payload)
        advanceTimeBy(4_000)
        runCurrent()
        assertNull(loader.state.value.snapshot)
        assertTrue(loader.state.value.failed)
    }

    @Test fun `cached server expiry is enforced while a slow refresh is still in flight`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(expiresInMs = 8_000)
        val finish = CompletableDeferred<Unit>()
        val loader = PaymentQrLoader(fixture.repository { finish.await(); Result.success("new-qr") }, backgroundScope)
        loader.start(forceRefresh = true)
        runCurrent()
        assertEquals("cached-qr", loader.state.value.snapshot?.payload)
        advanceTimeBy(4_000)
        runCurrent()
        assertNull(loader.state.value.snapshot)
        assertTrue(loader.state.value.loading)
        finish.complete(Unit)
        runCurrent()
        assertEquals("new-qr", loader.state.value.snapshot?.payload)
    }

    @Test fun `repeated failures never permanently stop foreground retries`() = runTest {
        val fixture = PaymentQrFixture(this)
        var calls = 0
        val loader = PaymentQrLoader(fixture.repository { calls++; Result.failure(IOException()) }, backgroundScope)
        loader.start()
        runCurrent()
        repeat(8) { attempt ->
            advanceTimeBy(PaymentQrLoader.retryDelayMillis(attempt + 1))
            runCurrent()
        }
        assertEquals(9, calls)
        assertEquals(30_000L, PaymentQrLoader.retryDelayMillis(100))
    }
}
