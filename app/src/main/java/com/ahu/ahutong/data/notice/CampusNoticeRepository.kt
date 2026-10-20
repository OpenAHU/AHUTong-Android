package com.ahu.ahutong.data.notice

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.notification.CampusNoticeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Front-of-app, once-per-day checks only. No WorkManager, service or periodic polling. */
object CampusNoticeRepository {
    private val store = EncryptedCampusNoticeStore()
    private val fence = SessionCampusNoticeFence()
    private val engine = CampusNoticeSyncEngine(
        sources = listOf(
            UniversityNoticeCrawler(UniversityNoticeHttpFetcher()),
            AcademicAffairsNoticeCrawler(AcademicAffairsNoticeWebFetcher()),
            PostgraduateNoticeCrawler(PostgraduateNoticeWebFetcher())
        ),
        store = store,
        fence = fence,
        isSourceEnabled = { source, _ ->
            source.sourceId != PostgraduateNoticeParser.SOURCE_ID || !AHUCache.canUseUndergraduateAcademics()
        }
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSnapshot = MutableStateFlow<CampusNoticeSnapshot?>(null)
    val snapshot: StateFlow<CampusNoticeSnapshot?> get() = mutableSnapshot
    private val mutableSyncingAccountId = MutableStateFlow<String?>(null)
    val syncingAccountId: StateFlow<String?> get() = mutableSyncingAccountId
    private var syncJob: Job? = null
    private var syncAccountId: String? = null
    private var syncRequestToken = 0L

    fun open(accountId: String) {
        CampusNoticeNotifier.cancelOtherAccounts(AppEnvironmentHolder.context(), accountId)
        scope.launch {
            val current = engine.snapshot(accountId)
            if (fence.currentAccountId() == accountId) mutableSnapshot.value = current
        }
    }

    /** Notification intents are untrusted; resolve against the current account's stored article. */
    suspend fun verifiedNotice(accountId: String, articleId: String, url: String): CampusNotice? =
        withContext(Dispatchers.IO) {
            val generation = fence.generation()
            if (!SessionStore.isLoggedIn() || fence.currentAccountId() != accountId) {
                return@withContext null
            }
            val notice = engine.snapshot(accountId).notices.firstOrNull {
                it.articleId == articleId && it.originalUrl == url
            } ?: return@withContext null
            notice.takeIf {
                CampusNoticeUrlPolicy.isTrustedArticle(it.sourceId, it.originalUrl) &&
                    fence.currentAccountId() == accountId && fence.generation() == generation
            }
        }

    @Synchronized
    fun requestSyncIfDue(force: Boolean = false) {
        if (!SessionStore.isLoggedIn()) return
        val accountId = SessionStore.currentUser()?.xh?.takeIf(String::isNotBlank) ?: return
        if (syncJob?.isActive == true) {
            if (syncAccountId == accountId) return
            syncJob?.cancel()
        }
        syncAccountId = accountId
        val requestToken = ++syncRequestToken
        syncJob = scope.launch {
            mutableSyncingAccountId.value = accountId
            try {
                val result = runCatching { engine.sync(accountId, force) }.getOrNull() ?: return@launch
                val current = result.snapshot ?: return@launch
                if (fence.currentAccountId() != accountId || fence.generation() != result.generation) return@launch
                mutableSnapshot.value = current
                if (current.notificationsEnabled && result.newUnread.isNotEmpty()) {
                    fence.commitIfCurrent(result.generation, accountId) {
                        CampusNoticeNotifier.post(AppEnvironmentHolder.context(), accountId, result.newUnread)
                    }
                }
            } finally {
                synchronized(this@CampusNoticeRepository) {
                    if (syncRequestToken == requestToken) mutableSyncingAccountId.value = null
                }
            }
        }
    }

    fun markRead(accountId: String, articleId: String?) {
        scope.launch {
            val updated = engine.markRead(accountId, articleId) ?: return@launch
            if (fence.currentAccountId() == accountId) mutableSnapshot.value = updated
        }
    }

    fun setNotificationsEnabled(accountId: String, enabled: Boolean) {
        scope.launch {
            val updated = engine.setNotificationsEnabled(accountId, enabled) ?: return@launch
            if (fence.currentAccountId() == accountId) mutableSnapshot.value = updated
        }
    }

    suspend fun clearAll() {
        val pending = synchronized(this) {
            syncRequestToken++
            syncJob.also { syncJob = null; syncAccountId = null }
        }
        pending?.cancelAndJoin()
        withContext(Dispatchers.IO) { store.clearAll() }
        mutableSnapshot.value = null
        mutableSyncingAccountId.value = null
    }
}
