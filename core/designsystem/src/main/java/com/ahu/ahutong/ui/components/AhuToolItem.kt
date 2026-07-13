package com.ahu.ahutong.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Tools-grid tile (icon + label) used on the Tools page / home widget library.
 */
@Composable
fun AhuToolItem(
    title: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    iconResId: Int? = null,
) {
    require(icon != null || iconPainter != null || iconResId != null) {
        "AhuToolItem requires icon, iconPainter, or iconResId"
    }

    Column(
        modifier = modifier
            .width(88.dp)
            .clip(SmoothRoundedCornerShape(AhuDimens.CardCornerMedium))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            icon != null -> Icon(
                imageVector = icon,
                modifier = Modifier.size(40.dp),
                contentDescription = null,
                tint = tint,
            )
            iconPainter != null -> Icon(
                painter = iconPainter,
                modifier = Modifier.size(40.dp),
                contentDescription = null,
                tint = tint,
            )
            iconResId != null -> Icon(
                painter = painterResource(id = iconResId),
                modifier = Modifier.size(40.dp),
                contentDescription = null,
                tint = tint,
            )
        }
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
