package com.ahu.ahutong.data.dao

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeWidgetDefaultsTest {
    @Test fun `new user classic layout keeps existing entries and pins canteen ranking`() {
        assertEquals(8, HomeWidgetDefaults.classic.size)
        assertEquals(listOf("bathroom", "electricity", "canteen_ranking"), HomeWidgetDefaults.classic.take(3))
        assertTrue(HomeWidgetDefaults.classic.drop(3).all { it == null })
    }

    @Test fun `radiant default includes canteen ranking exactly once within seven slots`() {
        assertEquals(7, HomeWidgetDefaults.radiant.size)
        assertEquals("canteen_ranking", HomeWidgetDefaults.radiant.last())
        assertEquals(1, HomeWidgetDefaults.radiant.count { it == HomeWidgetDefaults.PINNED_WIDGET_ID })
    }
}
