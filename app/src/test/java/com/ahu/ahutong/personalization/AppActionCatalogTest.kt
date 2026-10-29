package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.action.PrefetchPolicy
import com.ahu.ahutong.personalization.journey.JourneyGoalCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.ahu.ahutong.ui.screen.main.home.HomeWidgetRegistry
import java.io.File

class AppActionCatalogTest {
    @Test
    fun everyEnumValueHasExactlyOneSpec() {
        assertEquals(AppActionId.entries.size, AppActionCatalog.specs.size)
        AppActionId.entries.forEach { assertNotNull(AppActionCatalog.spec(it)) }
    }

    @Test
    fun transactionsCanNeverBeSuggested() {
        assertFalse(
            AppActionCatalog.specs.any { it.sideEffect == SideEffect.TRANSACTION && it.suggestible }
        )
    }

    @Test
    fun transactionsCanNeverBeRegisteredForPrefetch() {
        assertFalse(
            AppActionCatalog.specs.any {
                it.sideEffect == SideEffect.TRANSACTION && it.prefetchPolicy != PrefetchPolicy.NONE
            }
        )
    }

    @Test
    fun recentElectricityRoomsRouteUsesPaymentEntryAction() {
        assertEquals(
            AppActionId.OPEN_ELECTRICITY_PAYMENT,
            AppActionCatalog.actionForRoute("electricity_recent_rooms")
        )
    }

    @Test
    fun electricityAlertSettingsRouteHasCatalogMapping() {
        assertTrue("electricity_alert_settings" in AppActionCatalog.navigationRouteManifest)
        assertEquals(
            AppActionId.OPEN_ELECTRICITY_PAYMENT,
            AppActionCatalog.actionForRoute("electricity_alert_settings")
        )
    }

    @Test
    fun catalogPreservesTheEntireSchemaOneOutputAbi() {
        val schemaOneActions = """
            OPEN_HOME VIEW_SCHEDULE OPEN_TOOLS OPEN_SETTINGS VIEW_SCHOOL_CALENDAR VIEW_GRADES
            OPEN_PHONE_BOOK VIEW_EXAM_ROOM OPEN_EVALUATION FIND_FREE_CLASSROOM OPEN_LOST_FOUND
            VIEW_WEATHER OPEN_PAYMENT_QR REFRESH_PAYMENT_QR OPEN_REPOSITORY OPEN_REPOSITORY_DIRECTORY
            OPEN_REPOSITORY_DOWNLOADS OPEN_REPOSITORY_SETTINGS OPEN_REPOSITORY_ITEM DOWNLOAD_REPOSITORY_ITEM
            OPEN_BATHROOM_DEPOSIT OPEN_ELECTRICITY_PAYMENT OPEN_CARD_RECHARGE OPEN_CMB_CARD_RECHARGE
            OPEN_NETWORK_RECHARGE CONFIRM_BATHROOM_PAYMENT CONFIRM_ELECTRICITY_PAYMENT SUBMIT_CARD_RECHARGE
            SUBMIT_CMB_CARD_RECHARGE SUBMIT_NETWORK_RECHARGE OPEN_PREFERENCES OPEN_LICENSES OPEN_CONTRIBUTORS
            OPEN_INFO EDIT_HOME MANUAL_REFRESH_SCHEDULE MANUAL_REFRESH_EXAM MANUAL_REFRESH_GRADE
            MANUAL_REFRESH_REPOSITORY RETRY_GRADE RETRY_EXAM RETRY_REPOSITORY OPEN_COURSE_DETAIL
        """.trim().split(Regex("\\s+"))
        assertEquals(schemaOneActions + listOf("OTHER", "NONE"), AppActionCatalog.outputIds)
        assertEquals(1, AppActionCatalog.ACTION_CATALOG_VERSION)
        assertEquals(1, AppActionCatalog.OUTPUT_SCHEMA_VERSION)
        assertEquals(1, JourneyGoalCatalog.OUTPUT_SCHEMA_VERSION)
        val schemaOneJourneyExclusions = setOf(
            "OPEN_HOME", "OPEN_TOOLS", "OPEN_SETTINGS", "OPEN_PREFERENCES", "EDIT_HOME",
            "CONFIRM_BATHROOM_PAYMENT", "CONFIRM_ELECTRICITY_PAYMENT", "SUBMIT_CARD_RECHARGE",
            "SUBMIT_CMB_CARD_RECHARGE", "SUBMIT_NETWORK_RECHARGE"
        )
        val oldJourneyActions = schemaOneActions.filterNot { it in schemaOneJourneyExclusions }
        assertEquals(oldJourneyActions + listOf("OTHER", "NONE"), JourneyGoalCatalog.outputIds)
    }

    @Test
    fun newlyMappedRoutesKeepTheirPreviousNonLearningBehavior() {
        val actions = mapOf(
            "campus_notices" to AppActionId.VIEW_CAMPUS_NOTICES,
            "student_mail" to AppActionId.OPEN_STUDENT_MAIL,
            "program_completion" to AppActionId.VIEW_PROGRAM_COMPLETION,
            "identity_code" to AppActionId.OPEN_IDENTITY_CODE,
            "plugin/{pluginId}" to AppActionId.OPEN_PLUGIN,
            "settings__early_access" to AppActionId.OPEN_EARLY_ACCESS,
            "settings__privacy_policy" to AppActionId.OPEN_PRIVACY_POLICY,
            "settings__theme_lab" to AppActionId.OPEN_THEME_LAB,
            "settings__theme_color" to AppActionId.OPEN_THEME_COLORS,
            "scan" to AppActionId.OPEN_SCANNER,
            "billing" to AppActionId.VIEW_BILLING,
            "billing_stats" to AppActionId.VIEW_BILLING_STATS
        )
        actions.forEach { (route, action) ->
            assertEquals(action, AppActionCatalog.actionForRoute(route), route)
            val spec = AppActionCatalog.spec(action)
            assertFalse(spec.labelEligible, route)
            assertFalse(spec.predictable, route)
            assertFalse(spec.suggestible, route)
            assertEquals(PrefetchPolicy.NONE, spec.prefetchPolicy, route)
            assertFalse(action.stableId in AppActionCatalog.outputIds, route)
        }
    }

    @Test
    fun pluginRoutesUseOneTechnicalActionWithoutPredictingOrSuggestingPluginExecution() {
        listOf("plugin/{pluginId}", "plugin/campus_circle", "plugin/docscan").forEach {
            assertEquals(AppActionId.OPEN_PLUGIN, AppActionCatalog.actionForRoute(it))
        }
        assertEquals(null, AppActionCatalog.actionForRoute("plugin/"))
        assertEquals(null, AppActionCatalog.actionForRoute("plugin/campus_circle/other"))
        val plugin = AppActionCatalog.spec(AppActionId.OPEN_PLUGIN)
        assertFalse(plugin.labelEligible)
        assertFalse(plugin.predictable)
        assertFalse(plugin.suggestible)
        assertEquals(PrefetchPolicy.NONE, plugin.prefetchPolicy)
    }

    @Test
    fun everyNavigationAndHomeWidgetRouteHasCatalogMapping() {
        AppActionCatalog.navigationRouteManifest.forEach { route ->
            assertNotNull(AppActionCatalog.actionForRoute(route), route)
        }
        HomeWidgetRegistry.widgets.forEach { widget ->
            val route = assertNotNull(widget.route, "home widget route must not be null")
            assertNotNull(AppActionCatalog.actionForRoute(route), "unmapped widget route: $route")
        }
    }

    @Test
    fun navigationManifestMatchesAllProductionMainRoutes() {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        val repositoryRoot = generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val mainSource = File(
            repositoryRoot,
            "app/src/main/java/com/ahu/ahutong/ui/screen/Main.kt"
        ).readText()
        val literalRoutes = Regex("animatedComposable\\((?:[A-Za-z_][A-Za-z0-9_]*,\\s*)?\\\"([^\\\"]+)\\\"")
            .findAll(mainSource)
            .map { it.groupValues[1] }
            .filterNot { it == "debug" }
            .toSet()
        val dynamicRepositoryRoutes = setOf("repository", "repository/{path}")
        assertEquals(
            AppActionCatalog.navigationRouteManifest,
            literalRoutes + dynamicRepositoryRoutes,
            "update the typed action catalog whenever Main adds or removes a production route"
        )
    }

    @Test
    fun businessAvailabilityKeepsReservedOutputsAndScopesCommandsToTheirPage() {
        val home = AppActionCatalog.businessAvailability("home")
        assertTrue(home[AppActionCatalog.outputIndex.getValue(AppActionId.OPEN_PAYMENT_QR.stableId)])
        assertFalse(home[AppActionCatalog.outputIndex.getValue(AppActionId.SUBMIT_CARD_RECHARGE.stableId)])
        assertTrue(home[AppActionCatalog.outputIndex.getValue(AppActionCatalog.OTHER_OUTPUT_ID)])
        assertTrue(home[AppActionCatalog.outputIndex.getValue(AppActionCatalog.NONE_OUTPUT_ID)])
    }
}
