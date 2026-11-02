package com.ahu.ahutong.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider as MiuixPopupPositionProvider

/** Keeps Miuix placement rules while letting Compose own the current window's popup. */
internal class MiuixDropdownPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = ListPopupDefaults.DropdownPositionProvider.calculatePosition(
        anchorBounds = anchorBounds,
        windowBounds = IntRect(0, 0, windowSize.width, windowSize.height),
        layoutDirection = layoutDirection,
        popupContentSize = popupContentSize,
        popupMargin = IntRect(0, gapPx, 0, gapPx),
        alignment = MiuixPopupPositionProvider.Align.Right
    )
}
