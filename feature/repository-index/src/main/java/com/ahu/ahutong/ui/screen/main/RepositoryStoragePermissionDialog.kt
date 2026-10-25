package com.ahu.ahutong.ui.screen.main

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.ahu.ahutong.ui.components.SettingsConfirmationDialog
import com.ahu.ahutong.ui.state.RepositoryViewModel

@Composable
internal fun RepositoryStoragePermissionDialog(viewModel: RepositoryViewModel) {
    val request by viewModel.storagePermissionRequest.collectAsState()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onDownloadDirectorySelected(uri?.toString())
    }
    request?.let { current ->
        SettingsConfirmationDialog(
            title = "授权学习资料目录",
            message = "请在接下来的系统文件选择器中：\n" +
                "1. 打开下载（Download）目录。\n" +
                "2. 进入原来的 ahutong 子文件夹；首次下载可新建 ahutong 文件夹。\n" +
                "3. 点击“使用此文件夹”，再点击“允许”。\n\n" +
                "请选中文件夹内的位置，不要选择 Download 顶层。打开已有文件时，请选择原下载目录。",
            confirmLabel = "选择文件夹",
            onDismiss = viewModel::cancelStoragePermission,
            onConfirm = {
                viewModel.onStoragePickerOpened()
                try {
                    picker.launch(current.initialDirectoryUri?.let(Uri::parse))
                } catch (_: ActivityNotFoundException) {
                    viewModel.cancelStoragePermission()
                    Toast.makeText(context, "未找到系统文件选择器，请启用系统“文件”应用后重试", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
