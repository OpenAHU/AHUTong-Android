package com.ahu.ahutong.ui.screen.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.data.session.SavedAccount
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle

@Composable
fun SavedAccountsDialog(
    accounts: List<SavedAccount>,
    currentUserId: String?,
    onSelect: (String) -> Unit,
    onForget: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        title = "切换账号",
        onDismiss = onDismiss,
        contentScrollable = true,
        actions = listOf(
            AppDialogAction("取消", onDismiss),
            AppDialogAction("添加账号", onAdd, AppDialogActionStyle.Primary)
        ),
        content = {
            if (accounts.isEmpty()) {
                Text("暂无已保存账号，添加并登录后即可快速切换。")
            }
            accounts.forEach { account ->
                val isCurrent = account.userId == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                            .clickable(enabled = !isCurrent) { onSelect(account.userId) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            account.name.ifBlank { account.userId },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            account.userId + if (isCurrent) " · 当前账号" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { onForget(account.userId) }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "移除已保存账号 ${account.userId}"
                            )
                        }
                    }
                }
            }
        }
    )
}
