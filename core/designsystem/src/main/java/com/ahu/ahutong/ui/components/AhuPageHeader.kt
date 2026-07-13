package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.ahu.ahutong.ui.theme.AhuDimens

/**
 * Page title row used by Tools / Grade / Exam / Settings-style screens.
 *
 * @param title page title shown with [titleStyle]
 * @param actions trailing actions (refresh, search, edit…)
 * @param below optional content under the title row (search field, chips…)
 */
@Composable
fun AhuPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    actions: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AhuDimens.TitlePadding),
        verticalArrangement = Arrangement.spacedBy(AhuDimens.ContentHorizontal),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = titleStyle,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        below()
    }
}
