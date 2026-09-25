package com.ahu.ahutong.data.model

import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import org.junit.Assert.*
import org.junit.Test

class AcademicFeatureAccessTest {
    @Test fun graduateCannotOpenAnyUndergraduateAcademicRoute() {
        for (route in listOf("schedule", "grade", "exam", "evaluation", "free_classroom", "info")) {
            assertFalse(route, AcademicFeatureAccess.allowsRoute(AcademicAccountType.POSTGRADUATE, route))
            assertFalse(route, AcademicFeatureAccess.allowsRoute(AcademicAccountType.POSTGRADUATE, "$route?source=deeplink"))
            assertTrue(route, AcademicFeatureAccess.allowsRoute(AcademicAccountType.UNDERGRADUATE, route))
        }
    }

    @Test fun sharedCampusServicesRemainAvailable() {
        for (route in listOf("home", "tools", "settings", "bathroom_deposit", "electricity_pay",
            "card_balance_deposit", "network_recharge", "lost_found", "phone_book", "weather",
            "school_calendar", "repository", "xuexiaotong")) {
            assertTrue(route, AcademicFeatureAccess.allowsRoute(AcademicAccountType.POSTGRADUATE, route))
        }
    }

    @Test fun allHomeThemesAndWidgetLibrariesExcludeUndergraduateShortcuts() {
        for (radiant in listOf(false, true)) {
            val graduate = HomeWidgetRegistry.availableWidgets(radiant, undergraduateEnabled = false)
            assertTrue(graduate.none { it.route in AcademicFeatureAccess.undergraduateRoutes })
            assertTrue(graduate.any { it.route == "bathroom_deposit" })
            val undergraduate = HomeWidgetRegistry.availableWidgets(radiant, undergraduateEnabled = true)
            assertTrue(undergraduate.any { it.route == "grade" })
            assertTrue(undergraduate.any { it.route == "exam" })
        }
    }
}
