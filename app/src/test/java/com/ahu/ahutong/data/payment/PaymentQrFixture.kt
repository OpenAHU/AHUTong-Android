package com.ahu.ahutong.data.payment

import kotlinx.coroutines.test.TestScope

internal class MemoryQrCache : PaymentQrCacheStore {
    val records = mutableMapOf<String, PaymentQrRecord>()
    var failWrites = false
    override suspend fun read(accountId: String) = records[accountId]
    override suspend fun write(record: PaymentQrRecord) {
        check(!failWrites) { "storage unavailable" }
        records[record.accountId] = record
    }
    override suspend fun clear(accountId: String) { records.remove(accountId) }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class PaymentQrFixture(val scope: TestScope) {
    var owner: PaymentQrOwner? = PaymentQrOwner("account-a", 0)
    val cache = MemoryQrCache()
    val epoch: Long get() = 1_800_000_000_000L + scope.testScheduler.currentTime

    fun repository(request: suspend () -> Result<String>) = PaymentQrRepository(
        request, { owner }, cache, { scope.testScheduler.currentTime }, { epoch }, scope.backgroundScope
    )

    fun seed(payload: String = "cached-qr", ageMs: Long = 20_000, expiresInMs: Long? = null) {
        val account = requireNotNull(owner).accountId
        cache.records[account] = PaymentQrRecord(account, payload, epoch - ageMs, expiresInMs?.let { epoch + it })
    }
}
