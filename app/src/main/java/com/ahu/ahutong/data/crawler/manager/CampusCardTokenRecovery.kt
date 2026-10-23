package com.ahu.ahutong.data.crawler.manager

internal data class CampusCardTokenAttempt(
    val token: String? = null,
    val casLoginUrl: String? = null
)

/** Retry OAuth with a new one-shot ticket; an expired CAS session is renewed at most once. */
internal suspend fun fetchCampusCardTokenWithRecovery(
    fetchToken: suspend () -> CampusCardTokenAttempt,
    refreshSession: suspend (String) -> Boolean
): String? {
    var attempt = fetchToken()
    attempt.token?.takeIf { it.isNotBlank() }?.let { return it }

    // Bootstrap OAuth again on failure so recovery never depends on reusing a ticket
    // or returning "credential not ready" to the payment screen immediately.
    if (attempt.casLoginUrl == null) {
        attempt = fetchToken()
        attempt.token?.takeIf { it.isNotBlank() }?.let { return it }
    }

    val casLoginUrl = attempt.casLoginUrl ?: return null
    if (!refreshSession(casLoginUrl)) return null
    return fetchToken().token?.takeIf { it.isNotBlank() }
}
