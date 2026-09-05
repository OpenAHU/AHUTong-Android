package com.ahu.ahutong.utils

import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.personalization.action.ActionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationObservationPolicyTest {
    @Test
    fun `restored primary page starts as restore and later tab selections are organic`() {
        val policy = NavigationObservationPolicy()
        assertEquals(ActionSource.RESTORE, policy.observe(page("settings"))?.source)

        policy.expectSelection("schedule", ActionSource.ORGANIC)
        assertEquals(ActionSource.ORGANIC, policy.observe(page("schedule"))?.source)
        policy.expectSelection("tools", ActionSource.ORGANIC)
        assertEquals(ActionSource.ORGANIC, policy.observe(page("tools"))?.source)
        assertNull(policy.observe(page("tools")))
    }

    @Test
    fun `pager animation does not label intermediate pages`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home"))
        policy.expectSelection("settings", ActionSource.ORGANIC)
        assertNull(policy.observe(page("schedule", settled = false)))
        assertNull(policy.observe(page("tools", settled = false)))
        assertEquals(
            NavigationObservation("settings", ActionSource.ORGANIC),
            policy.observe(page("settings"))
        )
    }

    @Test
    fun `returning to a hidden pager waits for the requested page and keeps suggestion source`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        policy.observe(page("weather", stack = listOf("home-entry", "weather-1")))
        policy.expectSelection("schedule", ActionSource.SUGGESTION)
        assertNull(policy.observe(page("settings")))
        assertNull(policy.observe(page("tools", settled = false)))
        assertEquals(
            NavigationObservation("schedule", ActionSource.SUGGESTION),
            policy.observe(page("schedule"))
        )
    }

    @Test
    fun `system back from a pager tab is restore but a home tab click is organic`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        policy.expectSelection("home", ActionSource.RESTORE)
        assertEquals(ActionSource.RESTORE, policy.observe(page("home"))?.source)
        policy.observe(page("schedule"))
        policy.expectSelection("home", ActionSource.ORGANIC)
        assertEquals(ActionSource.ORGANIC, policy.observe(page("home"))?.source)
    }

    @Test
    fun `push and equal depth replacement are organic while popping to an existing entry restores`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home"))
        assertEquals(
            ActionSource.ORGANIC,
            policy.observe(page("weather", stack = listOf("home-entry", "weather-1")))?.source
        )
        assertEquals(
            ActionSource.ORGANIC,
            policy.observe(page("grade", stack = listOf("home-entry", "grade-1")))?.source
        )
        assertEquals(ActionSource.RESTORE, policy.observe(page("home"))?.source)
    }

    @Test
    fun `back from preferences restores its actual primary page`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        policy.observe(page("preferences", stack = listOf("home-entry", "preferences-1")))
        assertEquals(
            NavigationObservation("settings", ActionSource.RESTORE),
            policy.observe(page("settings"))
        )
    }

    @Test
    fun `public previous entry supports back after recreating the host in a nested page`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("evaluation", stack = listOf("home-entry", "grade-1", "evaluation-1")))
        assertEquals(
            ActionSource.RESTORE,
            policy.observe(page("grade", stack = listOf("home-entry", "grade-1")))?.source
        )
        assertEquals(ActionSource.RESTORE, policy.observe(page("home"))?.source)
    }

    @Test
    fun `long sessions retain the public previous entry as history is bounded`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home"))
        repeat(300) { index ->
            policy.observe(page("weather", stack = listOf("home-entry", "weather-$index")))
        }
        assertEquals(ActionSource.RESTORE, policy.observe(page("home"))?.source)
    }

    @Test
    fun `Radiant home tab intent stays organic even though it pops the destination stack`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home", theme = AppUiTheme.RADIANT))
        policy.observe(page("schedule", stack = listOf("home-entry", "schedule-1"), theme = AppUiTheme.RADIANT))
        policy.expectSelection("home", ActionSource.ORGANIC)
        assertEquals(
            ActionSource.ORGANIC,
            policy.observe(page("home", theme = AppUiTheme.RADIANT))?.source
        )
    }

    @Test
    fun `theme only page changes synchronize identity without recording a navigation`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        val change = policy.observe(page("home", theme = AppUiTheme.RADIANT))
        assertEquals("home", change?.route)
        assertTrue(change?.synchronizeOnly == true)
        assertNull(policy.observe(page("home", theme = AppUiTheme.LIQUID_GLASS)))
    }

    @Test
    fun `theme change inside preferences is silent but its later back is a real restore`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        policy.observe(page("preferences", stack = listOf("home-entry", "preferences-1")))
        assertNull(policy.observe(page("preferences", stack = listOf("home-entry", "preferences-1"), theme = AppUiTheme.RADIANT)))
        val back = policy.observe(page("home", theme = AppUiTheme.RADIANT))
        assertEquals(ActionSource.RESTORE, back?.source)
        assertFalse(back?.synchronizeOnly ?: true)
    }

    @Test
    fun `cancelled selection cannot misclassify a later unrelated navigation`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        policy.expectSelection("home", ActionSource.RESTORE)
        policy.observe(page("weather", stack = listOf("home-entry", "weather-1")))
        assertEquals(
            ActionSource.ORGANIC,
            policy.observe(page("home", stack = listOf("new-home-entry")))?.source
        )
    }

    @Test
    fun `cancelled animation unlocks the actual settled page in the same entry`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home"))
        val token = policy.expectSelection("settings", ActionSource.ORGANIC)
        assertNull(policy.observe(page("schedule", settled = false)))
        assertNull(policy.observe(page("schedule")))

        assertTrue(policy.cancelSelection(token))
        assertEquals(
            NavigationObservation("schedule", ActionSource.ORGANIC),
            policy.observe(page("schedule"))
        )
        policy.expectSelection("tools", ActionSource.ORGANIC)
        assertEquals(ActionSource.ORGANIC, policy.observe(page("tools"))?.source)
    }

    @Test
    fun `cancelling an old animation does not clear the newer selection`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("settings"))
        val oldToken = policy.expectSelection("home", ActionSource.RESTORE)
        policy.expectSelection("tools", ActionSource.SUGGESTION)

        assertFalse(policy.cancelSelection(oldToken))
        assertNull(policy.observe(page("schedule")))
        assertEquals(
            NavigationObservation("tools", ActionSource.SUGGESTION),
            policy.observe(page("tools"))
        )
    }

    @Test
    fun `cancelling a consumed selection does not replay the observed page`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home"))
        val token = policy.expectSelection("schedule", ActionSource.ORGANIC)
        policy.observe(page("schedule"))

        assertFalse(policy.cancelSelection(token))
        assertNull(policy.observe(page("schedule")))
    }

    @Test
    fun `diagnostics and suggested selections retain their explicit sources`() {
        val policy = NavigationObservationPolicy()
        policy.observe(page("home"))
        assertEquals(
            ActionSource.DEBUG,
            policy.observe(page("debug", stack = listOf("home-entry", "debug-1")).copy(diagnostics = true))?.source
        )
        policy.expectSelection("schedule", ActionSource.SUGGESTION)
        assertEquals(ActionSource.SUGGESTION, policy.observe(page("schedule"))?.source)
    }

    @Test
    fun `only non Radiant home destinations resolve through the primary pager`() {
        AppUiTheme.entries.forEach { theme ->
            assertEquals(
                if (theme == AppUiTheme.RADIANT) "home" else "settings",
                resolveVisibleRoute("home", theme, "settings")
            )
            assertEquals("preferences", resolveVisibleRoute("preferences", theme, "settings"))
            assertNull(resolveVisibleRoute(null, theme, "settings"))
        }
    }

    private fun page(
        route: String,
        stack: List<String> = listOf("home-entry"),
        theme: AppUiTheme = AppUiTheme.MATERIAL,
        settled: Boolean = true
    ) = NavigationSnapshot(
        route,
        stack.last(),
        stack.getOrNull(stack.lastIndex - 1),
        theme,
        settled,
        primaryPagerHost = theme != AppUiTheme.RADIANT && stack.last() == "home-entry"
    )
}
