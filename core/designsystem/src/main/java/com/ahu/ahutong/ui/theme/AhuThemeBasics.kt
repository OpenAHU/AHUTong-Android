package com.ahu.ahutong.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.ahu.ahutong.ui.components.LocalIsLiquidGlassEnabled
import com.kyant.monet.LocalTonalPalettes
import com.kyant.monet.TonalPalettes.Companion.toTonalPalettes
import com.kyant.monet.n1
import com.kyant.monet.toColor
import com.kyant.monet.toSrgb

/**
 * Lightweight theme wrapper owned by the design system.
 *
 * The app host still uses [com.ahu.ahutong.ui.theme.AHUTheme] which wires
 * PreferencesViewModel + system accent. Feature previews / isolated tests can
 * call this directly with a fixed [keyColor].
 */
@Composable
fun AhuTheme(
    keyColor: Color = Color(0xFF007FAC),
    useLiquidGlass: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalTonalPalettes provides keyColor.toSrgb().toColor().toTonalPalettes(),
            LocalContentColor provides if (isSystemInDarkTheme()) 100.n1 else 0.n1,
            LocalIsLiquidGlassEnabled provides useLiquidGlass,
        ) {
            content()
        }
    }
}
