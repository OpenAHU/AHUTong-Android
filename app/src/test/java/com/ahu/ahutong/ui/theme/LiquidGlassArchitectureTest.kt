package com.ahu.ahutong.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidGlassArchitectureTest {
    @Test
    fun `main owns the global backdrop host and conditional content capture`() {
        val main = source("com/ahu/ahutong/ui/screen/Main.kt")
        val surface = source("com/ahu/ahutong/ui/components/LiquidGlassSurface.kt")

        assertTrue(main.contains("LiquidGlassAppHost(modifier = Modifier.fillMaxSize())"))
        assertTrue(main.contains(".captureLiquidGlassContent()"))
        assertFalse(main.contains("rememberLayerBackdrop()"))
        assertTrue(surface.contains("tokens.quality.supportsBackdrop"))
        assertTrue(surface.contains("LocalLiquidGlassAmbientBackdrop provides ambientBackdrop"))
        assertTrue(surface.contains("LocalLiquidGlassContentBackdrop provides contentBackdrop"))
        assertTrue(surface.contains("else if (tokens.enabled)"))
        assertTrue(surface.contains("LiquidGlassQuality.Tinted"))
    }

    @Test
    fun `settings use the shared surface instead of a private glass implementation`() {
        val settings = source("com/ahu/ahutong/ui/components/SettingsComponents.kt")
        val sharedComponents = source("com/ahu/ahutong/ui/components/AppComponents.kt")

        assertTrue(settings.contains(".appLiquidGlassSurface("))
        assertTrue(settings.contains("LocalLiquidGlassAmbientBackdrop.current"))
        assertTrue(sharedComponents.contains("backdropSamplingEnabled = false"))
        assertFalse(settings.contains("private fun Modifier.liquidGlassSurface"))
        assertFalse(settings.contains("rememberLayerBackdrop()"))
    }

    @Test
    fun `theme selection is gated until persisted state is ready`() {
        val viewModel = source("com/ahu/ahutong/ui/state/PreferencesViewModel.kt")
        val theme = source("com/ahu/ahutong/ui/theme/AHUTheme.kt")
        val preferences = source("com/ahu/ahutong/ui/screen/settings/Preferences.kt")
        val themeSettings = source("com/ahu/ahutong/ui/screen/settings/ThemeLab.kt")
        val local = source("com/ahu/ahutong/ui/components/LocalIsLiquidGlassEnabled.kt")

        assertTrue(viewModel.contains("_isUiThemePreferenceReady = MutableStateFlow(startupThemePreferences != null)"))
        assertTrue(viewModel.contains("_isUiThemePreferenceReady.value = true"))
        assertTrue(theme.contains("enabled = isUiThemePreferenceReady && appUiTheme.usesLiquidGlass"))
        assertTrue(theme.contains("MiuixTheme(controller = miuixController)"))
        assertTrue(theme.contains("ColorSchemeMode.MonetLight"))
        assertTrue(theme.contains("ColorSchemeMode.MonetDark"))
        assertTrue(preferences.contains("ThemeSettingsSection("))
        assertTrue(themeSettings.contains("showMiuixDefault = appUiTheme == AppUiTheme.MIUIX"))
        assertTrue(local.contains("LocalAppUiTheme"))
        assertTrue(local.contains("compositionLocalOf { false }"))
    }

    @Test
    fun `glass controls honor policy and expose adjustable selection semantics`() {
        val button = source("com/ahu/ahutong/ui/components/LiquidButton.kt")
        val slider = source("com/ahu/ahutong/ui/components/LiquidSlider.kt")
        val toggle = source("com/ahu/ahutong/ui/components/LiquidToggle.kt")
        val tabs = source("com/ahu/ahutong/ui/components/LiquidBottomTabs.kt")
        val tab = source("com/ahu/ahutong/ui/components/LiquidBottomTab.kt")

        assertTrue(button.contains("LocalLiquidGlassTokens.current"))
        assertTrue(button.contains("tokens.quality.supportsRefraction"))
        assertTrue(slider.contains("!tokens.quality.supportsBlur"))
        assertTrue(slider.contains("setProgress { requestedValue ->"))
        assertTrue(slider.contains("heightIn(min = 48.dp)"))
        assertFalse(slider.contains("isSystemInDarkTheme"))
        assertTrue(toggle.contains("if (!tokens.quality.supportsBlur)"))
        assertTrue(toggle.contains("heightIn(min = 48.dp)"))
        assertTrue(toggle.contains("toggleableState = if (currentSelected.value())"))
        assertTrue(toggle.contains("currentOnSelect.value(!currentSelected.value())"))
        assertTrue(tabs.contains("tokens.floating.legacyTint"))
        assertTrue(tabs.contains(".selectableGroup()"))
        assertTrue(tab.contains(".selectable("))
        assertTrue(tab.contains("selected = selected"))
    }

    /**
     * 主题、组件与动效工具已移入 :core:designsystem，因此按源集根逐个查找，
     * 而不是写死 app 的路径——写死路径的断言会在文件搬家时以"文件不存在"的形式失效。
     */
    private fun source(relativePath: String): String {
        val root = repositoryRoot()
        val file = SOURCE_ROOTS
            .map { File(root, it + relativePath) }
            .firstOrNull { it.isFile }
            ?: error("找不到源文件：$relativePath（已查找 $SOURCE_ROOTS）")
        return file.readText()
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    private companion object {
        val SOURCE_ROOTS = listOf(
            "app/src/main/java/",
            "core/designsystem/src/main/java/",
            "feature/settings/src/main/java/"
        )
    }
}
