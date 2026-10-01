package com.ahu.ahutong.personalization.prefetch

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlin.test.assertTrue
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentQrRepositoryTest {
    @Test
    fun `foreground QR loads before prediction profile is ready`() = runTest {
        val repository = PaymentQrRepository {
            Result.success("https://example.invalid/payment-qr")
        }

        assertEquals(
            "https://example.invalid/payment-qr",
            repository.getForDisplay().getOrThrow()
        )
    }

    @Test
    fun `profile activation does not cancel an early foreground request`() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CompletableDeferred<Unit>()
        val repository = PaymentQrRepository {
            requestStarted.complete(Unit)
            finishRequest.await()
            Result.success("https://example.invalid/payment-qr")
        }

        val result = async { repository.getForDisplay() }
        requestStarted.await()
        repository.activateProfile("profile", profileGeneration = 1, loginGeneration = 1)
        finishRequest.complete(Unit)

        assertEquals("https://example.invalid/payment-qr", result.await().getOrThrow())
    }

    @Test
    fun `cancelled foreground request cannot strand subsequent QR requests`() = runTest {
        val started = CompletableDeferred<Unit>()
        var requests = 0
        val repository = PaymentQrRepository {
            requests++
            if (requests == 1) {
                started.complete(Unit)
                awaitCancellation()
            }
            Result.success("fresh-qr")
        }
        repository.activateProfile("profile", 1, 1)
        val cancelled = async { repository.getForDisplay() }
        started.await()
        cancelled.cancel()
        cancelled.join()

        assertEquals("fresh-qr", repository.getForDisplay().getOrThrow())
        assertEquals(2, requests)
    }

    @Test
    fun `failed manual refresh preserves a fresh QR for the same account`() = runTest {
        var requests = 0
        val repository = PaymentQrRepository {
            if (++requests == 1) Result.success("fresh-qr") else Result.failure(IOException())
        }
        repository.activateProfile("profile", 1, 1)
        assertEquals("fresh-qr", repository.getForDisplay().getOrThrow())
        assertEquals("fresh-qr", repository.getForDisplay(forceRefresh = true).getOrThrow())
        repository.activateProfile("another-profile", 2, 2)
        assertTrue(repository.getForDisplay().isFailure)
    }
}
