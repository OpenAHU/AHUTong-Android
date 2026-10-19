package com.ahu.ahutong.data.preview

import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import kotlinx.coroutines.CancellationException

/** Captures the account before selecting a channel and rejects its late update response. */
class EarlyAccessUpdateCheck(
    private val identities: EarlyAccessIdentitySource,
    private val eligibleSubject: () -> String?,
    private val fetch: suspend (String?) -> ApkUpdateInfo
) {
    suspend fun latest(): ApkUpdateInfo {
        val observed = identities.current()
        val subject = eligibleSubject()?.takeIf { it == observed.subject }
        val result = fetch(subject)
        if (identities.current() != observed || eligibleSubject()?.takeIf { it == observed.subject } != subject) {
            throw CancellationException("Account or early access membership changed during update check")
        }
        return result
    }
}
