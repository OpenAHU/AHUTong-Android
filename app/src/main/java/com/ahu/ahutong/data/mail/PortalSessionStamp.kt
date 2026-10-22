package com.ahu.ahutong.data.mail

import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator

/** The mailbox portal uses central CAS; academic refreshes have an independent generation. */
internal class PortalSessionStamp {
    private val scope = SessionRefreshCoordinator.Scope.CENTRAL_CAS
    private val identity = SessionRefreshCoordinator.currentIdentityGeneration()
    private var generation = SessionRefreshCoordinator.currentGeneration(scope)

    fun isCurrent(): Boolean =
        identity == SessionRefreshCoordinator.currentIdentityGeneration() &&
            !SessionRefreshCoordinator.isExplicitlySignedOut() &&
            generation == SessionRefreshCoordinator.currentGeneration(scope)

    suspend fun refresh(action: suspend () -> SessionRefreshCoordinator.RefreshOutcome): Boolean {
        val refreshed = SessionRefreshCoordinator.refreshIfNeeded(generation, scope = scope, refresh = action)
        if (!refreshed || identity != SessionRefreshCoordinator.currentIdentityGeneration() ||
            SessionRefreshCoordinator.isExplicitlySignedOut()
        ) return false
        // Another central-CAS consumer may already have refreshed this identity.
        generation = SessionRefreshCoordinator.currentGeneration(scope)
        return true
    }

    suspend fun commit(action: () -> Unit) =
        SessionRefreshCoordinator.commitIfCurrent(generation, scope) {
            if (isCurrent()) action()
        }
}
