package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Rounded surface card — the default content container across the app.
 *
 * Matches the recurring pattern:
 * `clip(SmoothRoundedCornerShape(32.dp)).background(...).padding(...)`.
 */
@Composable
fun AhuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AhuDimens.CardCorner,
    containerColor: Color? = null,
    contentPadding: PaddingValues = AhuDimens.CardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = containerColor ?: AhuColors.card
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(cornerRadius))
            .background(bg)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Horizontally inset card group (settings sections, account blocks…).
 */
@Composable
fun AhuInsetCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = AhuDimens.CardCorner,
    containerColor: Color? = null,
    contentPadding: PaddingValues = AhuDimens.CardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AhuCard(
        modifier = modifier.padding(horizontal = AhuDimens.ContentHorizontal),
        cornerRadius = cornerRadius,
        containerColor = containerColor,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        onClick = onClick,
        content = content,
    )
}
