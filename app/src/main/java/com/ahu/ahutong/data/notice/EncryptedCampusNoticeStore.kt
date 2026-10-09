package com.ahu.ahutong.data.notice

import com.ahu.ahutong.data.security.SecureBoxStore
import com.ahu.ahutong.data.security.SecureStorage
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

/** Dedicated per-account boxes survive a routine account switch, but clear with all app data. */
@Singleton
internal class EncryptedCampusNoticeStore @Inject constructor() : CampusNoticeStore {
    private val gson = Gson()
    private val lock = Any()

    override fun read(accountId: String): CampusNoticeSnapshot = synchronized(lock) {
        val raw = SecureBoxStore.get(box(accountId), KEY_STATE)
        val restored = raw?.takeIf { it.length <= MAX_JSON_LENGTH }?.let {
            runCatching { gson.fromJson(it, CampusNoticeSnapshot::class.java) }.getOrNull()
        }
        runCatching {
            restored?.takeIf { snapshot ->
                snapshot.accountId == accountId &&
                    snapshot.notices.size <= MAX_NOTICES &&
                    snapshot.notices.all { it.sourceId.isNotBlank() && it.articleId.isNotBlank() && it.originalUrl.isNotBlank() } &&
                    snapshot.sourceStatuses.all { (sourceId, status) ->
                        sourceId.isNotBlank() && status.sourceId == sourceId
                    }
            }
        }.getOrNull() ?: CampusNoticeSnapshot(accountId)
    }

    override fun write(accountId: String, snapshot: CampusNoticeSnapshot) = synchronized(lock) {
        require(snapshot.accountId == accountId)
        require(snapshot.notices.size <= MAX_NOTICES)
        SecureBoxStore.put(box(accountId), KEY_STATE, gson.toJson(snapshot))
    }

    override fun clearAll() {
        synchronized(lock) { SecureStorage.clearPrefix("notice_user_") }
    }

    private fun box(accountId: String): String = "notice_${SecureBoxStore.userBox(accountId)}"

    private companion object {
        const val KEY_STATE = "campus_notices_v1"
        const val MAX_NOTICES = 5_000
        const val MAX_JSON_LENGTH = 3_000_000
    }
}
