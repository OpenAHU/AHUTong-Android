package com.ahu.ahutong.data.notice

import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.data.AHURepository
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
            CollegeNoticeSource({ CollegeNoticeRegistry.resolveCurrent() }, CollegeNoticeWebFetcher()),
            PostgraduateNoticeCrawler(PostgraduateNoticeWebFetcher())
        ),
        store = store,
        fence = fence,
        isSourceEnabled = { source, _ ->
            when (source.sourceId) {
                CollegeNoticeRegistry.SOURCE_ID -> CollegeNoticeRegistry.resolveCurrent() != null
                PostgraduateNoticeParser.SOURCE_ID -> !AHUCache.canUseUndergraduateAcademics()
                else -> true
            }
        }
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableSnapshot = MutableStateFlow<CampusNoticeSnapshot?>(null)
    val snapshot: StateFlow<CampusNoticeSnapshot?> get() = mutableSnapshot
    private val mutableSyncingAccountId = MutableStateFlow<String?>(null)
    val syncingAccountId: StateFlow<String?> get() = mutableSyncingAccountId
    private val mutableLoadingMoreAccountId = MutableStateFlow<String?>(null)
    val loadingMoreAccountId: StateFlow<String?> get() = mutableLoadingMoreAccountId
    private var syncJob: Job? = null
    private var loadMoreJob: Job? = null
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
                // 学院源依赖学籍院系；首次使用通知时顺手补一次学籍缓存（已有缓存则不发请求）。
                if (CollegeNoticeRegistry.resolveCurrent() == null && AHUCache.getStudentInfo() == null) {
                    runCatching { AHURepository.getStudentInfo(force = false) }
                }
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

    /** 首页同步正在进行时避免再排队一次历史翻页；引擎内部也有互斥锁兜底。 */
    @Synchronized
    fun requestLoadMore(sourceId: String? = null) {
        if (!SessionStore.isLoggedIn()) return
        val accountId = SessionStore.currentUser()?.xh?.takeIf(String::isNotBlank) ?: return
        if (syncJob?.isActive == true || loadMoreJob?.isActive == true) return
        loadMoreJob = scope.launch {
            mutableLoadingMoreAccountId.value = accountId
            try {
                val result = runCatching { engine.loadMore(accountId, sourceId) }.getOrNull() ?: return@launch
                val current = result.snapshot ?: return@launch
                if (fence.currentAccountId() == accountId &&
                    fence.generation() == result.generation
                ) {
                    mutableSnapshot.value = current
                }
            } finally {
                if (mutableLoadingMoreAccountId.value == accountId) {
                    mutableLoadingMoreAccountId.value = null
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
        val pendingLoadMore = synchronized(this) {
            loadMoreJob.also { loadMoreJob = null }
        }
        pendingLoadMore?.cancelAndJoin()
        withContext(Dispatchers.IO) { store.clearAll() }
        mutableSnapshot.value = null
        mutableSyncingAccountId.value = null
        mutableLoadingMoreAccountId.value = null
    }
}
