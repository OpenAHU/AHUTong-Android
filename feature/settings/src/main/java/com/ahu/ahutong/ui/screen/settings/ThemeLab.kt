package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme
import kotlinx.coroutines.launch
import com.ahu.ahutong.data.model.DEFAULT_THEME_COLOR
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppCard
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
    onOpenDetails: () -> Unit
) {
    val appThemeMode by viewModel.appThemeMode.collectAsState()
    val appUiTheme by viewModel.appUiTheme.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    var showCustomColorDialog by remember { mutableStateOf(false) }

    SettingsSection(
        title = "主题",
        modifier = Modifier.padding(horizontal = 16.dp),
        backdrop = backdrop
    ) {
        ThemeColorPicker(
            selectedColor = themeColor,
            showMiuixDefault = appUiTheme == AppUiTheme.MIUIX,
            onColorSelected = viewModel::setThemeColor,
            onCustomColorClick = { showCustomColorDialog = true }
        )
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
        BackgroundControls(viewModel)
        SettingsSelectRow(
            title = "主题套装",
            selected = appUiTheme,
            choices = AppUiTheme.entries.filter { it != AppUiTheme.LIQUID_GLASS }
                .map { SettingsChoice(it, it.displayName) },
            onSelected = viewModel::setAppUiTheme
        )
        SettingsActionRow(title = "详细设定", onClick = onOpenDetails, showDivider = false)
    }

    if (showCustomColorDialog) {
        CustomThemeColorDialog(
            initialValue = themeColor.orEmpty(),
            onDismiss = { showCustomColorDialog = false },
            onConfirm = { color ->
                viewModel.setThemeColor(color)
                showCustomColorDialog = false
            }
        )
    }
}

private data class ThemeColorChoice(
    val value: String?,
    val name: String,
    val color: Color
)

@Composable
private fun ThemeColorPicker(
    selectedColor: String?,
    showMiuixDefault: Boolean,
    onColorSelected: (String?) -> Unit,
    onCustomColorClick: () -> Unit
) {
    val choices = buildList {
        if (showMiuixDefault) {
            add(ThemeColorChoice(DEFAULT_THEME_COLOR, "默认", Color(0xFF3482FF)))
        }
        add(ThemeColorChoice(null, "系统", MaterialTheme.colorScheme.primary))
        add(ThemeColorChoice("#FF4A90E2", "极光蓝", Color(0xFF4A90E2)))
        add(ThemeColorChoice("#FFE07A9F", "樱花粉", Color(0xFFE07A9F)))
        add(ThemeColorChoice("#FFF4A261", "落日橙", Color(0xFFF4A261)))
        add(ThemeColorChoice("#FF6A994E", "苔藓绿", Color(0xFF6A994E)))
        add(ThemeColorChoice("#FF9B7EDE", "薰衣草", Color(0xFF9B7EDE)))
        add(ThemeColorChoice("#FF2E8B57", "翡翠", Color(0xFF2E8B57)))
    }
    val presetValues = choices.map { it.value }.toSet()
    val customSelected = selectedColor != null &&
        selectedColor != DEFAULT_THEME_COLOR &&
        selectedColor !in presetValues

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "主题色",
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            ThemeColorSwatch(
                name = "自定义",
                color = runCatching {
                    Color(android.graphics.Color.parseColor(selectedColor))
                }.getOrDefault(MaterialTheme.colorScheme.surfaceContainerHighest),
                selected = customSelected,
                custom = true,
                onClick = onCustomColorClick
            )
            choices.forEach { choice ->
                ThemeColorSwatch(
                    name = choice.name,
                    color = choice.color,
                    selected = selectedColor == choice.value,
                    onClick = { onColorSelected(choice.value) }
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    custom: Boolean = false
) {
    Column(
        modifier = Modifier
            .clip(SmoothRoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SmoothRoundedCornerShape(16.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            when {
                selected -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已选择",
                    tint = Color.White
                )
                custom -> Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "自定义主题色",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = name,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomThemeColorDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    val valid = remember(value) {
        runCatching { android.graphics.Color.parseColor(value) }.isSuccess
    }
    AppDialog(
        title = "自定义主题色",
        onDismiss = onDismiss,
        actions = listOf(
            AppDialogAction("取消", onClick = onDismiss),
            AppDialogAction(
                "应用",
                enabled = valid,
                style = AppDialogActionStyle.Primary,
                onClick = { onConfirm(value) }
            )
        ),
        content = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("ARGB Hex") },
                placeholder = { Text("#FF007FAC") },
                isError = value.isNotBlank() && !valid,
                supportingText = {
                    if (value.isNotBlank() && !valid) Text("请输入有效的颜色代码")
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    errorContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

/** 全局背景：沿用原主页背景的存储与遮罩设置。 */
@Composable
private fun BackgroundControls(viewModel: PreferencesViewModel) {
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

    Text(
        text = "背景",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)
    )
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppButton(
                    onClick = {
                        pickImage.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    variant = AppButtonVariant.Primary,
                    modifier = Modifier.weight(1f)
                ) { Text(if (enabled) "更换图片" else "选择图片") }
                if (enabled) {
                    AppButton(
                        onClick = {
                            scope.launch {
                                com.ahu.ahutong.core.storage.HomeBackgroundStore.clear(context)
                            }
                        },
                        variant = AppButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    ) { Text("清除") }
                }
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "遮罩",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(48.dp)
                    )
                    androidx.compose.material3.Slider(
                        value = blur,
                        onValueChange = { blur = it },
                        onValueChangeFinished = {
                            com.ahu.ahutong.core.storage.HomeBackgroundStore
                                .updateMask(blur.toInt())
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${blur.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(28.dp)
                    )
                }
                AppButton(
                    onClick = {
                        com.ahu.ahutong.core.storage.HomeBackgroundStore
                            .dominantColorHex(context)?.let { viewModel.setThemeColor(it) }
                    },
                    variant = AppButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("从背景图取主题色") }
            }
        }
}
