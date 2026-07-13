package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Section label above a card group ("账户信息", "关于"…).
 */
@Composable
fun AhuSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = AhuDimens.TitleHorizontal),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * Grouped list container used by Settings-style pages.
 *
 * Children are typically [AhuListItem]s with a 2.dp gap between rows.
 */
@Composable
fun AhuListGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = AhuDimens.ContentHorizontal)
            .clip(SmoothRoundedCornerShape(AhuDimens.CardCorner)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/**
 * Single tappable row inside [AhuListGroup] (settings entry).
 */
@Composable
fun AhuListItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(AhuDimens.ListItemCorner))
            .background(AhuColors.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
            )
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        trailing?.invoke()
    }
}
