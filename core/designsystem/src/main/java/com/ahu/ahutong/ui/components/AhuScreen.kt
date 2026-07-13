package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Standard full-screen page shell used by most feature screens.
 *
 * Applies system-bar padding and optional bottom-nav clearance. Prefer this
 * over hand-rolling `Column(Modifier.fillMaxSize().systemBarsPadding()…)` in
 * new feature modules.
 *
 * @param scrollable when true, content is placed in a vertical scroll column.
 * @param clearBottomNav when true, adds [AhuDimens.BottomNavClearance] so content
 *   is not covered by the host liquid bottom bar (home / schedule / tools / settings).
 * @param contentPaddingBottom extra bottom padding beyond bottom-nav clearance.
 */
@Composable
fun AhuScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    clearBottomNav: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AhuDimens.SectionSpacing),
    contentPaddingBottom: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bottomPadding =
        contentPaddingBottom + if (clearBottomNav) AhuDimens.BottomNavClearance else 0.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(bottom = bottomPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Full-screen box shell for pages that need free positioning (overlays, pagers…).
 */
@Composable
fun AhuScreenBox(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
        contentAlignment = contentAlignment,
        content = content,
    )
}
