package com.ahu.ahutong.ui.theme.pack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.kyant.backdrop.Backdrop
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.ClassicPageScaffoldImpl
import com.ahu.ahutong.ui.components.MaterialButtonImpl
import com.ahu.ahutong.ui.components.MaterialCardImpl
import com.ahu.ahutong.ui.components.MaterialFabImpl
import com.ahu.ahutong.ui.components.MaterialFilterChipImpl
import com.ahu.ahutong.ui.components.MaterialHeaderIconButtonImpl
import com.ahu.ahutong.ui.components.MaterialModalBottomSheetImpl
import com.ahu.ahutong.ui.components.MaterialProgressImpl
import com.ahu.ahutong.ui.components.MaterialSelectFieldImpl
import com.ahu.ahutong.ui.components.MaterialToggleImpl
import com.ahu.ahutong.ui.components.RowPageHeaderImpl
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.components.TrailingAction
import com.ahu.ahutong.ui.components.OutlinedTextFieldImpl
import com.ahu.ahutong.ui.components.OutlinedSearchFieldImpl
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 组件契约包：页面层只面向 [LocalComponentPack] 调用，不出现任何主题分支。
 *
 * 默认实现基于 Material3（兜底主题）；各主题包按需 override。
 * 新增主题 = 新建一个子类并注册进 [AppUiTheme.componentPack]。
 *
 * 注意：契约成员一律不声明默认参数（由 App* 包装层显式传全）——
 * open 成员 + 默认值 + 值类型参数会触发 Kotlin 2.2 编译器
 * JvmInlineClassLowering 的 NPE（getTypeSubstitutionMap）。
 */
open class AppComponentPack {
    @Composable
    open fun Toggle(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
        contentDescription: String?
    ) {
        MaterialToggleImpl(checked, onCheckedChange, modifier, enabled, contentDescription)
    }

    @Composable
    open fun <T> SelectField(
        label: String,
        selected: T?,
        options: List<AppSelectOption<T>>,
        onSelected: (T) -> Unit,
        modifier: Modifier,
        placeholder: String,
        enabled: Boolean,
        valueTextAlign: TextAlign,
        miuixInsideMargin: PaddingValues,
        miuixStandalone: Boolean,
        liquidLabelWeight: Float,
        liquidValueWeight: Float
    ) {
        MaterialSelectFieldImpl(
            label = label,
            selected = selected,
            options = options,
            onSelected = onSelected,
            modifier = modifier,
            placeholder = placeholder,
            enabled = enabled,
            valueTextAlign = valueTextAlign
        )
    }

    @Composable
    open fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        variant: AppButtonVariant,
        content: @Composable RowScope.() -> Unit
    ) {
        MaterialButtonImpl(onClick, modifier, enabled, variant, content)
    }

    @Composable
    open fun Card(
        modifier: Modifier,
        shape: Shape,
        contentPadding: PaddingValues,
        enabled: Boolean,
        onClick: (() -> Unit)?,
        backdrop: Backdrop?,
        content: @Composable ColumnScope.() -> Unit
    ) {
        MaterialCardImpl(modifier, shape, contentPadding, enabled, onClick, content)
    }

    @Composable
    open fun CircularProgressIndicator(
        progress: (() -> Float)?,
        modifier: Modifier,
        size: Dp,
        strokeWidth: Dp,
        color: Color?
    ) {
        MaterialProgressImpl(progress, modifier, size, strokeWidth, color)
    }

    @Composable
    open fun FloatingActionButton(
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        MaterialFabImpl(onClick, modifier, content)
    }

    @Composable
    open fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier,
        enabled: Boolean,
        singleLine: Boolean,
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        visualTransformation: VisualTransformation
    ) {
        OutlinedTextFieldImpl(
            value, onValueChange, label, modifier, enabled, singleLine,
            keyboardOptions, keyboardActions, visualTransformation, liquid = false
        )
    }

    @Composable
    open fun SearchField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier,
        onSearch: (String) -> Unit
    ) {
        OutlinedSearchFieldImpl(value, onValueChange, placeholder, modifier, onSearch, liquid = false)
    }

    @Composable
    open fun HeaderIconButton(
        imageVector: ImageVector,
        miuixImageVector: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier,
        backdrop: Backdrop?,
        tint: Color?
    ) {
        MaterialHeaderIconButtonImpl(imageVector, contentDescription, onClick, modifier, tint)
    }

    @Composable
    open fun PageHeader(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        backdrop: Backdrop?,
        horizontalPadding: Dp,
        verticalPadding: Dp,
        actions: @Composable RowScope.() -> Unit
    ) {
        RowPageHeaderImpl(title, modifier, onBack, backdrop, horizontalPadding, verticalPadding, actions)
    }

    @Composable
    open fun FilterChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean
    ) {
        MaterialFilterChipImpl(selected, onClick, label, modifier, enabled)
    }

    @Composable
    open fun ModalBottomSheet(
        title: String,
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) {
        MaterialModalBottomSheetImpl(title, onDismissRequest, modifier, content)
    }

    /**
     * 统一页面壳（[com.ahu.ahutong.ui.components.AppPageScaffold] 的契约侧）：
     * 默认内联标题行；Radiant 包覆盖为固定渐变悬浮头，Miuix 包覆盖为大标题收起式。
     * [content] 滚动流与 [lazyContent] 懒列表二选一。
     */
    @Composable
    open fun PageScaffold(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        subtitle: String?,
        actions: List<TrailingAction>,
        trailingContent: (@Composable RowScope.() -> Unit)?,
        search: SecondarySearchState?,
        scrollEnabled: Boolean,
        bottomPadding: Dp,
        verticalArrangement: Arrangement.Vertical,
        listState: LazyListState,
        content: (@Composable ColumnScope.() -> Unit)?,
        lazyContent: (LazyListScope.() -> Unit)?,
        freeContent: (@Composable BoxScope.() -> Unit)?
    ) {
        ClassicPageScaffoldImpl(
            title, modifier, onBack, subtitle, actions, trailingContent, search,
            scrollEnabled, bottomPadding, verticalArrangement, listState, content, lazyContent, freeContent
        )
    }
}

/** 当前主题对应的组件契约包。页面与组件库一律从这里取实现，禁止再读 [AppUiTheme] 分支。 */
val LocalComponentPack = staticCompositionLocalOf<AppComponentPack> { MaterialComponentPack }

/** 主题 → 组件包注册表。新增主题在此注册一行即可。 */
val AppUiTheme.componentPack: AppComponentPack
    get() = when (this) {
        AppUiTheme.MIUIX -> MiuixComponentPack
        AppUiTheme.LIQUID_GLASS, AppUiTheme.RADIANT -> RadiantComponentPack
        AppUiTheme.MATERIAL -> MaterialComponentPack
    }
