package com.ahu.ahutong.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * Shared spacing / radius tokens used across screens.
 *
 * Prefer these over magic numbers so new feature pages stay visually consistent.
 */
object AhuDimens {
    /** Horizontal inset for page title blocks. */
    val TitleHorizontal = 24.dp

    /** Top inset for page title blocks. */
    val TitleTop = 32.dp

    /** Horizontal inset for cards / list groups. */
    val ContentHorizontal = 16.dp

    /** Default vertical gap between page sections. */
    val SectionSpacing = 24.dp

    /** Space reserved so scroll content clears the liquid bottom nav. */
    val BottomNavClearance = 96.dp

    /** Large content card corner radius (settings groups, grade cards…). */
    val CardCorner = 32.dp

    /** Medium card / tool tile corner radius. */
    val CardCornerMedium = 16.dp

    /** Compact list-row inner corner when grouped. */
    val ListItemCorner = 4.dp

    /** Default internal padding for [com.ahu.ahutong.ui.components.AhuCard]. */
    val CardPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

    /** Page title padding: start/end 24, top 32. */
    val TitlePadding = PaddingValues(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 0.dp)
}
