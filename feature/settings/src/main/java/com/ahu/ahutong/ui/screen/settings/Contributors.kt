package com.ahu.ahutong.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.haptics.hapticClickable as clickable
import com.ahu.ahutong.ui.state.ActiveAuthor
import com.ahu.ahutong.ui.state.CommitRecord
import com.ahu.ahutong.ui.state.DeveloperViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Contributors(
    title: String,
    onBack: () -> Unit,
    developerViewModel: DeveloperViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val commitState by developerViewModel.commits.collectAsState()
    var seniorsExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedSenior by remember { mutableStateOf<DeveloperViewModel.Senior?>(null) }
    var expandedAuthors by remember { mutableStateOf<Set<String>>(emptySet()) }

    SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(
            title = title,
            onBack = onBack,
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            bottomPadding = 48.dp
        ) {
            SettingsSection(
                title = "站在前辈的肩膀上",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                if (!seniorsExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { seniorsExpanded = true }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        developerViewModel.seniors.forEachIndexed { index, senior ->
                            AsyncImage(
                                model = senior.avatar,
                                contentDescription = senior.name,
                                modifier = Modifier
                                    .offset(x = (-14 * index).dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        CircleShape
                                    )
                            )
                        }
                        Text(
                            text = "${developerViewModel.seniors.size} 位前辈",
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            developerViewModel.seniors.forEach { senior ->
                                Column(
                                    modifier = Modifier
                                        .width(88.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { selectedSenior = senior }
                                        .padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AsyncImage(
                                        model = senior.avatar,
                                        contentDescription = senior.name,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                    )
                                    Text(
                                        text = senior.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "收起",
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { seniorsExpanded = false }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            SettingsSection(
                title = "活跃的开发者",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                when (val state = commitState) {
                    DeveloperViewModel.CommitState.Loading -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "正在获取提交记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DeveloperViewModel.CommitState.Failed -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { developerViewModel.refreshCommits() }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "提交记录加载失败，点击重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is DeveloperViewModel.CommitState.Ready -> {
                        if (state.authors.isEmpty()) {
                            Text(
                                text = "暂无提交记录",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        state.authors.forEachIndexed { index, author ->
                            ActiveAuthorRow(
                                author = author,
                                name = developerViewModel.displayName(author.login),
                                summary = developerViewModel.summary(author),
                                expanded = author.login in expandedAuthors,
                                onToggle = {
                                    expandedAuthors = if (author.login in expandedAuthors) {
                                        expandedAuthors - author.login
                                    } else {
                                        expandedAuthors + author.login
                                    }
                                },
                                onOpenCommit = { openUrl(context, it.url) }
                            )
                            if (index != state.authors.lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSenior?.let { senior ->
        AppDialog(
            title = senior.name,
            onDismiss = { selectedSenior = null },
            actions = listOf(
                AppDialogAction(
                    label = "联系我",
                    onClick = {
                        senior.openQq(context)
                        selectedSenior = null
                    },
                    style = AppDialogActionStyle.Primary
                )
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = senior.avatar,
                    contentDescription = senior.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Text(
                    text = senior.desc,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ActiveAuthorRow(
    author: ActiveAuthor,
    name: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenCommit: (CommitRecord) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = "https://github.com/${author.login}.png?size=200",
                contentDescription = author.login,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded) {
            author.commits.forEach { commit ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCommit(commit) }
                        .padding(start = 78.dp, end = 20.dp, top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = commit.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${commit.shortSha} ${commit.dateCst.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
