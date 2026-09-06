package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.data.repository.RepositoryManager
import com.ahu.ahutong.ui.components.appLiquidGlassSceneBackground
import com.ahu.ahutong.ui.components.appLiquidGlassSurface
import com.ahu.ahutong.ui.components.AppPageHeader
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.RepositoryViewModel
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.monet.a1
import com.kyant.monet.n1
import com.kyant.monet.withNight

@Composable
fun RepositoryDownloads(
    navController: NavHostController
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val viewModel: RepositoryViewModel = viewModel(viewModelStoreOwner = activity)
    val markdownState by viewModel.markdownState.collectAsState()
    var files by remember { mutableStateOf(viewModel.getDownloadedFiles()) }
    var deleteConfirmPath by remember { mutableStateOf<String?>(null) }
    var batchDeleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var isManaging by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var isDeleting by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }
    val secondaryTextColor = if (isDark) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    fun refreshFiles() {
        files = viewModel.getDownloadedFiles()
    }

    LaunchedEffect(Unit) { refreshFiles() }

    fun deleteFiles(paths: Collection<String>) {
        if (isDeleting) return
        isDeleting = true
        deletionError = null
        viewModel.deleteFiles(paths) { result ->
            result.onSuccess { deletion ->
                files = deletion.remainingFiles
                selectedPaths = selectedPaths.intersect(files.mapTo(mutableSetOf()) { it.path })
                deletionError = deletion.failedPaths.takeIf { it.isNotEmpty() }?.let {
                    "${it.size} 个文件未能删除，请重试"
                }
            }.onFailure {
                deletionError = "无法更新下载列表：${it.message ?: "请稍后重试"}"
            }
            isDeleting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .appLiquidGlassSceneBackground(96.n1 withNight 10.n1)
    ) {
        AppPageHeader(
            title = if (isManaging) "已选择 ${selectedPaths.size} 项" else "已下载文件",
            onBack = { navController.popBackStack() },
            actions = {
                if (files.isNotEmpty()) {
                    TextButton(onClick = {
                        isManaging = !isManaging
                        if (!isManaging) selectedPaths = emptySet()
                    }) {
                        Text(if (isManaging) "完成" else "管理")
                    }
                }
            }
        )

        if (isDeleting || deletionError != null) {
            Text(
                text = deletionError ?: "正在删除…",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                color = if (deletionError != null) MaterialTheme.colorScheme.error else secondaryTextColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无下载文件", style = MaterialTheme.typography.bodyLarge,
                        color = secondaryTextColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("浏览学习资料时可下载文件", style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(files, key = { it.path }) { file ->
                    val isSelected = file.path in selectedPaths
                    DownloadedFileRow(
                        file = file,
                        isManaging = isManaging,
                        isSelected = isSelected,
                        onClick = {
                            if (isManaging) {
                                selectedPaths = if (isSelected) selectedPaths - file.path
                                else selectedPaths + file.path
                            } else {
                                viewModel.openDownloadedFile(file)
                            }
                        },
                        onDelete = { if (!isDeleting) deleteConfirmPath = file.path }
                    )
                }
            }

            // 管理模式底部栏
            if (isManaging && files.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .appLiquidGlassSurface(
                            shape = SmoothRoundedCornerShape(16.dp),
                            fallbackColor = 100.n1 withNight 30.n1,
                            level = LiquidGlassSurfaceLevel.Floating
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        selectedPaths = if (selectedPaths.size == files.size) emptySet()
                        else files.map { it.path }.toSet()
                    }) {
                        Text(if (selectedPaths.size == files.size) "取消全选" else "全选")
                    }
                    TextButton(
                        onClick = {
                            if (selectedPaths.isNotEmpty()) {
                                batchDeleteTargets = selectedPaths.toList()
                            }
                        },
                        enabled = selectedPaths.isNotEmpty() && !isDeleting
                    ) {
                        Text(
                            "删除选中 (${selectedPaths.size})",
                            color = if (selectedPaths.isNotEmpty()) MaterialTheme.colorScheme.error
                                    else secondaryTextColor
                        )
                    }
                }
            }
        }
    }

    RepositoryMarkdownReader(
        markdownState = markdownState,
        onDismiss = { viewModel.clearMarkdown() }
    )

    // 单个删除确认
    deleteConfirmPath?.let { path ->
        ConfirmDialog(
            title = "确认删除",
            message = "确定要删除此文件吗？",
            onCancel = { deleteConfirmPath = null },
            onConfirm = {
                deleteFiles(listOf(path))
                deleteConfirmPath = null
            }
        )
    }

    // 批量删除确认
    batchDeleteTargets?.let { targets ->
        ConfirmDialog(
            title = "批量删除",
            message = "确定要删除选中的 ${targets.size} 个文件吗？",
            onCancel = { batchDeleteTargets = null },
            onConfirm = {
                deleteFiles(targets)
                batchDeleteTargets = null
            }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    SettingsConfirmationDialog(
        title = title,
        message = message,
        confirmLabel = "删除",
        onConfirm = onConfirm,
        onDismiss = onCancel,
        destructive = true
    )
}

@Composable
private fun DownloadedFileRow(
    file: DownloadedFile,
    isManaging: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val typeLabel = RepositoryViewModel.getFileTypeIcon(file.name)
    val fileAccentColor = when (typeLabel) {
        "PDF" -> Color(0xFFE53935)
        "DOC" -> Color(0xFF1565C0)
        "PPT" -> Color(0xFFE65100)
        "XLS" -> Color(0xFF2E7D32)
        else -> Color(0xFF757575)
    }
    val fileBadgeColor = if (isDark) {
        fileAccentColor.copy(alpha = 0.88f)
    } else {
        fileAccentColor.copy(alpha = 0.14f)
    }
    val fileBadgeTextColor = if (isDark) Color.White else fileAccentColor
    val secondaryTextColor = if (isDark) {
        Color.White.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val uncheckedCheckboxColor = if (isDark) {
        Color.White.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 管理模式：复选框
        if (isManaging) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) "已选择" else "未选择",
                tint = if (isSelected) 90.a1 withNight 90.a1 else uncheckedCheckboxColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 类型标签
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(fileBadgeColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = fileBadgeTextColor,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatSize(file.size)} · ${RepositoryManager.formatDisplayPath(file.path)}",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isManaging) {
            IconButton(onClick = { onClick() }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "打开",
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
