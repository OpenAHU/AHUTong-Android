package com.ahu.ahutong.ui.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
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

/** 注册一个导览目标：布局变化（滚动、切页）时持续上报真实边界。 */
fun Modifier.tourTarget(id: String): Modifier = composed {
    DisposableEffect(id) {
        onDispose { TourBounds.forget(id) }
    }
    onGloballyPositioned { TourBounds.report(id, it.boundsInWindow()) }
}
