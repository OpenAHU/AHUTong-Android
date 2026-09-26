package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.action.PrefetchPolicy
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
    fun outputSchemaHasReservedClassesAtEnd() {
        assertEquals(AppActionCatalog.OTHER_OUTPUT_ID, AppActionCatalog.outputIds.takeLast(2).first())
        assertEquals(AppActionCatalog.NONE_OUTPUT_ID, AppActionCatalog.outputIds.last())
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
    fun everyNonRoutePredictableActionHasAConcreteTypedCallSite() {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        val repositoryRoot = generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val catalogPath = "personalization${File.separator}action${File.separator}AppAction.kt"
        // 生产代码已经分布在多个模块里（feature / data / core），动作的调用点因此要在
        // 每个模块的源集里找——只扫 :app 会在文件搬进模块后以"没有调用点"的形式误报。
        val sources = moduleSourceRoots(repositoryRoot)
            .asSequence()
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" && !it.path.endsWith(catalogPath) }
            .joinToString("\n") { it.readText() }
        AppActionCatalog.specs
            .filter { it.predictable && it.route == null }
            .forEach { spec ->
                assertTrue(
                    sources.contains("AppActionId.${spec.id.name}"),
                    "${spec.id.name} has no typed semantic action call site"
                )
            }
    }

    @Test
    fun businessAvailabilityKeepsReservedOutputsAndScopesCommandsToTheirPage() {
        val home = AppActionCatalog.businessAvailability("home")
        assertTrue(home[AppActionCatalog.outputIndex.getValue(AppActionId.OPEN_PAYMENT_QR.stableId)])
        assertFalse(home[AppActionCatalog.outputIndex.getValue(AppActionId.SUBMIT_CARD_RECHARGE.stableId)])
        assertTrue(home[AppActionCatalog.outputIndex.getValue(AppActionCatalog.OTHER_OUTPUT_ID)])
        assertTrue(home[AppActionCatalog.outputIndex.getValue(AppActionCatalog.NONE_OUTPUT_ID)])
    }

    /**
     * 生产代码分布在多个模块里（:app、:core:*、:data:*、:feature:*），
     * 因此按"每个模块的源集根"逐个查找，而不是写死 app 的路径。
     */
    private fun moduleSourceRoots(repositoryRoot: File): List<File> =
        listOf(File(repositoryRoot, "app/src/main/java")) +
            listOf("core", "data", "feature").flatMap { parent ->
                File(repositoryRoot, parent).listFiles().orEmpty()
                    .sortedBy { it.name }
                    .map { File(it, "src/main/java") }
                    .filter { it.isDirectory }
            }
}
