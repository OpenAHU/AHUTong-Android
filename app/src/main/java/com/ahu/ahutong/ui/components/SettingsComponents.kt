package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.kyant.backdrop.Backdrop

data class SettingsChoice<T>(
    val value: T,
    val label: String
)

@Composable
fun SettingsDialogSurface(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = SmoothRoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SettingsConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    SettingsDialogSurface(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            confirmLabel,
                            color = if (destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun settingsGroupColor(): Color = if (LocalIsLiquidGlassEnabled.current) {
    MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
} else {
    MaterialTheme.colorScheme.surfaceContainer
}

@Composable
fun SettingsBackdropContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Backdrop) -> Unit
) {
    GlassBackdropContainer(modifier = modifier, content = content)
}

@Composable
fun SettingsPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = null
) {
    val isLiquid = LocalIsLiquidGlassEnabled.current
    val backShape = SmoothRoundedCornerShape(24.dp)
    val glassTint = liquidGlassTint()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onBack?.let {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(
                        if (isLiquid && backdrop != null) {
                            Modifier.liquidGlassSurface(
                                backdrop = backdrop,
                                shape = backShape,
                                surfaceColor = glassTint
                            )
                        } else {
                            Modifier
                                .clip(backShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    )
                    .clickable(onClick = it),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = if (onBack == null) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SettingsHeroCard(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isLiquid = LocalIsLiquidGlassEnabled.current
    val shape = SmoothRoundedCornerShape(28.dp)
    val glassTint = liquidGlassTint()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isLiquid) {
                    Modifier.liquidGlassSurface(backdrop, shape, glassTint)
                } else {
                    Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLiquid = LocalIsLiquidGlassEnabled.current
    val shape = SmoothRoundedCornerShape(if (isLiquid) 26.dp else 24.dp)
    val glassTint = liquidGlassTint()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 20.dp),
            color = if (isLiquid) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLiquid && backdrop != null) {
                        Modifier.liquidGlassSurface(backdrop, shape, glassTint)
                    } else {
                        Modifier
                            .clip(shape)
                            .background(settingsGroupColor())
                    }
                ),
            content = content
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    value: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = true,
    showDivider: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 68.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingPainter != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = leadingPainter,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else leadingIcon?.let {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            SettingsRowText(
                title = title,
                subtitle = subtitle,
                destructive = destructive,
                modifier = Modifier.weight(1f)
            )
            value?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
        SettingsDivider(visible = showDivider, leadingInset = if (leadingIcon == null && leadingPainter == null) 20.dp else 74.dp)
    }
}

@Composable
fun SettingsInfoRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    showDivider: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowText(
                title = title,
                subtitle = subtitle,
                modifier = Modifier.weight(1f)
            )
            value?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        SettingsDivider(visible = showDivider)
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
    onHorizontalDragActiveChange: (Boolean) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onSelectedChange
                )
                .heightIn(min = 72.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsRowText(
                title = title,
                subtitle = subtitle,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            LiquidToggle(
                selected = { selected },
                onSelect = onSelectedChange,
                backdrop = backdrop,
                userInputEnabled = enabled,
                toggleOnTap = false,
                onHorizontalDragActiveChange = onHorizontalDragActiveChange
            )
        }
        SettingsDivider(visible = showDivider)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSelectRow(
    title: String,
    selected: T,
    choices: List<SettingsChoice<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.value == selected }?.label.orEmpty()
    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsRowText(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = selectedLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                choices.forEach { choice ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = choice.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            RadioButton(
                                selected = choice.value == selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        },
                        trailingIcon = {
                            if (choice.value == selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onSelected(choice.value)
                            expanded = false
                        }
                    )
                }
            }
        }
        SettingsDivider(visible = showDivider)
    }
}

@Composable
fun <T> SettingsDialogSelectRow(
    title: String,
    selected: T,
    choices: List<SettingsChoice<T>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = title,
    subtitle: String? = null,
    showDivider: Boolean = true
) {
    var dialogVisible by remember { mutableStateOf(false) }
    val selectedLabel = choices.firstOrNull { it.value == selected }?.label.orEmpty()

    SettingsActionRow(
        title = title,
        subtitle = subtitle,
        value = selectedLabel,
        showChevron = true,
        showDivider = showDivider,
        modifier = modifier,
        onClick = { dialogVisible = true }
    )

    if (dialogVisible) {
        SettingsDialogSurface(onDismissRequest = { dialogVisible = false }) {
            Column(
                modifier = Modifier
                    .padding(vertical = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                    Text(
                        text = dialogTitle,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                    choices.forEach { choice ->
                        val isSelected = choice.value == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelected(choice.value)
                                        dialogVisible = false
                                    }
                                )
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = choice.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun SettingsRowText(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        subtitle?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.38f
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SettingsDivider(
    visible: Boolean,
    leadingInset: androidx.compose.ui.unit.Dp = 20.dp
) {
    if (visible) {
        HorizontalDivider(
            modifier = Modifier.padding(start = leadingInset),
            color = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = if (LocalIsLiquidGlassEnabled.current) 0.55f else 0.7f
            ),
            thickness = 0.5.dp
        )
    } else {
        Spacer(modifier = Modifier.height(0.dp))
    }
}
