package com.ahu.ahutong.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** 导览目标的实时窗口坐标，控件离开组合时自动清除。 */
object TourBounds {
    private val rects = mutableStateMapOf<String, Rect>()

    fun get(id: String): Rect? = rects[id]

    fun report(id: String, rect: Rect) {
        rects[id] = rect
    }

    fun forget(id: String) {
        rects.remove(id)
    }
}

/**
 * 导览步进到长页面（如设置根页）时，通知目标把自己滚进可视区。
 * [targetId] 为空或没有对应控件时自然无副作用。
 */
object TourScrollRequest {
    var targetId by mutableStateOf<String?>(null)
        private set
    var signal by mutableIntStateOf(0)
        private set

    fun request(id: String) {
        targetId = id
        signal += 1
    }
}

/** 注册一个导览目标：布局变化（滚动、切页）时持续上报真实边界。 */
fun Modifier.tourTarget(id: String): Modifier = composed {
    DisposableEffect(id) {
        onDispose { TourBounds.forget(id) }
    }
    onGloballyPositioned { TourBounds.report(id, it.boundsInWindow()) }
}

/**
 * 让目标停到屏幕中央：请求矩形上下各扩出半个视口，系统会一次滚到位。
 * 等导览切步形变结束后再触发，聚焦框和页面从同一帧起一起动。
 */
fun Modifier.tourCenterIntoView(id: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val viewportPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    var rowSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val signal = TourScrollRequest.signal
    LaunchedEffect(signal) {
        if (TourScrollRequest.targetId != id) return@LaunchedEffect
        kotlinx.coroutines.delay(360)
        val height = rowSize.height.toFloat()
        if (height <= 0f) return@LaunchedEffect
        val extra = ((viewportPx - height) / 2f).coerceAtLeast(0f)
        runCatching {
            requester.bringIntoView(
                Rect(0f, -extra, rowSize.width.toFloat(), height + extra)
            )
        }
    }
    this
        .onSizeChanged { rowSize = it }
        .onGloballyPositioned { TourBounds.report(id, it.boundsInWindow()) }
        .bringIntoViewRequester(requester)
}
