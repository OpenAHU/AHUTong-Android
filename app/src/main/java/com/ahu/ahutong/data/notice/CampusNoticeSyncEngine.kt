package com.ahu.ahutong.data.notice

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface CampusNoticeSource {
    val sourceId: String
    suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice>
}

/** 支持按游标回翻历史列表的信源；[pageUrl] 为 null 时取第一页。 */
internal interface PagedCampusNoticeSource : CampusNoticeSource {
    suspend fun fetchPage(pageUrl: String?): CampusNoticePage
}

internal interface CampusNoticeStore {
    fun read(accountId: String): CampusNoticeSnapshot
    fun write(accountId: String, snapshot: CampusNoticeSnapshot)
    fun clearAll()
}

/** Commits account-owned data only while the login generation and account still match. */
internal interface CampusNoticeAccountFence {
    fun currentAccountId(): String?
    fun generation(): Long
    suspend fun commitIfCurrent(generation: Long, accountId: String, action: () -> Unit): Boolean
}

internal data class CampusNoticeSyncResult(
    val snapshot: CampusNoticeSnapshot?,
    val newUnread: List<CampusNotice> = emptyList(),
    val generation: Long,
    val staleAccount: Boolean = false
)

internal class CampusNoticeSyncEngine(
    private val sources: List<CampusNoticeSource>,
    private val store: CampusNoticeStore,
    private val fence: CampusNoticeAccountFence,
    private val isSourceEnabled: (CampusNoticeSource, String) -> Boolean = { _, _ -> true },
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Shanghai")) },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val unreadLifetimeMillis = 7L * 24 * 60 * 60 * 1_000

    suspend fun snapshot(accountId: String): CampusNoticeSnapshot = mutex.withLock {
        val stored = store.read(accountId)
        val updated = expireUnread(stored)
        if (updated != stored) {
            val generation = fence.generation()
            if (fence.currentAccountId() == accountId) {
                fence.commitIfCurrent(generation, accountId) { store.write(accountId, updated) }
            }
        }
        updated
    }

    suspend fun sync(accountId: String, force: Boolean = false): CampusNoticeSyncResult = mutex.withLock {
        val observedGeneration = fence.generation()
        if (fence.currentAccountId() != accountId) {
            return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
        }
        val stored = store.read(accountId)
        var snapshot = expireUnread(stored)
        if (snapshot != stored && !fence.commitIfCurrent(observedGeneration, accountId) {
                store.write(accountId, snapshot)
            }) {
            return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
        }
        val newlyUnread = mutableListOf<CampusNotice>()
        for (source in sources) {
            if (!isSourceEnabled(source, accountId)) continue
            val previous = snapshot.sourceStatuses[source.sourceId]
            if (!force && previous?.lastSuccessfulSyncDate == today().toString()) continue
            try {
                val seen = snapshot.notices.asSequence()
                    .filter { it.sourceId == source.sourceId }
                    .map(CampusNotice::articleId).toSet()
                val baseline = previous?.lastSuccessfulSyncDate == null
                val pagedSource = source as? PagedCampusNoticeSource
                var baselineNextPageUrl: String? = null
                // 首次同步直接用分页读取，顺手把历史翻页游标落到第 2 页，省掉一次重复请求。
                val fetched = if (baseline && pagedSource != null) {
                    val page = pagedSource.fetchPage(null)
                    baselineNextPageUrl = page.nextPageUrl
                    page.notices
                } else {
                    source.fetchSince(seen, firstSync = baseline)
                }
                val newItems = fetched.filterNot { it.articleId in seen }
                    .map { it.copy(discoveredAtMillis = nowMillis(), read = baseline) }
                val status = (previous ?: CampusNoticeSourceStatus(source.sourceId)).copy(
                    lastSuccessfulSyncDate = today().toString(),
                    lastSuccessfulSyncAtMillis = nowMillis(),
                    lastError = null,
                    nextPageUrl = if (baseline && pagedSource != null) {
                        baselineNextPageUrl
                    } else {
                        previous?.nextPageUrl
                    },
                    historyExhausted = if (baseline && pagedSource != null) {
                        baselineNextPageUrl == null
                    } else {
                        previous?.historyExhausted ?: false
                    },
                    historyError = if (baseline) null else previous?.historyError
                )
                val updated = snapshot.copy(
                    notices = (newItems + snapshot.notices)
                        .distinctBy { it.sourceId to it.articleId },
                    sourceStatuses = snapshot.sourceStatuses + (source.sourceId to status)
                )
                if (!fence.commitIfCurrent(observedGeneration, accountId) {
                        store.write(accountId, updated)
                    }) {
                    return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
                }
                snapshot = updated
                if (!baseline) newlyUnread += newItems
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val updated = snapshot.copy(sourceStatuses = snapshot.sourceStatuses +
                    (source.sourceId to (previous ?: CampusNoticeSourceStatus(source.sourceId)).copy(
                        lastError = syncErrorMessage(error)
                    )))
                if (!fence.commitIfCurrent(observedGeneration, accountId) {
                        store.write(accountId, updated)
                    }) {
                    return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
                }
                snapshot = updated
            }
        }
        CampusNoticeSyncResult(snapshot, newlyUnread, observedGeneration)
    }

    /**
     * 回翻历史列表，每次给每个（或指定）信源追加一页更早的公告。
     *
     * 历史页一律标记为已读：翻旧公告不应制造未读数或补发通知。
     */
    suspend fun loadMore(accountId: String, sourceId: String? = null): CampusNoticeSyncResult = mutex.withLock {
        val observedGeneration = fence.generation()
        if (fence.currentAccountId() != accountId) {
            return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
        }
        val stored = store.read(accountId)
        var snapshot = expireUnread(stored)
        if (snapshot != stored && !fence.commitIfCurrent(observedGeneration, accountId) {
                store.write(accountId, snapshot)
            }) {
            return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
        }
        for (source in sources) {
            if (sourceId != null && source.sourceId != sourceId) continue
            if (!isSourceEnabled(source, accountId)) continue
            val paged = source as? PagedCampusNoticeSource ?: continue
            val previous = snapshot.sourceStatuses[source.sourceId]
            if (previous?.historyExhausted == true) continue
            try {
                var cursor = previous?.nextPageUrl
                var page = paged.fetchPage(cursor)
                // 升级前留下的快照没有游标：第 1 页是既有基线，直接翻到第 2 页。
                if (cursor == null && previous?.lastSuccessfulSyncDate != null) {
                    page = page.nextPageUrl?.let { paged.fetchPage(it) }
                        ?: page.copy(notices = emptyList(), nextPageUrl = null)
                }
                val existingIds = snapshot.notices.asSequence()
                    .filter { it.sourceId == source.sourceId }
                    .map(CampusNotice::articleId)
                    .toSet()
                val older = page.notices
                    .filterNot { it.articleId in existingIds }
                    .map { it.copy(discoveredAtMillis = nowMillis(), read = true) }
                val status = (previous ?: CampusNoticeSourceStatus(source.sourceId)).copy(
                    nextPageUrl = page.nextPageUrl,
                    historyExhausted = page.nextPageUrl == null,
                    historyError = null
                )
                val updated = snapshot.copy(
                    notices = (snapshot.notices + older).distinctBy { it.sourceId to it.articleId },
                    sourceStatuses = snapshot.sourceStatuses + (source.sourceId to status)
                )
                if (!fence.commitIfCurrent(observedGeneration, accountId) {
                        store.write(accountId, updated)
                    }) {
                    return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
                }
                snapshot = updated
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val updated = snapshot.copy(sourceStatuses = snapshot.sourceStatuses +
                    (source.sourceId to (previous ?: CampusNoticeSourceStatus(source.sourceId)).copy(
                        historyError = syncErrorMessage(error)
                    )))
                if (!fence.commitIfCurrent(observedGeneration, accountId) {
                        store.write(accountId, updated)
                    }) {
                    return@withLock CampusNoticeSyncResult(null, generation = observedGeneration, staleAccount = true)
                }
                snapshot = updated
            }
        }
        CampusNoticeSyncResult(snapshot, generation = observedGeneration)
    }

    private fun syncErrorMessage(error: Exception): String = when (error) {
        is CampusNoticeAccessBlockedException -> "访问受限（HTTP 412）"
        is CampusNoticeProtocolException -> error.message ?: "网站结构已变化"
        else -> "更新失败，请稍后重试"
    }

    suspend fun markRead(accountId: String, articleId: String?): CampusNoticeSnapshot? = mutex.withLock {
        val observedGeneration = fence.generation()
        if (fence.currentAccountId() != accountId) return@withLock null
        val previous = expireUnread(store.read(accountId))
        val updated = previous.copy(notices = previous.notices.map { notice ->
            if (articleId == null || notice.articleId == articleId) notice.copy(read = true) else notice
        })
        if (!fence.commitIfCurrent(observedGeneration, accountId) { store.write(accountId, updated) }) null
        else updated
    }

    suspend fun setNotificationsEnabled(accountId: String, enabled: Boolean): CampusNoticeSnapshot? = mutex.withLock {
        val observedGeneration = fence.generation()
        if (fence.currentAccountId() != accountId) return@withLock null
        val updated = expireUnread(store.read(accountId)).copy(notificationsEnabled = enabled)
        if (!fence.commitIfCurrent(observedGeneration, accountId) { store.write(accountId, updated) }) null
        else updated
    }

    private fun expireUnread(snapshot: CampusNoticeSnapshot): CampusNoticeSnapshot {
        val cutoff = nowMillis() - unreadLifetimeMillis
        val notices = snapshot.notices.map { notice ->
            if (!notice.read && notice.discoveredAtMillis > 0 && notice.discoveredAtMillis < cutoff) {
                notice.copy(read = true)
            } else notice
        }
        return if (notices == snapshot.notices) snapshot else snapshot.copy(notices = notices)
    }
}
