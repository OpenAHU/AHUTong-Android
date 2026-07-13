package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.capsule.ContinuousCapsule

/**
 * Horizontal capsule filter strip (campus / type chips on LostFound, etc.).
 *
 * Prefer over hand-rolled `clip(ContinuousCapsule).background(card).padding(8.dp)`.
 */
@Composable
fun AhuFilterBar(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .padding(horizontal = AhuDimens.ContentHorizontal)
            .clip(ContinuousCapsule)
            .background(AhuColors.card)
            .then(
                if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * Three-equal-width segmented control for binary/ternary modes
 * (e.g. 失物招领 / 寻物启事).
 */
@Composable
fun AhuSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val alignment = when (index) {
                0 -> Alignment.CenterStart
                options.lastIndex -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = alignment,
            ) {
                AhuChip(
                    text = label,
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}
