package com.ahu.ahutong.ui.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ahu.ahutong.feature.settings.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.components.SettingsInfoRow
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.state.SettingsViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(
    appName: String,
    appIcon: Painter,
    onBack: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenContributors: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDataCleared: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showUpdateLog by remember { mutableStateOf(false) }
    var updateLog by remember { mutableStateOf("") }
    var showClear by remember { mutableStateOf(false) }

    LaunchedEffect(showUpdateLog) {
        if (showUpdateLog) updateLog = viewModel.changelog()
    }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = "关于与支持",
            onBack = onBack,
            backdrop = backdrop
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = appIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                )
                Text(
                    text = appName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            SettingsSection(
                title = null,
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                SettingsInfoRow(
                    title = "当前版本",
                    value = viewModel.versionName.orEmpty(),
                    showDivider = true
                )
                SettingsActionRow(
                    title = "更新说明",
                    leadingPainter = painterResource(R.drawable.ic_log),
                    onClick = { showUpdateLog = true }
                )
                SettingsActionRow(
                    title = "官网",
                    leadingPainter = painterResource(R.drawable.ic_earth),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://openahu.org"))
                            )
                        }.onFailure {
                            Toast.makeText(context, "无法打开官网", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingsActionRow(
                    title = "反馈",
                    leadingPainter = painterResource(R.drawable.ic_feedback),
                    onClick = onOpenFeedback
                )
                SettingsActionRow(
                    title = "贡献者",
                    leadingPainter = painterResource(R.drawable.ic_peoples),
                    onClick = onOpenContributors
                )
                SettingsActionRow(
                    title = "清除所有数据",
                    leadingPainter = painterResource(R.drawable.ic_delete),
                    destructive = true,
                    showDivider = false,
                    onClick = { showClear = true }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "OpenAHU 团队维护",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "隐私政策",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenPrivacy)
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "开源许可",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenLicense)
                    )
                }
                Text(
                    text = "Copyright © ${LocalDate.now().year} OpenAHU",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showUpdateLog) {
        AppDialog(
            title = "更新说明",
            onDismiss = { showUpdateLog = false },
            contentScrollable = true,
            actions = listOf(
                AppDialogAction(
                    label = "完成",
                    onClick = { showUpdateLog = false },
                    style = AppDialogActionStyle.Primary
                )
            )
        ) {
            Text(
                text = updateLog,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showClear) {
        SettingsConfirmationDialog(
            title = "清除所有数据？",
            message = "已保存账号、登录状态、课表及本机设置将被永久清除。",
            confirmLabel = "清除",
            destructive = true,
            onConfirm = {
                showClear = false
                scope.launch {
                    viewModel.clearAllData()
                    Toast.makeText(context, "已清除所有数据", Toast.LENGTH_SHORT).show()
                    onDataCleared()
                }
            },
            onDismiss = { showClear = false }
        )
    }
}
