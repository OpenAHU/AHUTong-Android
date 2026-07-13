package com.ahu.ahutong.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ahu.ahutong.data.repository.DownloadedFile
import com.ahu.ahutong.feature.repository.R
import com.ahu.ahutong.ui.components.AhuCard
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuEmptyState
import com.ahu.ahutong.ui.components.AhuHeaderIconButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.RepositoryViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens

@Composable
fun RepositoryDownloads(
    navController: NavHostController
) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val viewModel: RepositoryViewModel = viewModel(viewModelStoreOwner = activity)
    var files by remember { mutableStateOf(viewModel.getDownloadedFiles()) }
    var deleteConfirmPath by remember { mutableStateOf<String?>(null) }
    var batchDeleteTargets by remember { mutableStateOf<List<String>?>(null) }
    var isManaging by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }

    fun refreshFiles() {
        files = viewModel.getDownloadedFiles()
    }

    LaunchedEffect(Unit) { refreshFiles() }

    AhuScreen(
        scrollable = false,
        verticalArrangement = Arrangement.Top,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AhuHeaderIconButton(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(id = R.string.back),
                onClick = { navController.popBackStack() },
            )
            Text(
                text = if (isManaging) {
                    stringResource(id = R.string.selected_count, selectedPaths.size)
                } else {
                    stringResource(id = R.string.downloaded_files)
                },
                style = MaterialTheme.typography.titleLarge,
                color = AhuColors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (files.isNotEmpty()) {
                TextButton(onClick = {
                    isManaging = !isManaging
                    if (!isManaging) selectedPaths = emptySet()
                }) {
                    Text(
                        if (isManaging) stringResource(id = R.string.done)
                        else stringResource(id = R.string.manage)
                    )
                }
            }
        }

        if (files.isEmpty()) {
            AhuEmptyState(
                text = stringResource(id = R.string.no_downloaded_files),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        onDelete = { deleteConfirmPath = file.path }
                    )
                }
            }

            if (isManaging && files.isNotEmpty()) {
                AhuCard(
                    modifier = Modifier.padding(12.dp),
                    cornerRadius = AhuDimens.CardCornerMedium,
                    containerColor = AhuColors.cardStrong,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            selectedPaths = if (selectedPaths.size == files.size) emptySet()
                            else files.map { it.path }.toSet()
                        }) {
                            Text(
                                if (selectedPaths.size == files.size) {
                                    stringResource(id = R.string.deselect_all)
                                } else {
                                    stringResource(id = R.string.select_all)
                                }
                            )
                        }
                        TextButton(
                            onClick = {
                                if (selectedPaths.isNotEmpty()) {
                                    batchDeleteTargets = selectedPaths.toList()
                                }
                            },
                            enabled = selectedPaths.isNotEmpty()
                        ) {
                            Text(
                                stringResource(id = R.string.delete_selected, selectedPaths.size),
                                color = if (selectedPaths.isNotEmpty()) Color(0xFFFF5252)
                                else AhuColors.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    deleteConfirmPath?.let { path ->
        ConfirmDialog(
            title = stringResource(id = R.string.confirm_delete_title),
            message = stringResource(id = R.string.confirm_delete_message),
            onCancel = { deleteConfirmPath = null },
            onConfirm = {
                viewModel.deleteFile(path)
                refreshFiles()
                selectedPaths = selectedPaths - path
                deleteConfirmPath = null
            }
        )
    }

    batchDeleteTargets?.let { targets ->
        ConfirmDialog(
            title = stringResource(id = R.string.batch_delete_title),
            message = stringResource(id = R.string.batch_delete_message, targets.size),
            onCancel = { batchDeleteTargets = null },
            onConfirm = {
                targets.forEach { viewModel.deleteFile(it) }
                refreshFiles()
                selectedPaths = emptySet()
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
    AhuDialog(
        onDismissRequest = onCancel,
        scrollable = false,
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.titleMedium,
            color = AhuColors.onSurface,
        )
        Text(
            message,
            modifier = Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AhuColors.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(id = R.string.cancel),
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = AhuColors.onSurface,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(id = R.string.delete),
                modifier = Modifier
                    .clickable { onConfirm() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFFF5252)
            )
        }
    }
}

@Composable
private fun DownloadedFileRow(
    file: DownloadedFile,
    isManaging: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val typeLabel = RepositoryViewModel.getFileTypeIcon(file.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(SmoothRoundedCornerShape(AhuDimens.CardCornerMedium))
            .background(AhuColors.cardStrong)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isManaging) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (isSelected) {
                    stringResource(id = R.string.selected)
                } else {
                    stringResource(id = R.string.not_selected)
                },
                tint = if (isSelected) AhuColors.primaryAction else AhuColors.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (typeLabel) {
                        "PDF" -> Color(0xFFE53935)
                        "DOC" -> Color(0xFF1565C0)
                        "PPT" -> Color(0xFFE65100)
                        "XLS" -> Color(0xFF2E7D32)
                        else -> Color(0xFF757575)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = AhuColors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatSize(file.size)} · ${file.path}",
                style = MaterialTheme.typography.bodySmall,
                color = AhuColors.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isManaging) {
            IconButton(onClick = { onClick() }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(id = R.string.open),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.padding(start = 4.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(id = R.string.delete),
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
