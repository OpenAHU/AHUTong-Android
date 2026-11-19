package com.ahu.ahutong.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

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

/** 目标被导览请求时自动滚动到可视区；与 [tourTarget] 搭配使用。 */
fun Modifier.tourBringIntoView(id: String): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    val signal = TourScrollRequest.signal
    LaunchedEffect(signal) {
        if (TourScrollRequest.targetId == id) {
            runCatching { requester.bringIntoView() }
        }
    }
    bringIntoViewRequester(requester)
}
