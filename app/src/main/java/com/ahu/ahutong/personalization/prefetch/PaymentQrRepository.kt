package com.ahu.ahutong.personalization.prefetch

import android.os.SystemClock
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.core.common.AhuResult
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SensitiveQrEnvelope internal constructor(
    internal val value: String,
    val profileKey: String,
    val profileGeneration: Long,
    val loginGeneration: Long,
    val requestGeneration: Long,
    val fetchedAtElapsedMs: Long,
    val validUntilElapsedMs: Long,
    val serverExpiryVerified: Boolean
) {
    fun isFresh(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean =
        validUntilElapsedMs - nowElapsedMs >= DISPLAY_MIN_REMAINING_MS

    private companion object { const val DISPLAY_MIN_REMAINING_MS = 5_000L }
}

@Singleton
class PaymentQrRepository internal constructor(
    private val firstPartyRequest: suspend () -> Result<String>
) {
    @Inject constructor() : this(firstPartyRequest = ::requestPaymentQrFirstParty)

    private val mutex = Mutex()
    private val requestGeneration = AtomicLong(0)
    @Volatile private var profileKey: String? = null
    @Volatile private var profileGeneration: Long = 0
    @Volatile private var loginGeneration: Long = 0
    @Volatile private var envelope: SensitiveQrEnvelope? = null
    @Volatile private var inFlight: Deferred<Result<SensitiveQrEnvelope>>? = null

    suspend fun activateProfile(profileKey: String, profileGeneration: Long, loginGeneration: Long) {
        mutex.withLock {
            if (this.profileKey != profileKey || this.profileGeneration != profileGeneration || this.loginGeneration != loginGeneration) {
                clearLocked()
                this.profileKey = profileKey
                this.profileGeneration = profileGeneration
                this.loginGeneration = loginGeneration
            }
        }
    }

    suspend fun getForDisplay(forceRefresh: Boolean = false): Result<String> {
        if (!forceRefresh) {
            consumeFreshForDisplay()?.let { return Result.success(it) }
        }
        // The foreground payment flow must remain available while the optional prediction
        // profile is still starting. Without a profile there is nothing safe to cache, so
        // request the same audited first-party endpoint and return the value directly.
        if (profileKey == null) return firstPartyRequest()

        val result = fetch(forceRefresh = forceRefresh, predictive = false)
        if (result.isFailure) {
            consumeFreshForDisplay()?.let { return Result.success(it) }
        }
        return result.map(SensitiveQrEnvelope::value)
    }

    suspend fun prefetchPredictively(): Result<Unit> {
        if (!PREDICTIVE_PROTOCOL_EXPIRY_VERIFIED) {
            return Result.failure(IllegalStateException("payment QR endpoint expiry semantics have not been protocol-verified"))
        }
        return fetch(forceRefresh = false, predictive = true).map { Unit }
    }

    suspend fun consumeFreshForDisplay(): String? = mutex.withLock {
        envelope?.takeIf(SensitiveQrEnvelope::isFresh)?.value
    }

    suspend fun clearSensitive() = mutex.withLock { clearLocked() }

    fun hasFreshEnvelope(): Boolean = envelope?.isFresh() == true

    private suspend fun fetch(forceRefresh: Boolean, predictive: Boolean): Result<SensitiveQrEnvelope> {
        val existing = mutex.withLock {
            if (!forceRefresh) envelope?.takeIf(SensitiveQrEnvelope::isFresh)?.let { return Result.success(it) }
            inFlight
        }
        if (existing != null) return existing.await().also { result ->
            if (predictive && result.getOrNull()?.serverExpiryVerified != true) {
                return Result.failure(IllegalStateException("payment QR protocol does not expose a verifiable server expiry"))
            }
        }

        val deferred = CompletableDeferred<Result<SensitiveQrEnvelope>>()
        val selectedRequest = mutex.withLock {
            val raced = inFlight
            if (raced != null) raced else {
                inFlight = deferred
                deferred
            }
        }
        if (selectedRequest !== deferred) return selectedRequest.await()

        val result = try {
            requestFirstParty()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight === deferred) inFlight = null
                    deferred.cancel(cancelled)
                }
            }
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
        withContext(NonCancellable) {
            mutex.withLock {
                if (inFlight === deferred) {
                    val value = result.getOrNull()
                    if (value != null && value.profileKey == profileKey &&
                        value.profileGeneration == profileGeneration && value.loginGeneration == loginGeneration &&
                        value.requestGeneration == requestGeneration.get()
                    ) {
                        envelope = value
                    }
                    inFlight = null
                    deferred.complete(result)
                }
            }
        }
        val final = deferred.await()
        if (predictive && final.getOrNull()?.serverExpiryVerified != true) {
            return Result.failure(IllegalStateException("payment QR predictive prefetch is fail-closed until expiry semantics are verified"))
        }
        return final
    }

    private suspend fun requestFirstParty(): Result<SensitiveQrEnvelope> = withContext(Dispatchers.IO) {
        val activeProfile = profileKey ?: return@withContext Result.failure(IllegalStateException("no active profile"))
        val expectedProfileGeneration = profileGeneration
        val expectedLoginGeneration = loginGeneration
        val generation = requestGeneration.incrementAndGet()
        val value = firstPartyRequest().getOrElse { return@withContext Result.failure(it) }
        if (activeProfile != profileKey || expectedProfileGeneration != profileGeneration || expectedLoginGeneration != loginGeneration) {
            return@withContext Result.failure(IllegalStateException("profile generation changed"))
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        val serverExpiryEpochMs = parseServerExpiryEpochMs(value)
        val serverRemaining = serverExpiryEpochMs?.minus(System.currentTimeMillis())
        val verified = serverRemaining != null && serverRemaining in 1..MAX_REASONABLE_SERVER_TTL_MS
        val clientTtl = if (verified) minOf(CLIENT_MAX_TTL_MS, serverRemaining!!) else CLIENT_MAX_TTL_MS
        Result.success(
            SensitiveQrEnvelope(
                value,
                activeProfile,
                expectedProfileGeneration,
                expectedLoginGeneration,
                generation,
                nowElapsed,
                nowElapsed + clientTtl,
                verified
            )
        )
    }

    private fun parseServerExpiryEpochMs(value: String): Long? {
        val query = runCatching { URI(value).rawQuery }.getOrNull() ?: return null
        val values = query.split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2) pieces[0].lowercase() to pieces[1] else null
        }.toMap()
        val raw = values["expiresat"] ?: values["expires_at"] ?: values["exp"] ?: return null
        val numeric = raw.toLongOrNull() ?: runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() ?: return null
        return if (numeric < 10_000_000_000L) numeric * 1_000L else numeric
    }

    private fun clearLocked() {
        requestGeneration.incrementAndGet()
        inFlight?.cancel()
        inFlight = null
        envelope = null
    }

    private companion object {
        const val CLIENT_MAX_TTL_MS = 15_000L
        const val MAX_REASONABLE_SERVER_TTL_MS = 5 * 60_000L
        // Flip only after the first-party API contract exposes and documents a verifiable expiry.
        const val PREDICTIVE_PROTOCOL_EXPIRY_VERIFIED = false
    }
}

private suspend fun requestPaymentQrFirstParty(): Result<String> = try {
    when (val response = AHURepository.getQrcode()) {
        is AhuResult.Success -> Result.success(response.value)
        is AhuResult.Failure -> Result.failure(IllegalStateException("payment QR request failed"))
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
