package com.ahu.ahutong.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.core.designsystem.R as DesignSystemR
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppPageScaffold
import com.ahu.ahutong.ui.components.AppSectionCard
import com.ahu.ahutong.ui.components.AppTitleIconButton
import com.ahu.ahutong.ui.state.ScheduleViewModel

@Composable
fun ProfileScreen(
    scheduleViewModel: ScheduleViewModel,
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val scheduleConfig by scheduleViewModel.scheduleConfig.observeAsState()

    val user = SessionStore.currentUser()
    val name = user?.name?.takeIf(String::isNotBlank) ?: "未登录"
    val studentId = user?.xh?.takeIf(String::isNotBlank)
    val isGraduate = user?.academicAccountType == AcademicAccountType.POSTGRADUATE
    val accountType = if (isGraduate) "研究生账号" else "本科生账号"
    val weekLabel = when {
        scheduleConfig?.isInSemester == true &&
            (scheduleConfig?.week ?: 0) > 0 -> "第${scheduleConfig?.week}周"
        scheduleConfig != null -> "假期中"
        else -> "未确认"
    }

    AppPageScaffold(
        title = "个人信息",
        onBack = onBack,
        trailingContent = {
            AppTitleIconButton(
                onClick = viewModel::refresh,
                contentDescription = "刷新个人信息",
                icon = DesignSystemR.drawable.ic_refresh,
                loading = refreshing
            )
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = {
            AppSectionCard(contentPadding = PaddingValues(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = listOfNotNull(
                                studentId?.let { "学号 $it" },
                                accountType
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            AppSectionCard {
                ProfileSectionTitle("基础信息")
                ProfileInfoRow("姓名", name)
                ProfileInfoRow("学号", studentId ?: "未获取")
                ProfileInfoRow("账号类型", accountType)
            }

            AppSectionCard {
                ProfileSectionTitle("培养信息")
                when {
                    refreshing && profiles.isEmpty() -> Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppCircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        Text(
                            "正在读取学院与专业…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    profiles.isEmpty() -> Text(
                        text = "暂未获取到学院和专业信息。可以点右上角刷新，或先在成绩页加载一次。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> profiles.forEachIndexed { index, profile ->
                        if (index > 0) {
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text = profile.trainingType.ifBlank { "主修" },
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        ProfileInfoRow("学院", profile.department.ifBlank { "未获取" })
                        ProfileInfoRow("专业", profile.major.ifBlank { "未获取" })
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (!isGraduate) {
                AppSectionCard {
                    ProfileSectionTitle("学期")
                    ProfileInfoRow("学年", "${scheduleViewModel.schoolYear} 学年")
                    ProfileInfoRow("学期", "第${scheduleViewModel.schoolTerm}学期")
                    ProfileInfoRow("当前教学周", weekLabel)
                }
            }

            Text(
                text = "姓名、学号来自登录会话；学院、专业来自教务成绩档案；学期与周次来自课表设置。以上信息仅在本机展示。",
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}

@Composable
private fun ProfileSectionTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            modifier = Modifier.weight(2f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
