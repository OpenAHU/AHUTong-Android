package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Standard modal bottom sheet with project surface colors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AhuBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AhuColors.pageBackground,
        contentColor = AhuColors.onSurface,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AhuColors.onSurface,
                )
            }
            content()
        }
    }
}
