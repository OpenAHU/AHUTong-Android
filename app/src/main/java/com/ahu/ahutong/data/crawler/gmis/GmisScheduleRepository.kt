package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.schedule.gmis.*

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.AcademicAccountType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class GmisScheduleSnapshot(
    val terms: List<GmisTerm>,
    val selectedTerm: GmisTerm,
    val timetable: GmisTimetable
)

/** Shares a single network session and encrypted, per-account timetable cache. */
internal class GmisScheduleRepository(
    private val cache: GmisScheduleCache,
    private val newClient: (String) -> GmisScheduleClient,
    private val currentAccount: () -> String?
) {
    private val mutex = Mutex()
    private var clientAccount: String? = null
    private var client: GmisScheduleClient? = null
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> get() = mutableRevision

    fun cachedCurrent(accountId: String): GmisScheduleSnapshot? {
        if (currentAccount() != accountId) return null
        val terms = cache.terms(accountId) ?: return null
        val selected = terms.firstOrNull { it.selected } ?: terms.firstOrNull() ?: return null
        val table = cache.timetable(accountId, selected.code) ?: return null
        return GmisScheduleSnapshot(terms, selected, table)
    }

    fun cachedForTerm(accountId: String, termCode: String): GmisTimetable? =
        if (currentAccount() == accountId) cache.timetable(accountId, termCode) else null

    suspend fun current(accountId: String): GmisScheduleSnapshot = mutex.withLock {
        val terms = termsLocked(accountId, force = false)
        val selected = terms.firstOrNull { it.selected } ?: terms.first()
        GmisScheduleSnapshot(terms, selected, timetableLocked(accountId, selected, force = false))
    }

    suspend fun refresh(accountId: String, preferredTermCode: String?): GmisScheduleSnapshot =
        mutex.withLock {
            val terms = termsLocked(accountId, force = true)
            val selected = terms.firstOrNull { it.code == preferredTermCode }
                ?: terms.firstOrNull { it.selected } ?: terms.first()
            GmisScheduleSnapshot(terms, selected, timetableLocked(accountId, selected, force = true))
        }

    suspend fun forTerm(accountId: String, term: GmisTerm): GmisTimetable = mutex.withLock {
        val available = termsLocked(accountId, force = false)
        val known = available.firstOrNull { it.code == term.code }
            ?: throw GmisProtocolException("研究生教务学期已变化，请刷新课表")
        timetableLocked(accountId, known, force = false)
    }

    private suspend fun termsLocked(accountId: String, force: Boolean): List<GmisTerm> {
        requireCurrentAccount(accountId)
        if (!force) cache.terms(accountId)?.let { return it }
        val fresh = clientFor(accountId).terms()
        requireCurrentAccount(accountId)
        cache.saveTerms(accountId, fresh)
        mutableRevision.value += 1
        return fresh
    }

    private suspend fun timetableLocked(accountId: String, term: GmisTerm, force: Boolean): GmisTimetable {
        requireCurrentAccount(accountId)
        if (!force) cache.timetable(accountId, term.code)?.let { return it }
        val fresh = clientFor(accountId).timetable(term)
        requireCurrentAccount(accountId)
        cache.saveTimetable(accountId, term.code, fresh)
        mutableRevision.value += 1
        return fresh
    }

    private fun clientFor(accountId: String): GmisScheduleClient {
        if (clientAccount != accountId || client == null) {
            clientAccount = accountId
            client = newClient(accountId)
        }
        return requireNotNull(client)
    }

    private fun requireCurrentAccount(accountId: String) {
        if (currentAccount() != accountId) throw CancellationException("Account changed")
    }
}

internal object PostgraduateScheduleRepository {
    private val store = object : GmisScheduleCache {
        override fun terms(accountId: String): List<GmisTerm>? =
            GmisCacheCodec.decodeTerms(AHUCache.getGmisScheduleCache(accountId, "terms"))

        override fun timetable(accountId: String, termCode: String): GmisTimetable? =
            GmisCacheCodec.decodeTimetable(
                AHUCache.getGmisScheduleCache(accountId, "term.${PostgraduateCacheKey.term(termCode)}")
            )

        override fun saveTerms(accountId: String, terms: List<GmisTerm>) {
            AHUCache.saveGmisScheduleCache(accountId, "terms", GmisCacheCodec.encodeTerms(terms))
        }

        override fun saveTimetable(accountId: String, termCode: String, timetable: GmisTimetable) {
            AHUCache.saveGmisScheduleCache(accountId,
                "term.${PostgraduateCacheKey.term(termCode)}", GmisCacheCodec.encodeTimetable(timetable))
        }
    }

    val instance = GmisScheduleRepository(
        cache = store,
        newClient = PostgraduateScheduleSource::forAccount,
        currentAccount = {
            AHUCache.getCurrentUser()
                ?.takeIf { it.academicAccountType == AcademicAccountType.POSTGRADUATE }?.xh
        }
    )
}

internal object PostgraduateCacheKey {
    fun term(code: String): String {
        require(Regex("[A-Za-z0-9_-]{1,40}").matches(code))
        return code
    }
}
