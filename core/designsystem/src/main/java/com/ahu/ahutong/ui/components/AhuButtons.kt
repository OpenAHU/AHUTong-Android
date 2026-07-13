package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.AhuColors
import com.kyant.capsule.ContinuousCapsule

/**
 * Filled capsule text button (login / primary actions / dialog confirms).
 */
@Composable
fun AhuPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color? = null,
    contentColor: Color? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    val bg = containerColor ?: AhuColors.primaryAction
    val fg = contentColor ?: AhuColors.onPrimaryAction
    Text(
        text = text,
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        color = fg,
        style = textStyle,
    )
}

/**
 * Grouped icon actions in a capsule surface (refresh + search on Grade/Exam).
 */
@Composable
fun AhuIconActionGroup(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val bg = containerColor ?: AhuColors.cardStrong
    Row(
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(bg),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Convenience [IconButton] for [AhuIconActionGroup] / page headers.
 */
@Composable
fun AhuHeaderIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}

/**
 * Compact chip-like capsule control for filters / week selectors.
 */
@Composable
fun AhuChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) AhuColors.chipSelected else AhuColors.chipUnselected
    val fg = if (selected) AhuColors.onPrimaryAction else AhuColors.onSurface
    Text(
        text = text,
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * Horizontal row of chips with standard spacing.
 */
@Composable
fun AhuChipRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
