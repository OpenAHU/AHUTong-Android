package com.ahu.ahutong.data.payment

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PaymentQrRepositoryTest {
    @Test fun `foreground success is cached before any prediction profile exists`() = runTest {
        val fixture = PaymentQrFixture(this)
        var calls = 0
        val repository = fixture.repository { calls++; Result.success("qr") }
        assertEquals("qr", repository.getForDisplay().getOrThrow())
        advanceTimeBy(20_000)
        assertEquals("qr", repository.getForDisplay().getOrThrow())
        assertEquals(1, calls)
        assertTrue(repository.prefetchPredictively().isFailure)
        assertEquals(1, calls)
    }

    @Test fun `a new repository restores the same accounts persisted QR without a request`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.repository { Result.success("persisted-qr") }.refresh().getOrThrow()
        advanceTimeBy(25_000)
        val restarted = fixture.repository { error("cached display cannot require network") }
        assertEquals("persisted-qr", restarted.cachedForDisplay()?.payload)
        assertEquals(25_000, restarted.cachedForDisplay()?.ageMillis(testScheduler.currentTime))
    }

    @Test fun `foreground startup uses fresh cache and refreshes stale cache before other work`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(ageMs = 20_000)
        var calls = 0
        val repository = fixture.repository { calls++; Result.success("fresh-qr") }
        assertEquals("cached-qr", repository.prepareForForeground().getOrThrow().payload)
        assertEquals(0, calls)
        advanceTimeBy(30_000)
        assertEquals("fresh-qr", repository.prepareForForeground().getOrThrow().payload)
        assertEquals(1, calls)
    }

    @Test fun `a failed refresh after the old ten second window retains the cached QR`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(ageMs = 50_000)
        val repository = fixture.repository { Result.failure(IOException("weak network")) }
        assertTrue(repository.refresh().isFailure)
        assertEquals("cached-qr", repository.cachedForDisplay()?.payload)
        assertEquals("cached-qr", repository.getForDisplay(forceRefresh = true).getOrThrow())
    }

    @Test fun `concurrent taps and a cancelled caller share one ongoing request`() = runTest {
        val fixture = PaymentQrFixture(this)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val repository = fixture.repository {
            calls++
            started.complete(Unit)
            finish.await()
            Result.success("qr")
        }
        val closedPage = async { repository.refresh() }
        started.await()
        closedPage.cancel()
        closedPage.join()
        val reopenedPage = async { repository.refresh() }
        runCurrent()
        assertEquals(1, calls)
        finish.complete(Unit)
        assertEquals("qr", reopenedPage.await().getOrThrow().payload)
        assertEquals("qr", repository.cachedForDisplay()?.payload)
    }

    @Test fun `a response from the previous account cannot display or persist in the new account`() = runTest {
        val fixture = PaymentQrFixture(this)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repository = fixture.repository { started.complete(Unit); finish.await(); Result.success("old-account-qr") }
        val old = async { repository.refresh() }
        started.await()
        fixture.owner = PaymentQrOwner("account-b", 1)
        finish.complete(Unit)
        assertTrue(old.await().isFailure)
        assertNull(repository.cachedForDisplay())
        assertTrue(fixture.cache.records.isEmpty())
    }

    @Test fun `sign out while a request is pending cannot repopulate the cache`() = runTest {
        val fixture = PaymentQrFixture(this)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repository = fixture.repository { started.complete(Unit); finish.await(); Result.success("qr") }
        val old = async { repository.refresh() }
        started.await()
        fixture.owner = null
        finish.complete(Unit)
        assertTrue(old.await().isFailure)
        assertNull(repository.cachedForDisplay())
        assertTrue(fixture.cache.records.isEmpty())
        assertTrue(repository.refresh().isFailure)
    }

    @Test fun `cached payloads never cross accounts or survive an explicit server expiry`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed(expiresInMs = 8_000)
        val repository = fixture.repository { Result.failure(IOException()) }
        assertEquals("cached-qr", repository.cachedForDisplay()?.payload)
        advanceTimeBy(4_000)
        assertNull(repository.cachedForDisplay())
        fixture.owner = PaymentQrOwner("account-b", 1)
        assertNull(repository.cachedForDisplay())
    }

    @Test fun `old future dated and malformed cache entries are discarded`() = runTest {
        val fixture = PaymentQrFixture(this)
        for (age in listOf(-1L, PaymentQrSnapshot.MAX_RETAINED_AGE_MS, PaymentQrSnapshot.MAX_RETAINED_AGE_MS + 1)) {
            fixture.seed(ageMs = age)
            assertNull(fixture.repository { error("not used") }.cachedForDisplay())
        }
        fixture.seed(payload = " ")
        assertNull(fixture.repository { error("not used") }.cachedForDisplay())
    }

    @Test fun `disk write failure cannot withhold a newly fetched QR`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.cache.failWrites = true
        val repository = fixture.repository { Result.success("fresh-qr") }
        assertEquals("fresh-qr", repository.refresh().getOrThrow().payload)
        assertEquals("fresh-qr", repository.cachedForDisplay()?.payload)
    }

    @Test fun `an explicitly expired server payload cannot replace a cached QR`() = runTest {
        val fixture = PaymentQrFixture(this)
        fixture.seed()
        val repository = fixture.repository { Result.success("https://example.invalid/qr?expires_at=${fixture.epoch - 1}") }
        assertTrue(repository.refresh().isFailure)
        assertEquals("cached-qr", repository.cachedForDisplay()?.payload)
        assertFalse(repository.cachedForDisplay().toString().contains("cached-qr"))
    }
}
