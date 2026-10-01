package com.ahu.ahutong.data.crawler

import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Try existing sessions before requiring login; each foreground attempt has a budget. */
internal class PaymentQrRequest(
    private val firstParty: suspend () -> Result<String>,
    private val rustHttp: suspend () -> Result<String>,
    private val attemptTimeoutMs: Long = 12_000L,
    private val retryDelayMs: Long = 500L
) {
    suspend fun execute(): Result<String> {
        attempt(firstParty).let { if (it.isSuccess) return it }
        // Both transports use the same first-party QR endpoint but retain independent
        // cookies. A usable Rust session can survive an Android session refresh failure.
        // JNI shares that Rust session but has a blocking request timeout of 300s;
        // the cancellable HTTP transport keeps foreground recovery bounded.
        attempt(rustHttp).let { if (it.isSuccess) return it }
        delay(retryDelayMs)
        // No failure counter disables future display requests. Reopening the QR retries.
        return attempt(firstParty)
    }

    private suspend fun attempt(request: suspend () -> Result<String>): Result<String> =
        withTimeoutOrNull(attemptTimeoutMs) {
            try {
                val result = request()
                (result.exceptionOrNull() as? CancellationException)?.let { throw it }
                result.mapCatching { value ->
                    check(value.isNotBlank()) { "empty payment QR" }
                    value
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        } ?: Result.failure(SocketTimeoutException("payment QR request timed out"))
}
