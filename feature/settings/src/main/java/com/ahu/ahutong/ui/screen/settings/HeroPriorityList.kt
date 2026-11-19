package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ahu.ahutong.core.storage.HomeHeroSlot
import com.ahu.ahutong.ui.components.settingsPrimaryTextStyle
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import kotlin.math.roundToInt

private val HeroRowHeight = 64.dp

private fun HomeHeroSlot.label(): String = when (this) {
    HomeHeroSlot.ONGOING_EXAM -> "正在进行的考试"
    HomeHeroSlot.ONGOING_COURSE -> "正在进行的课程"
    HomeHeroSlot.ASSIGNMENT -> "最近截止的作业"
    HomeHeroSlot.NEXT_COURSE -> "下一节课"
    HomeHeroSlot.NEXT_EXAM -> "下一场考试"
}

/** 主页 Hero 的上屏优先级：拖动排列，顺序即绝对优先级。 */
@Composable
internal fun HeroPriorityList(
    order: List<HomeHeroSlot>,
    onCommit: (List<HomeHeroSlot>) -> Unit
) {
    var localOrder by remember { mutableStateOf(HomeHeroSlot.normalize(order)) }
    var draggingSlot by remember { mutableStateOf<HomeHeroSlot?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { HeroRowHeight.toPx() }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // 外部写入只在未拖动时同步进来，避免打断手势
    LaunchedEffect(order) {
        if (draggingSlot == null) localOrder = HomeHeroSlot.normalize(order)
    }

    fun moveTo(from: Int, to: Int): List<HomeHeroSlot> {
        val next = localOrder.toMutableList().apply { add(to, removeAt(from)) }
        localOrder = next
        return next
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroRowHeight * localOrder.size + 8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        userScrollEnabled = false
    ) {
        itemsIndexed(localOrder, key = { _, slot -> slot }) { index, slot ->
            val dragging = draggingSlot == slot
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HeroRowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .then(
                        if (dragging) {
                            Modifier
                                .graphicsLayer { translationY = dragOffset }
                                .shadow(16.dp, SmoothRoundedCornerShape(14.dp), clip = true)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    SmoothRoundedCornerShape(14.dp)
                                )
                        } else {
                            Modifier.animateItem()
                        }
                    )
            ) {
                Column(Modifier.fillMaxSize()) {
                    HeroPriorityRow(
                        slot = slot,
                        position = index + 1,
                        dragging = dragging,
                        modifier = Modifier.weight(1f),
                        onMoveUp = if (index > 0) {
                            { onCommit(moveTo(index, index - 1)) }
                        } else null,
                        onMoveDown = if (index < localOrder.lastIndex) {
                            { onCommit(moveTo(index, index + 1)) }
                        } else null,
                        dragHandleModifier = Modifier.pointerInput(slot) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingSlot = slot
                                    dragOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    // 手势块以 slot 为 key 不会随换位重建，索引必须现查，不能用组合时捕获的值
                                    val from = localOrder.indexOf(slot)
                                    if (from < 0) return@detectDragGestures
                                    dragOffset += amount.y
                                    val shift = (dragOffset / rowHeightPx).roundToInt()
                                    val target = (from + shift).coerceIn(0, localOrder.lastIndex)
                                    if (target != from) {
                                        moveTo(from, target)
                                        dragOffset -= (target - from) * rowHeightPx
                                        haptics.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }
                                },
                                onDragEnd = {
                                    draggingSlot = null
                                    dragOffset = 0f
                                    onCommit(localOrder)
                                },
                                onDragCancel = {
                                    draggingSlot = null
                                    dragOffset = 0f
                                    localOrder = HomeHeroSlot.normalize(order)
                                }
                            )
                        }
                    )
                    if (index != localOrder.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPriorityRow(
    slot: HomeHeroSlot,
    position: Int,
    dragging: Boolean,
    modifier: Modifier,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    dragHandleModifier: Modifier
) {
    val accessibilityActions = buildList {
        onMoveUp?.let { add(CustomAccessibilityAction("上移") { it(); true }) }
        onMoveDown?.let { add(CustomAccessibilityAction("下移") { it(); true }) }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp)
            .then(
                if (accessibilityActions.isEmpty()) {
                    Modifier
                } else {
                    Modifier.semantics { customActions = accessibilityActions }
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = position.toString(),
            modifier = Modifier.width(22.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = slot.label(),
            modifier = Modifier.weight(1f),
            style = settingsPrimaryTextStyle(),
            color = if (dragging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Box(
            modifier = dragHandleModifier
                .size(48.dp)
                .clip(SmoothRoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "拖动排序",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
