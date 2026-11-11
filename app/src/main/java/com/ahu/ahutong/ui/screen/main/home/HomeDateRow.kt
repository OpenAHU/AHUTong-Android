package com.ahu.ahutong.ui.screen.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.debug.DebugClock
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HomeDateRow(
    dateText: String? = null,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    // dateText 由主页分钟级 ticker 驱动：常开跨过午夜也能刷新；
    // 缺省回退为组合内现算（首帧 ticker 尚未赋值时不留白）。
    val date = dateText?.takeIf { it.isNotBlank() }
        ?: SimpleDateFormat("MM-dd / EE", Locale.CHINA).format(DebugClock.nowDate())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.bodyMedium,
            color = 45.n1 withNight 75.n1
        )
        trailingContent()
    }
}