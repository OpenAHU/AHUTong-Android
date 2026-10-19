package com.ahu.ahutong.data.payment

import android.os.SystemClock
import com.ahu.ahutong.data.security.SecureBoxStore
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.URI
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PaymentQrOwner(val accountId: String, val identityGeneration: Long)

internal class PaymentQrRecord(
    @SerializedName("accountId") val accountId: String,
    @SerializedName("payload") val payload: String,
    @SerializedName("fetchedAtEpochMs") val fetchedAtEpochMs: Long,
    @SerializedName("serverExpiryEpochMs") val serverExpiryEpochMs: Long?
)

class PaymentQrSnapshot internal constructor(
    val payload: String,
    val fetchedAtEpochMs: Long,
    private val fetchedAtElapsedMs: Long,
    private val serverExpiryElapsedMs: Long?
) {
    fun ageMillis(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long =
        (nowElapsedMs - fetchedAtElapsedMs).coerceAtLeast(0)

    fun canDisplay(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean =
        ageMillis(nowElapsedMs) < MAX_RETAINED_AGE_MS &&
            (serverExpiryElapsedMs == null || serverExpiryElapsedMs - nowElapsedMs > 5_000L)

    fun shouldRefresh(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean =
        ageMillis(nowElapsedMs) >= REFRESH_AFTER_MS ||
            (serverExpiryElapsedMs != null && serverExpiryElapsedMs - nowElapsedMs <= 20_000L)

    fun refreshDelayMillis(nowElapsedMs: Long): Long = minOf(
        (REFRESH_AFTER_MS - ageMillis(nowElapsedMs)).coerceAtLeast(0),
        serverExpiryElapsedMs?.let { (it - nowElapsedMs - 15_000L).coerceAtLeast(5_000L) } ?: REFRESH_AFTER_MS
    )

    override fun toString(): String = "PaymentQrSnapshot(fetchedAt=$fetchedAtEpochMs)"

    companion object {
        // Retention is a display fallback, not a claim about server-side validity.
        const val MAX_RETAINED_AGE_MS = 10 * 60_000L
        const val REFRESH_AFTER_MS = 45_000L
        const val STALE_AFTER_MS = 60_000L
    }
}

internal interface PaymentQrCacheStore {
    suspend fun read(accountId: String): PaymentQrRecord?
    suspend fun write(record: PaymentQrRecord)
    suspend fun clear(accountId: String)
}

internal object EncryptedPaymentQrCache : PaymentQrCacheStore {
    private const val KEY = "payment_qr_v1"
    private val gson = Gson()

    override suspend fun read(accountId: String): PaymentQrRecord? = withContext(Dispatchers.IO) {
        SecureBoxStore.get(SecureBoxStore.userBox(accountId), KEY)?.let {
            runCatching { gson.fromJson(it, PaymentQrRecord::class.java) }.getOrNull()
        }
    }

    override suspend fun write(record: PaymentQrRecord) = withContext(Dispatchers.IO) {
        SecureBoxStore.put(SecureBoxStore.userBox(record.accountId), KEY, gson.toJson(record))
    }

    override suspend fun clear(accountId: String) = withContext(Dispatchers.IO) {
        SecureBoxStore.remove(SecureBoxStore.userBox(accountId), KEY)
    }
}

internal fun paymentQrServerExpiry(payload: String): Long? {
    val query = runCatching { URI(payload).rawQuery }.getOrNull() ?: return null
    val values = query.split('&').mapNotNull { item ->
        item.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].lowercase() to it[1] }
    }.toMap()
    val raw = values["expiresat"] ?: values["expires_at"] ?: values["exp"] ?: return null
    val numeric = raw.toLongOrNull()
    return if (numeric != null) {
        if (numeric in 0..9_999_999_999L) numeric * 1_000L else numeric
    } else runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
}
