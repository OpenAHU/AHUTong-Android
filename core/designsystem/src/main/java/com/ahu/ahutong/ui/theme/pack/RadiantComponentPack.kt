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
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.RadiantButtonImpl
import com.ahu.ahutong.ui.components.RadiantCardImpl
import com.ahu.ahutong.ui.components.RadiantFabImpl
import com.ahu.ahutong.ui.components.RadiantProgressImpl
import com.ahu.ahutong.ui.components.RadiantSelectFieldImpl
import com.ahu.ahutong.ui.components.RadiantToggleImpl
import com.kyant.backdrop.Backdrop

/**
 * Radiant 曜光主题包：自研液态玻璃实现（Liquid 系列）。
 * LIQUID_GLASS 与 RADIANT 共用本包——两者的组件实现本就同源。
 */
object RadiantComponentPack : AppComponentPack() {
    @Composable
    override fun Toggle(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
        contentDescription: String?
    ) {
        RadiantToggleImpl(checked, onCheckedChange, modifier, enabled, contentDescription)
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
        RadiantSelectFieldImpl(
            label = label,
            selected = selected,
            options = options,
            onSelected = onSelected,
            modifier = modifier,
            placeholder = placeholder,
            enabled = enabled,
            valueTextAlign = valueTextAlign,
            labelWeight = liquidLabelWeight,
            valueWeight = liquidValueWeight
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
        RadiantButtonImpl(onClick, modifier, enabled, variant, content)
    }

    @Composable
    override fun Card(
        modifier: Modifier,
        shape: Shape,
        contentPadding: PaddingValues,
        enabled: Boolean,
        onClick: (() -> Unit)?,
        backdrop: Backdrop?,
        content: @Composable ColumnScope.() -> Unit
    ) {
        RadiantCardImpl(modifier, shape, contentPadding, enabled, onClick, backdrop, content)
    }

    @Composable
    override fun CircularProgressIndicator(
        progress: (() -> Float)?,
        modifier: Modifier,
        size: Dp,
        strokeWidth: Dp,
        color: Color?
    ) {
        RadiantProgressImpl(progress, modifier, size, strokeWidth, color)
    }

    @Composable
    override fun FloatingActionButton(
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        RadiantFabImpl(onClick, modifier, content)
    }

    @Composable
    override fun HeaderIconButton(
        imageVector: androidx.compose.ui.graphics.vector.ImageVector,
        miuixImageVector: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier,
        backdrop: Backdrop?,
        tint: Color?
    ) {
        com.ahu.ahutong.ui.components.RadiantHeaderIconButtonImpl(
            imageVector, contentDescription, onClick, modifier, backdrop, tint
        )
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
        com.ahu.ahutong.ui.components.OutlinedTextFieldImpl(
            value, onValueChange, label, modifier, enabled, singleLine,
            keyboardOptions, keyboardActions, visualTransformation, liquid = true
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
        com.ahu.ahutong.ui.components.OutlinedSearchFieldImpl(
            value, onValueChange, placeholder, modifier, onSearch, liquid = true
        )
    }

    @Composable
    override fun ModalBottomSheet(
        title: String,
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        com.ahu.ahutong.ui.components.RadiantModalBottomSheetImpl(title, onDismissRequest, modifier, content)
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
        com.ahu.ahutong.ui.components.RadiantPageScaffoldImpl(
            title, modifier, subtitle, actions, trailingContent, search,
            scrollEnabled, bottomPadding, verticalArrangement, listState, content, lazyContent, freeContent
        )
    }
}
