package com.ahu.ahutong.ui.theme.pack

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.MiuixButtonImpl
import com.ahu.ahutong.ui.components.MiuixCardImpl
import com.ahu.ahutong.ui.components.MiuixFabImpl
import com.ahu.ahutong.ui.components.MiuixProgressImpl
import com.ahu.ahutong.ui.components.MiuixSelectFieldImpl
import com.ahu.ahutong.ui.components.MiuixToggleImpl
import com.ahu.ahutong.ui.components.AppSelectOption

/**
 * Miuix（HyperOS 风格）主题包：手感件直接包装 Miuix 库实现。
 * 未覆盖的成员（如未来新增的契约组件）自动回落到 [AppComponentPack] 的默认实现。
 */
object MiuixComponentPack : AppComponentPack() {
    @Composable
    override fun Toggle(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
        contentDescription: String?
    ) {
        MiuixToggleImpl(checked, onCheckedChange, modifier, enabled, contentDescription)
    }

    @Composable
    override fun <T> SelectField(
        label: String,
        selected: T?,
        options: List<AppSelectOption<T>>,
        onSelected: (T) -> Unit,
        modifier: Modifier,
        placeholder: String,
        enabled: Boolean,
        valueTextAlign: androidx.compose.ui.text.style.TextAlign,
        miuixInsideMargin: PaddingValues,
        miuixStandalone: Boolean,
        liquidLabelWeight: Float,
        liquidValueWeight: Float
    ) {
        MiuixSelectFieldImpl(
            label = label,
            selected = selected,
            options = options,
            onSelected = onSelected,
            modifier = modifier,
            enabled = enabled,
            insideMargin = miuixInsideMargin,
            standalone = miuixStandalone
        )
    }

    @Composable
    override fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        variant: AppButtonVariant,
        content: @Composable RowScope.() -> Unit
    ) {
        MiuixButtonImpl(onClick, modifier, enabled, variant, content)
    }

    @Composable
    override fun Card(
        modifier: Modifier,
        shape: Shape,
        contentPadding: PaddingValues,
        enabled: Boolean,
        onClick: (() -> Unit)?,
        backdrop: com.kyant.backdrop.Backdrop?,
        content: @Composable ColumnScope.() -> Unit
    ) {
        MiuixCardImpl(modifier, contentPadding, enabled, onClick, content)
    }

    @Composable
    override fun CircularProgressIndicator(
        progress: (() -> Float)?,
        modifier: Modifier,
        size: Dp,
        strokeWidth: Dp,
        color: Color?
    ) {
        MiuixProgressImpl(progress, modifier, size, strokeWidth, color)
    }

    @Composable
    override fun FloatingActionButton(
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        MiuixFabImpl(onClick, modifier, content)
    }

    @Composable
    override fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier,
        enabled: Boolean,
        singleLine: Boolean,
        keyboardOptions: androidx.compose.foundation.text.KeyboardOptions,
        keyboardActions: androidx.compose.foundation.text.KeyboardActions,
        visualTransformation: androidx.compose.ui.text.input.VisualTransformation
    ) {
        com.ahu.ahutong.ui.components.MiuixTextFieldImpl(
            value, onValueChange, label, modifier, enabled, singleLine,
            keyboardOptions, keyboardActions, visualTransformation
        )
    }

    @Composable
    override fun SearchField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier,
        onSearch: (String) -> Unit
    ) {
        com.ahu.ahutong.ui.components.MiuixSearchFieldImpl(value, onValueChange, placeholder, modifier, onSearch)
    }

    @Composable
    override fun FilterChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean
    ) {
        com.ahu.ahutong.ui.components.MiuixFilterChipImpl(selected, onClick, label, modifier, enabled)
    }

    @Composable
    override fun ModalBottomSheet(
        title: String,
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        com.ahu.ahutong.ui.components.MiuixModalBottomSheetImpl(title, onDismissRequest, modifier, content)
    }

    @Composable
    override fun HeaderIconButton(
        imageVector: androidx.compose.ui.graphics.vector.ImageVector,
        miuixImageVector: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier,
        backdrop: com.kyant.backdrop.Backdrop?,
        tint: Color?
    ) {
        com.ahu.ahutong.ui.components.MiuixHeaderIconButtonImpl(
            miuixImageVector, contentDescription, onClick, modifier, tint
        )
    }

    @Composable
    override fun PageHeader(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        backdrop: com.kyant.backdrop.Backdrop?,
        horizontalPadding: androidx.compose.ui.unit.Dp,
        verticalPadding: androidx.compose.ui.unit.Dp,
        actions: @Composable RowScope.() -> Unit
    ) {
        com.ahu.ahutong.ui.components.MiuixPageHeaderImpl(title, modifier, onBack, actions)
    }

    @Composable
    override fun PageScaffold(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        subtitle: String?,
        actions: List<com.ahu.ahutong.ui.components.TrailingAction>,
        trailingContent: (@Composable RowScope.() -> Unit)?,
        search: com.ahu.ahutong.ui.components.SecondarySearchState?,
        scrollEnabled: Boolean,
        bottomPadding: androidx.compose.ui.unit.Dp,
        verticalArrangement: androidx.compose.foundation.layout.Arrangement.Vertical,
        listState: androidx.compose.foundation.lazy.LazyListState,
        content: (@Composable ColumnScope.() -> Unit)?,
        lazyContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)?,
        freeContent: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)?
    ) {
        com.ahu.ahutong.ui.components.MiuixPageScaffoldImpl(
            title, modifier, onBack, subtitle, actions, trailingContent, search,
            scrollEnabled, bottomPadding, verticalArrangement, listState, content, lazyContent, freeContent
        )
    }
}
