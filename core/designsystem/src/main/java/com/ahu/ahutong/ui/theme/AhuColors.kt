package com.ahu.ahutong.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

/**
 * Semantic color tokens built on Monet tonal palettes.
 *
 * Screens should prefer these helpers over raw `xx.n1 withNight yy.n1` so
 * light/dark surfaces stay consistent when the design system is adjusted.
 */
object AhuColors {
    /** Root page / NavHost background (`Main` uses the same). */
    val pageBackground: Color
        @Composable get() = 96.n1 withNight 10.n1

    /** Default elevated card surface. */
    val card: Color
        @Composable get() = 100.n1 withNight 20.n1

    /** Slightly stronger card surface (tools tiles, icon action groups). */
    val cardStrong: Color
        @Composable get() = 100.n1 withNight 30.n1

    /** Accent-tinted surface (hero / brand blocks). */
    val accentSurface: Color
        @Composable get() = 90.a1 withNight 20.n1

    /** Filled primary control background. */
    val primaryAction: Color
        @Composable get() = 90.a1 withNight 85.a1

    /** Text / icon on [primaryAction]. */
    val onPrimaryAction: Color
        @Composable get() = 0.n1

    /** Primary body / title text. */
    val onSurface: Color
        @Composable get() = 0.n1 withNight 100.n1

    /** Soft chip / filter background when selected. */
    val chipSelected: Color
        @Composable get() = 90.a1 withNight 30.n1

    /** Soft chip background when unselected. */
    val chipUnselected: Color
        @Composable get() = 100.n1 withNight 30.n1
}
