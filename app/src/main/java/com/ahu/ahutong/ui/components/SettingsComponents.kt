package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.window.Dialog
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.theme.LiquidGlassSurfaceLevel
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults as MiuixBasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

data class SettingsChoice<T>(
    val value: T,
    val label: String
)

@Composable
private fun rememberThemeHapticAction(action: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    val useMiuixFeedback = LocalAppUiTheme.current == AppUiTheme.MIUIX
    return remember(action, haptic, useMiuixFeedback) {
        {
            if (useMiuixFeedback) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            action()
        }
    }
}

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
fun settingsScreenBackground(): Color = if (LocalIsLiquidGlassEnabled.current) {
    MaterialTheme.colorScheme.surfaceContainerLowest
} else {
    MaterialTheme.colorScheme.surface
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
    if (isRadiantUi) {
        GlassBackdropContainer(modifier = modifier, content = content)
        return
    }
    val backdrop = LocalLiquidGlassAmbientBackdrop.current
    val background = settingsScreenBackground()

    Box(modifier = modifier.appLiquidGlassSceneBackground(background)) {
        content(backdrop)
    }
}

@Composable
fun SettingsPageLayout(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
    scrollState: ScrollState = rememberScrollState(),
    scrollEnabled: Boolean = true,
    bottomPadding: androidx.compose.ui.unit.Dp = 112.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val uiTheme = LocalAppUiTheme.current
    if (uiTheme != AppUiTheme.MIUIX) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = scrollEnabled)
                .systemBarsPadding()
                .padding(bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsPageHeader(title = title, onBack = onBack, backdrop = backdrop)
            content()
        }
        return
    }

    val scrollBehavior = MiuixScrollBehavior()
    val haptic = LocalHapticFeedback.current
    val onBackWithFeedback = onBack?.let { callback ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            callback()
        }
    }
    MiuixScaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MiuixTopAppBar(
                title = title,
                largeTitle = title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    onBackWithFeedback?.let { callback ->
                        MiuixIconButton(onClick = callback) {
                            MiuixIcon(
                                imageVector = MiuixIcons.Useful.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .scrollEndHaptic()
                .verticalScroll(scrollState, enabled = scrollEnabled)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = paddingValues.calculateTopPadding() + 20.dp,
                        bottom = bottomPadding
                    )
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

@Composable
fun SettingsPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backdrop: Backdrop? = null
) {
    AppPageHeader(
        title = title,
        modifier = modifier,
        onBack = onBack,
        backdrop = backdrop
    )
}

@Composable
fun SettingsHeroCard(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val onClickWithFeedback = rememberThemeHapticAction(onClick)
    val uiTheme = LocalAppUiTheme.current
    val isRadiant = uiTheme == AppUiTheme.RADIANT
    if (uiTheme == AppUiTheme.MIUIX) {
        MiuixCard(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            pressFeedbackType = PressFeedbackType.Sink,
            onClick = onClickWithFeedback
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
        return
    }
    val shape = SmoothRoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isRadiant) {
                    Modifier.liquidGlassSurface(
                        backdrop = backdrop,
                        shape = shape,
                        surfaceColor = liquidGlassTint()
                    )
                } else {
                    Modifier.appLiquidGlassSurface(
                        shape = shape,
                        fallbackColor = MaterialTheme.colorScheme.primaryContainer,
                        level = LiquidGlassSurfaceLevel.Floating,
                        backdrop = backdrop
                    )
                }
            )
            .clickable(onClick = onClickWithFeedback)
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
    val uiTheme = LocalAppUiTheme.current
    val isRadiant = uiTheme == AppUiTheme.RADIANT
    if (uiTheme == AppUiTheme.MIUIX) {
        Column(modifier = modifier.fillMaxWidth()) {
            MiuixSmallTitle(text = title)
            MiuixCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(0.dp)
            ) {
                content()
            }
        }
        return
    }
    val isLiquid = LocalIsLiquidGlassEnabled.current
    val shape = SmoothRoundedCornerShape(if (isLiquid) 26.dp else 24.dp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isRadiant) 8.dp else 6.dp)
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
                    if (isRadiant) {
                        backdrop?.let {
                            Modifier.liquidGlassSurface(
                                backdrop = it,
                                shape = shape,
                                surfaceColor = liquidGlassTint()
                            )
                        } ?: Modifier
                            .clip(shape)
                            .background(settingsGroupColor())
                    } else {
                        Modifier.appLiquidGlassSurface(
                            shape = shape,
                            fallbackColor = settingsGroupColor(),
                            level = LiquidGlassSurfaceLevel.Panel,
                            backdrop = backdrop
                        )
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
    val onClickWithFeedback = rememberThemeHapticAction(onClick)
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        Column(modifier = modifier.fillMaxWidth()) {
            SuperArrow(
                title = title,
                titleColor = MiuixBasicComponentDefaults.titleColor(
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onBackground
                    }
                ),
                summary = subtitle,
                leftAction = leadingIcon?.let { icon ->
                    {
                        MiuixIcon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            tint = if (destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MiuixTheme.colorScheme.primary
                            }
                        )
                    }
                },
                rightActions = {
                    value?.let {
                        MiuixText(
                            text = it,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                onClick = onClickWithFeedback
            )
            SettingsDivider(visible = showDivider)
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClickWithFeedback)
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
        SettingsDivider(
            visible = showDivider,
            leadingInset = if (leadingIcon == null && leadingPainter == null) 20.dp else 74.dp
        )
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
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        Column(modifier = modifier.fillMaxWidth()) {
            MiuixBasicComponent(
                title = title,
                summary = subtitle,
                modifier = Modifier.fillMaxWidth(),
                rightActions = {
                    value?.let {
                        MiuixText(
                            text = it,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            )
            SettingsDivider(visible = showDivider)
        }
        return
    }
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
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val haptic = LocalHapticFeedback.current
        val onCheckedWithFeedback: (Boolean) -> Unit = { checked ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelectedChange(checked)
        }
        Column(modifier = modifier.fillMaxWidth()) {
            SuperSwitch(
                checked = selected,
                onCheckedChange = onCheckedWithFeedback,
                title = title,
                summary = subtitle,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            )
            SettingsDivider(visible = showDivider)
        }
        return
    }
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
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) {
        val selectedIndex = choices.indexOfFirst { it.value == selected }.coerceAtLeast(0)
        val haptic = LocalHapticFeedback.current
        Column(modifier = modifier.fillMaxWidth()) {
            SuperDropdown(
                items = choices.map(SettingsChoice<T>::label),
                selectedIndex = selectedIndex,
                title = title,
                summary = subtitle,
                modifier = Modifier.fillMaxWidth(),
                onSelectedIndexChange = { index ->
                    choices.getOrNull(index)?.let {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelected(it.value)
                    }
                }
            )
            SettingsDivider(visible = showDivider)
        }
        return
    }
    if (LocalAppUiTheme.current.usesLiquidGlass) {
        var expanded by remember { mutableStateOf(false) }
        var anchorBounds by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }
        val selectedLabel = choices.firstOrNull { it.value == selected }?.label.orEmpty()
        val popupWidth = LocalConfiguration.current.screenWidthDp.dp * 0.5f
        val popupOptions = remember(choices) {
            choices.map { choice -> AppSelectOption(choice.value, choice.label) }
        }
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsRowText(
                    title = title,
                    subtitle = subtitle,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    Row(
                        modifier = Modifier
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                anchorBounds = IntRect(
                                    left = bounds.left.roundToInt(),
                                    top = bounds.top.roundToInt(),
                                    right = bounds.right.roundToInt(),
                                    bottom = bounds.bottom.roundToInt()
                                )
                            }
                            .heightIn(min = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button
                            ) { expanded = !expanded }
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        LiquidGlassDropdownIndicator(
                            expanded = expanded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LiquidGlassDropdownPopup(
                        expanded = expanded,
                        anchorBoundsInWindow = anchorBounds,
                        popupWidth = popupWidth,
                        selected = selected,
                        options = popupOptions,
                        onSelected = onSelected,
                        onDismiss = { expanded = false }
                    )
                }
            }
            SettingsDivider(visible = showDivider)
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val isLiquidGlass = LocalAppUiTheme.current.usesLiquidGlass
    val selectedLabel = choices.firstOrNull { it.value == selected }?.label.orEmpty()
    val menuMinWidth = LocalConfiguration.current.screenWidthDp.dp * 0.5f
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
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .heightIn(min = 48.dp)
                        .then(
                            if (isLiquidGlass) {
                                Modifier
                                    .clip(SmoothRoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.32f))
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    modifier = Modifier
                        .widthIn(min = menuMinWidth)
                        .then(
                            if (isLiquidGlass) {
                                Modifier.appLiquidGlassSurface(
                                    shape = SmoothRoundedCornerShape(20.dp),
                                    fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
                                    level = LiquidGlassSurfaceLevel.Floating,
                                    backdrop = LocalLiquidGlassContentBackdrop.current,
                                    backdropSamplingEnabled = false
                                )
                            } else {
                                Modifier
                            }
                        ),
                    matchAnchorWidth = false,
                    containerColor = if (isLiquidGlass) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    tonalElevation = 0.dp
                ) {
                    choices.forEach { choice ->
                        val isSelected = choice.value == selected
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = choice.label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    }
                                ),
                            onClick = {
                                onSelected(choice.value)
                                expanded = false
                            }
                        )
                    }
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
    if (LocalAppUiTheme.current == AppUiTheme.MIUIX) return
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
