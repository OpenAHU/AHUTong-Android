package com.ahu.ahutong.utils

import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.personalization.action.ActionSource

internal fun resolveVisibleRoute(
    navigationRoute: String?,
    uiTheme: AppUiTheme,
    primaryRoute: String
): String? = if (navigationRoute == "home" && uiTheme != AppUiTheme.RADIANT) {
    primaryRoute
} else {
    navigationRoute
}

internal data class NavigationSnapshot(
    val route: String?,
    val entryId: String?,
    val previousEntryId: String?,
    val uiTheme: AppUiTheme,
    val settled: Boolean = true,
    val diagnostics: Boolean = false,
    val primaryPagerHost: Boolean = false
)

internal data class NavigationObservation(
    val route: String,
    val source: ActionSource,
    val synchronizeOnly: Boolean = false
)

/** Classifies actual destination changes without confusing Pager pages with NavHost entries. */
internal class NavigationObservationPolicy {
    private data class Selection(val token: Long, val route: String, val source: ActionSource)

    private var previous: NavigationSnapshot? = null
    private val observedEntryIds = LinkedHashSet<String>()
    private var requestedRoute: Selection? = null
    private var nextSelectionToken = 0L

    fun expectSelection(route: String, source: ActionSource): Long {
        val token = ++nextSelectionToken
        requestedRoute = Selection(token, route, source)
        return token
    }

    fun cancelSelection(token: Long): Boolean {
        if (requestedRoute?.token != token) return false
        requestedRoute = null
        return true
    }

    fun observe(snapshot: NavigationSnapshot): NavigationObservation? {
        val route = snapshot.route ?: return null
        val entryId = snapshot.entryId ?: return null
        // A multi-page animation passes through pages the user did not choose.
        if (!snapshot.settled) return null
        // A hidden Pager can need its first layout before animateScrollToPage begins. Do not
        // observe its old page in that gap when returning from a secondary destination.
        if (snapshot.primaryPagerHost && requestedRoute?.route?.let { it != route } == true) {
            return null
        }

        val last = previous
        val returningToObservedEntry = entryId in observedEntryIds
        previous = snapshot
        // The public previous entry also seeds history after an Activity is recreated while a
        // secondary page is visible. No restricted NavController stack access is needed.
        snapshot.previousEntryId?.let(::rememberEntry)
        rememberEntry(entryId)
        val selection = requestedRoute?.takeIf { it.route == route }
        if (selection != null || last != null && entryId != last.entryId) {
            requestedRoute = null
        }
        if (last?.route == route) return null

        val themeOnlyChange = last != null && last.uiTheme != snapshot.uiTheme &&
            last.entryId == entryId && selection == null
        val source = when {
            snapshot.diagnostics -> ActionSource.DEBUG
            last == null -> ActionSource.RESTORE
            selection != null -> selection.source
            themeOnlyChange -> ActionSource.RESTORE
            entryId != last.entryId && returningToObservedEntry -> ActionSource.RESTORE
            else -> ActionSource.ORGANIC
        }
        return NavigationObservation(route, source, synchronizeOnly = themeOnlyChange)
    }

    private fun rememberEntry(entryId: String) {
        observedEntryIds.remove(entryId)
        observedEntryIds.add(entryId)
        if (observedEntryIds.size > 128) {
            val oldest = observedEntryIds.iterator()
            oldest.next()
            oldest.remove()
        }
    }
}
