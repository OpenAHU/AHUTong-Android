package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.ui.theme.AhuColors

/**
 * Project-styled floating action button (LostFound publish "+", etc.).
 */
@Composable
fun AhuFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    containerColor: Color? = null,
    contentColor: Color? = null,
    label: String = "+",
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(size),
        containerColor = containerColor ?: AhuColors.primaryAction,
        contentColor = contentColor ?: AhuColors.onPrimaryAction,
    ) {
        Text(text = label, fontSize = 28.sp)
    }
}

/**
 * Default bottom-end placement padding used with [AhuScreenBox].
 */
fun Modifier.ahuFabPadding(): Modifier = this.padding(24.dp)
