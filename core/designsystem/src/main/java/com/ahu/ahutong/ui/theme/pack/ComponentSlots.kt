package com.ahu.ahutong.ui.theme.pack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppSelectOption
import com.ahu.ahutong.ui.components.SecondarySearchState
import com.ahu.ahutong.ui.components.TrailingAction
import com.kyant.backdrop.Backdrop

/**
 * 组件槽位：Theme Park 的最小混配单元。
 * 每个槽位对应契约（[AppComponentPack]）的一个成员，可独立指定实现来源。
 */
enum class ComponentSlotId(val displayName: String) {
    Button("按钮"),
    Card("卡片"),
    Toggle("开关"),
    SelectField("下拉选择器"),
    TextField("文本框"),
    SearchField("搜索框"),
    HeaderIconButton("头部图标钮"),
    PageHeader("页头"),
    FilterChip("筛选片"),
    Progress("环形进度"),
    Fab("悬浮按钮"),
    BottomSheet("底部抽屉"),
    PageScaffold("页面壳");

    companion object {
        fun fromName(name: String?): ComponentSlotId? = entries.firstOrNull { it.name == name }
    }
}

/** 槽位实现来源。序列化键稳定，勿改（用户配置持久化用）。 */
enum class SlotSource(val storageKey: String, val displayName: String) {
    MATERIAL("material", "Material 3"),
    MIUIX("miuix", "Miuix"),
    RADIANT("radiant", "曜光 Radiant");

    /** 该来源对应的整套组件包（槽位实现的供应方）。 */
    val pack: AppComponentPack
        get() = when (this) {
            MATERIAL -> MaterialComponentPack
            MIUIX -> MiuixComponentPack
            RADIANT -> RadiantComponentPack
        }

    companion object {
        fun fromStorage(value: String?): SlotSource? = entries.firstOrNull { it.storageKey == value }
    }
}

/** 槽位覆盖表 ↔ 持久化字符串（"Button=radiant;Card=miuix"）。 */
fun Map<ComponentSlotId, SlotSource>.serializeSlotOverrides(): String =
    entries.joinToString(";") { (slot, source) -> "${slot.name}=${source.storageKey}" }

fun parseSlotOverrides(raw: String?): Map<ComponentSlotId, SlotSource> =
    raw.orEmpty().split(';')
        .mapNotNull { entry ->
            val (slotName, sourceKey) = entry.split('=', limit = 2).takeIf { it.size == 2 }
                ?: return@mapNotNull null
            val slot = ComponentSlotId.fromName(slotName) ?: return@mapNotNull null
            val source = SlotSource.fromStorage(sourceKey) ?: return@mapNotNull null
            slot to source
        }
        .toMap()

/**
 * 混搭组件包：以 [base] 预设套装为底，[overrides] 逐槽指定实现来源。
 *
 * 页面层与组件包装层完全无感——契约（[AppComponentPack]）与注册表
 * （[LocalComponentPack]）不变，混搭发生在包内部逐成员委托。
 *
 * 注意：槽位实现若依赖宿主环境（如 Radiant 的玻璃 backdrop），
 * 在非对应套装下按其既有降级路径渲染（实色回落），不做跨环境搬运。
 */
class MixedComponentPack(
    private val base: AppComponentPack,
    private val overrides: Map<ComponentSlotId, SlotSource>
) : AppComponentPack() {

    private fun pack(slot: ComponentSlotId): AppComponentPack =
        overrides[slot]?.pack ?: base

    @Composable
    override fun Toggle(
        checked: Boolean,
        onCheckedChange: ((Boolean) -> Unit)?,
        modifier: Modifier,
        enabled: Boolean,
        contentDescription: String?
    ) = pack(ComponentSlotId.Toggle).Toggle(checked, onCheckedChange, modifier, enabled, contentDescription)

    @Composable
    override fun <T> SelectField(
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
    ) = pack(ComponentSlotId.SelectField).SelectField(
        label, selected, options, onSelected, modifier, placeholder, enabled,
        valueTextAlign, miuixInsideMargin, miuixStandalone, liquidLabelWeight, liquidValueWeight
    )

    @Composable
    override fun Button(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        variant: AppButtonVariant,
        content: @Composable RowScope.() -> Unit
    ) = pack(ComponentSlotId.Button).Button(onClick, modifier, enabled, variant, content)

    @Composable
    override fun Card(
        modifier: Modifier,
        shape: Shape,
        contentPadding: PaddingValues,
        enabled: Boolean,
        onClick: (() -> Unit)?,
        backdrop: Backdrop?,
        content: @Composable ColumnScope.() -> Unit
    ) = pack(ComponentSlotId.Card).Card(modifier, shape, contentPadding, enabled, onClick, backdrop, content)

    @Composable
    override fun CircularProgressIndicator(
        progress: (() -> Float)?,
        modifier: Modifier,
        size: Dp,
        strokeWidth: Dp,
        color: Color?
    ) = pack(ComponentSlotId.Progress).CircularProgressIndicator(progress, modifier, size, strokeWidth, color)

    @Composable
    override fun FloatingActionButton(
        onClick: () -> Unit,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) = pack(ComponentSlotId.Fab).FloatingActionButton(onClick, modifier, content)

    @Composable
    override fun TextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: String,
        modifier: Modifier,
        enabled: Boolean,
        singleLine: Boolean,
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        visualTransformation: VisualTransformation
    ) = pack(ComponentSlotId.TextField).TextField(
        value, onValueChange, label, modifier, enabled, singleLine,
        keyboardOptions, keyboardActions, visualTransformation
    )

    @Composable
    override fun SearchField(
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        modifier: Modifier,
        onSearch: (String) -> Unit
    ) = pack(ComponentSlotId.SearchField).SearchField(value, onValueChange, placeholder, modifier, onSearch)

    @Composable
    override fun HeaderIconButton(
        imageVector: ImageVector,
        miuixImageVector: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier,
        backdrop: Backdrop?,
        tint: Color?
    ) = pack(ComponentSlotId.HeaderIconButton)
        .HeaderIconButton(imageVector, miuixImageVector, contentDescription, onClick, modifier, backdrop, tint)

    @Composable
    override fun PageHeader(
        title: String,
        modifier: Modifier,
        onBack: (() -> Unit)?,
        backdrop: Backdrop?,
        horizontalPadding: Dp,
        verticalPadding: Dp,
        actions: @Composable RowScope.() -> Unit
    ) = pack(ComponentSlotId.PageHeader)
        .PageHeader(title, modifier, onBack, backdrop, horizontalPadding, verticalPadding, actions)

    @Composable
    override fun FilterChip(
        selected: Boolean,
        onClick: () -> Unit,
        label: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean
    ) = pack(ComponentSlotId.FilterChip).FilterChip(selected, onClick, label, modifier, enabled)

    @Composable
    override fun ModalBottomSheet(
        title: String,
        onDismissRequest: () -> Unit,
        modifier: Modifier,
        content: @Composable ColumnScope.() -> Unit
    ) = pack(ComponentSlotId.BottomSheet).ModalBottomSheet(title, onDismissRequest, modifier, content)

    @Composable
    override fun PageScaffold(
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
    ) = pack(ComponentSlotId.PageScaffold).PageScaffold(
        title, modifier, onBack, subtitle, actions, trailingContent, search,
        scrollEnabled, bottomPadding, verticalArrangement, listState, content, lazyContent, freeContent
    )
}

/** 主题套装 + 槽位覆盖 → 实际生效的组件包。无覆盖时直接返回套装（零包装开销）。 */
fun AppComponentPack.mixing(overrides: Map<ComponentSlotId, SlotSource>): AppComponentPack =
    if (overrides.isEmpty()) this else MixedComponentPack(this, overrides)
