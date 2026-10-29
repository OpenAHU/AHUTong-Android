package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import kotlinx.coroutines.launch
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCircularProgressIndicator
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppFilterChip
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import com.ahu.ahutong.ui.components.AppSelectField
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.AppToggle
import com.ahu.ahutong.ui.components.SettingsChoice
import com.ahu.ahutong.ui.components.SettingsActionRow
import com.ahu.ahutong.ui.components.SettingsDialogSelectRow
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.components.SettingsSelectRow
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.pack.ComponentSlotId
import com.ahu.ahutong.ui.theme.pack.SlotSource

/**
 * 主题实验室（Theme Park 控制台）：
 * 套装预设 + 13 个组件槽位逐一混搭 + 实时预览。
 * 预览区全部走 App* 组件——改槽位即整树重组，所见即全局所得。
 */
@Composable
fun ThemeLab(
    onBack: () -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val appUiTheme by viewModel.appUiTheme.collectAsState()
    val slotOverrides by viewModel.componentSlotOverrides.collectAsState()
    var dialogPreviewShown by remember { mutableStateOf(false) }
    var sheetPreviewShown by remember { mutableStateOf(false) }
    var previewToggle by remember { mutableStateOf(true) }
    var previewChip by remember { mutableStateOf(true) }
    var previewText by remember { mutableStateOf("") }
    var previewSelection by remember { mutableStateOf("选项一") }

    com.ahu.ahutong.ui.components.SettingsBackdropContainer(modifier = Modifier.fillMaxSize()) { backdrop ->
    SettingsPageLayout(
        title = "详细设定",
        onBack = onBack
    ) {
        SettingsSection(
            title = "实时预览（当前混搭效果）",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppButton(
                        onClick = {},
                        variant = AppButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    ) { Text("主要") }
                    AppButton(
                        onClick = {},
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("次要") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppToggle(
                        checked = previewToggle,
                        onCheckedChange = { previewToggle = it },
                        contentDescription = "预览开关"
                    )
                    AppFilterChip(
                        selected = previewChip,
                        onClick = { previewChip = !previewChip },
                        label = { Text("筛选片") }
                    )
                    AppCircularProgressIndicator()
                }
                AppTextField(
                    value = previewText,
                    onValueChange = { previewText = it },
                    label = "文本框",
                    modifier = Modifier.fillMaxWidth()
                )
                AppSelectField(
                    label = "下拉选择器",
                    selected = previewSelection,
                    options = listOf("选项一", "选项二", "选项三").map { AppSelectOption(it, it) },
                    onSelected = { previewSelection = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppButton(
                        onClick = { dialogPreviewShown = true },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("预览弹窗") }
                    AppButton(
                        onClick = { sheetPreviewShown = true },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("预览抽屉") }
                }
            }
        }

        SettingsSection(
            title = "组件槽位（逐件指定实现来源）",
            modifier = Modifier.padding(horizontal = 16.dp),
            backdrop = backdrop
        ) {
            ComponentSlotId.entries.forEachIndexed { index, slot ->
                SettingsDialogSelectRow(
                    title = slot.displayName,
                    selected = slotOverrides[slot],
                    choices = listOf(
                        SettingsChoice<SlotSource?>(null, "跟随套装（${appUiTheme.displayName}）")
                    ) + SlotSource.entries.map { SettingsChoice<SlotSource?>(it, it.displayName) },
                    onSelected = { source -> viewModel.setComponentSlotOverride(slot, source) },
                    dialogTitle = "${slot.displayName} · 实现来源",
                    showDivider = index < ComponentSlotId.entries.lastIndex
                )
            }
        }

        if (slotOverrides.isNotEmpty()) {
            AppButton(
                onClick = viewModel::clearComponentSlotOverrides,
                variant = AppButtonVariant.Destructive,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) { Text("一键还原为整套（${appUiTheme.displayName}）") }
        }
    }

    if (dialogPreviewShown) {
        AppDialog(
            title = "弹窗预览",
            onDismiss = { dialogPreviewShown = false },
            actions = listOf(
                AppDialogAction("取消", onClick = { dialogPreviewShown = false }),
                AppDialogAction(
                    "确定",
                    onClick = { dialogPreviewShown = false },
                    style = AppDialogActionStyle.Primary
                )
            ),
            content = { Text("这是当前混搭下的弹窗样式。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
    }

    if (sheetPreviewShown) {
        AppModalBottomSheet(
            title = "底部抽屉预览",
            onDismissRequest = { sheetPreviewShown = false }
        ) {
            Text(
                text = "这是当前混搭下的底部抽屉样式。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
    }

}

@Composable
fun ThemeSettingsSection(
    viewModel: PreferencesViewModel,
    backdrop: com.kyant.backdrop.Backdrop,
    onOpenDetails: () -> Unit,
    onOpenThemeColors: () -> Unit,
    onHorizontalDragActiveChange: (Boolean) -> Unit = {}
) {
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val appUiTheme by viewModel.appUiTheme.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val materialFloatingBottomBar by viewModel.materialFloatingBottomBar.collectAsState()

    SettingsSection(
        title = "主题",
        modifier = Modifier.padding(horizontal = 16.dp),
        backdrop = backdrop
    ) {
        SettingsSelectRow(
            title = "主题套装",
            selected = appUiTheme,
            choices = AppUiTheme.entries.filter { it != AppUiTheme.LIQUID_GLASS }
                .map { SettingsChoice(it, it.displayName) },
            onSelected = viewModel::setAppUiTheme
        )
        if (appUiTheme == AppUiTheme.MATERIAL) {
            com.ahu.ahutong.ui.components.SettingsToggleRow(
                title = "悬浮底栏",
                selected = materialFloatingBottomBar,
                onSelectedChange = viewModel::setMaterialFloatingBottomBar,
                backdrop = backdrop,
                onHorizontalDragActiveChange = onHorizontalDragActiveChange
            )
        }
        SettingsSelectRow(
            title = "深色模式",
            selected = appThemeMode,
            choices = listOf(
                SettingsChoice(AppThemeMode.FOLLOW_SYSTEM, "跟随系统"),
                SettingsChoice(AppThemeMode.DARK, "深色"),
                SettingsChoice(AppThemeMode.LIGHT, "浅色")
            ),
            onSelected = viewModel::setAppThemeMode
        )
        SettingsActionRow(
            title = "主题色",
            value = themeColorName(themeColor),
            onClick = onOpenThemeColors
        )
        BackgroundControls(viewModel, backdrop, onHorizontalDragActiveChange)
        SettingsActionRow(title = "详细设定", onClick = onOpenDetails, showDivider = false)
    }
}

/** 全局背景：沿用原主页背景的存储与遮罩设置。 */
@Composable
private fun BackgroundControls(
    viewModel: PreferencesViewModel,
    backdrop: com.kyant.backdrop.Backdrop,
    onHorizontalDragActiveChange: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val revision by com.ahu.ahutong.core.storage.HomeBackgroundStore.revision.collectAsState()
    var blur by remember(revision) {
        mutableFloatStateOf(com.ahu.ahutong.core.storage.HomeBackgroundStore.maskPercent.toFloat())
    }
    val enabled = com.ahu.ahutong.core.storage.HomeBackgroundStore.isEnabled

    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                com.ahu.ahutong.core.storage.HomeBackgroundStore.importFromUri(context, uri)
            }
        }
    }

    // 性能优先：背景导入后玻璃效果已自动降级，弹窗告知一次（用户可在下方开关手动恢复）
    var showAutoGlassNotice by remember(revision) {
        androidx.compose.runtime.mutableStateOf(
            com.ahu.ahutong.core.storage.HomeBackgroundStore.consumeAutoGlassNotice()
        )
    }

    SettingsActionRow(
        title = if (enabled) "更改图片" else "选择背景图片",
        onClick = {
            pickImage.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    )
    if (enabled) {
        SettingsActionRow(
            title = "清除背景图片",
            destructive = true,
            onClick = {
                scope.launch {
                    com.ahu.ahutong.core.storage.HomeBackgroundStore.clear(context)
                }
            }
        )
    }
    if (enabled) {
        com.ahu.ahutong.ui.components.SettingsSliderRow(
            title = "遮罩",
            value = blur,
            valueLabel = "${blur.toInt()}%",
            onValueChange = { blur = it },
            onValueChangeFinished = {
                com.ahu.ahutong.core.storage.HomeBackgroundStore.updateMask(blur.toInt())
            },
            valueRange = 0f..100f,
            backdrop = backdrop
        )
        com.ahu.ahutong.ui.components.SettingsToggleRow(
            title = "关闭部分玻璃效果",
            selected = com.ahu.ahutong.core.storage.HomeBackgroundStore.reduceGlassEffects,
            onSelectedChange = com.ahu.ahutong.core.storage.HomeBackgroundStore::setReduceGlassEffects,
            backdrop = backdrop,
            onHorizontalDragActiveChange = onHorizontalDragActiveChange
        )
        SettingsActionRow(
            title = "从背景图取主题色",
            onClick = {
                scope.launch {
                    com.ahu.ahutong.core.storage.HomeBackgroundStore
                        .dominantColorHex(context)?.let { viewModel.setThemeColor(it) }
                }
            }
        )
    }

    if (showAutoGlassNotice) {
        AppDialog(
            title = "已关闭部分玻璃效果",
            onDismiss = { showAutoGlassNotice = false },
            actions = listOf(
                AppDialogAction("知道了", onClick = { showAutoGlassNotice = false },
                    style = AppDialogActionStyle.Primary)
            )
        )
    }
}
