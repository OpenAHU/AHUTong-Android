package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Standard modal dialog surface used by Settings / payment confirms / schedule detail.
 */
@Composable
fun AhuDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Column(
            modifier = modifier
                .clip(SmoothRoundedCornerShape(AhuDimens.CardCorner))
                .background(AhuColors.pageBackground)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AhuDimens.SectionSpacing),
            content = content,
        )
    }
}
