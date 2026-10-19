package com.ahu.ahutong.data.payment

import android.os.SystemClock
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Foreground business cache. Prediction activation/cancellation never owns this lifetime. */
@Singleton
class PaymentQrRepository internal constructor(
    private val request: suspend () -> Result<String>,
    private val ownerProvider: () -> PaymentQrOwner?,
    private val store: PaymentQrCacheStore,
    private val elapsedMillis: () -> Long,
    private val epochMillis: () -> Long,
    private val requestScope: CoroutineScope
) {
    @Inject constructor() : this(
        request = ::requestPaymentQr,
        ownerProvider = ::currentPaymentQrOwner,
        store = EncryptedPaymentQrCache,
        elapsedMillis = SystemClock::elapsedRealtime,
        epochMillis = System::currentTimeMillis,
        requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val mutex = Mutex()
    private var owner: PaymentQrOwner? = null
    private var snapshot: PaymentQrSnapshot? = null
    private var requestId = 0L
    private var inFlight: Deferred<Result<PaymentQrSnapshot>>? = null

    internal fun elapsedNow(): Long = elapsedMillis()
    internal fun currentOwner(): PaymentQrOwner? = ownerProvider()

    suspend fun prepareForForeground(): Result<PaymentQrSnapshot> {
        cachedForDisplay()?.takeUnless { it.shouldRefresh(elapsedMillis()) }?.let { return Result.success(it) }
        return refresh()
    }

    suspend fun cachedForDisplay(): PaymentQrSnapshot? = mutex.withLock {
        syncOwnerLocked()
        snapshot?.takeIf { it.canDisplay(elapsedMillis()) }.also {
            if (it == null) snapshot = null
        }
    }

    /** Concurrent taps and view recreation await one application-owned request. */
    suspend fun refresh(): Result<PaymentQrSnapshot> {
        val selected = mutex.withLock {
            syncOwnerLocked()
            val expectedOwner = owner
                ?: return Result.failure(IllegalStateException("no active payment QR account"))
            inFlight?.let { return@withLock it }
            val id = ++requestId
            requestScope.async(start = CoroutineStart.LAZY) {
                try {
                    fetch(expectedOwner)
                } finally {
                    withContext(NonCancellable) {
                        mutex.withLock { if (requestId == id) inFlight = null }
                    }
                }
            }.also { inFlight = it; it.start() }
        }
        return selected.await()
    }

    suspend fun getForDisplay(forceRefresh: Boolean = false): Result<String> {
        if (!forceRefresh) cachedForDisplay()?.let { return Result.success(it.payload) }
        val result = refresh()
        return result.map { it.payload }.recoverCatching { error ->
            if (error is CancellationException) throw error
            cachedForDisplay()?.payload ?: throw error
        }
    }

    // Predictive requests remain disabled until the upstream expiry contract is verified.
    suspend fun prefetchPredictively(): Result<Unit> = Result.failure(
        IllegalStateException("payment QR predictive expiry is not protocol-verified")
    )

    private suspend fun syncOwnerLocked() {
        val current = ownerProvider()
        if (current == owner) return
        requestId++
        inFlight?.cancel()
        inFlight = null
        snapshot = null
        owner = current
        if (current == null) return
        val record = try { store.read(current.accountId) } catch (error: Exception) {
            if (error is CancellationException) throw error
            null
        }
        if (ownerProvider() == current && record != null && record.accountId == current.accountId) {
            snapshot = restore(record)
        }
    }

    private fun restore(record: PaymentQrRecord): PaymentQrSnapshot? {
        if (record.payload.isNullOrBlank() || record.fetchedAtEpochMs <= 0) return null
        val nowEpoch = epochMillis()
        val age = nowEpoch - record.fetchedAtEpochMs
        if (age !in 0 until PaymentQrSnapshot.MAX_RETAINED_AGE_MS) return null
        val nowElapsed = elapsedMillis()
        return PaymentQrSnapshot(
            record.payload, record.fetchedAtEpochMs, nowElapsed - age,
            record.serverExpiryEpochMs?.let { nowElapsed + (it - nowEpoch) }
        ).takeIf { it.canDisplay(nowElapsed) }
    }

    private suspend fun fetch(expectedOwner: PaymentQrOwner): Result<PaymentQrSnapshot> = try {
        val payload = request().getOrThrow()
        check(payload.isNotBlank()) { "empty payment QR" }
        val record = PaymentQrRecord(expectedOwner.accountId, payload, epochMillis(), paymentQrServerExpiry(payload))
        mutex.withLock {
            check(owner == expectedOwner && ownerProvider() == expectedOwner) { "payment QR account changed" }
            val value = checkNotNull(restore(record)) { "payment QR already expired" }
            // Disk errors must not withhold a freshly acquired foreground QR.
            try { store.write(record) } catch (error: Exception) {
                if (error is CancellationException) throw error
            }
            if (ownerProvider() != expectedOwner) {
                store.clear(expectedOwner.accountId)
                error("payment QR account changed")
            }
            snapshot = value
            Result.success(value)
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}

private fun currentPaymentQrOwner(): PaymentQrOwner? {
    val generation = SessionRefreshCoordinator.currentIdentityGeneration()
    if (SessionRefreshCoordinator.isExplicitlySignedOut()) return null
    val user = SessionStore.currentUser() ?: return null
    if (generation != SessionRefreshCoordinator.currentIdentityGeneration()) return null
    return PaymentQrOwner(user.xh, generation)
}

private suspend fun requestPaymentQr(): Result<String> = when (val response = AHURepository.getQrcode()) {
    is AhuResult.Success -> Result.success(response.value)
    is AhuResult.Failure -> Result.failure(IllegalStateException("payment QR request failed"))
}
