package com.ahu.ahutong.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.UiStyle
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.ahu.ahutong.ui.components.LocalUiStyle
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.dynamicColorScheme
import com.kyant.monet.n1
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb

@Composable
fun AHUTheme(content: @Composable () -> Unit) {
    val preferencesViewModel: PreferencesViewModel = hiltViewModel()
    val themeColorHex by preferencesViewModel.themeColor.collectAsState()
    val themeMode by preferencesViewModel.appThemeMode.collectAsState()
    val uiStyle by preferencesViewModel.uiStyle.collectAsState()
    val useLiquidGlass = uiStyle != UiStyle.ORIGINAL
    val isDarkTheme = themeMode.resolve(isSystemInDarkTheme())
    val configuration = LocalConfiguration.current
    val themeConfiguration = remember(configuration, isDarkTheme) {
        Configuration(configuration).apply {
            val nightMode = if (isDarkTheme) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
    }
    val view = LocalView.current

    SideEffect {
        view.context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    val customKeyColor = remember(themeColorHex) {
        themeColorHex?.let { value ->
            runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
        }
    }
    val keyColor = when {
        customKeyColor != null -> customKeyColor
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            colorResource(id = android.R.color.system_accent1_500)
        else -> Color(0xFF007FAC)
    }
    val tonalPalettes = remember(keyColor) {
        keyColor.toSrgb().toColor().toTonalPalettes()
    }

    CompositionLocalProvider(
        LocalConfiguration provides themeConfiguration,
        LocalTonalPalettes provides tonalPalettes
    ) {
        MaterialTheme(colorScheme = dynamicColorScheme(isLight = !isDarkTheme)) {
            CompositionLocalProvider(
                LocalContentColor provides if (isDarkTheme) 100.n1 else 0.n1,
                LocalIsLiquidGlassEnabled provides useLiquidGlass,
                LocalUiStyle provides uiStyle,
                content = content
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
