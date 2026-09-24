package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.monet.n1
import com.kyant.monet.withNight
import java.util.Calendar

@Composable
fun WorkDetailDialog(
    work: Work,
    onDismiss: () -> Unit,
    onToggleDone: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onViewQuestions: (() -> Unit)? = null
) {
    val isCustom = work.workId.startsWith("event_")

    AppDialog(
        title = work.title,
        onDismiss = onDismiss,
        subtitle = if (isCustom) "自定义日程" else work.courseName,
        titleFontWeight = null,
        titleMaxLines = 2,
        contentSpacing = 16.dp,
        actions = if (isCustom) {
            buildList {
                onToggleDone?.let {
                    add(
                        AppDialogAction(
                            label = if (work.isDone) "恢复未完成" else "标记完成",
                            onClick = it,
                            style = if (work.isDone) AppDialogActionStyle.Neutral
                            else AppDialogActionStyle.Primary
                        )
                    )
                }
                onDelete?.let {
                    add(
                        AppDialogAction(
                            label = "删除该日程",
                            onClick = it,
                            style = AppDialogActionStyle.Danger
                        )
                    )
                }
            }
        } else {
            buildList {
                // 非自定义日程且有详情页链接：提供「查看题目」（WebView 只读）
                onViewQuestions?.let {
                    add(
                        AppDialogAction(
                            label = "查看题目",
                            onClick = it,
                            style = AppDialogActionStyle.Primary
                        )
                    )
                }
            }
        }
    ) {
        // 状态
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (work.isDone) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (work.isDone) "已完成" else "未完成",
                fontSize = 14.sp,
                color = if (work.isDone) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }

        // 开始时间
        work.startTs?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "开始时间",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatFullTime(it),
                    fontSize = 14.sp
                )
            }
        }

        // 截止时间
        work.endTs?.let {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "截止时间",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatFullTime(it),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatFullTime(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 $hh:$mm"
}