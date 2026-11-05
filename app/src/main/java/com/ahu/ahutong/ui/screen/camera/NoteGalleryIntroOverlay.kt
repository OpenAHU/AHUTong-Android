package com.ahu.ahutong.ui.screen.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** The camera remains visible while every action except opening notes is intercepted. */
@Composable
internal fun NoteGalleryIntroOverlay(
    galleryBounds: Rect,
    onDismiss: () -> Unit,
    onOpenGallery: () -> Unit
) {
    var canDismiss by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        delay(2_000L)
        canDismiss = true
    }
    BackHandler {}
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.20f))
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { true }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (canDismiss) onDismiss() }
    ) {
        Text(
            "点此打开笔记页面",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 4f)
            ),
            modifier = Modifier.offset {
                IntOffset(
                    galleryBounds.left.roundToInt(),
                    (galleryBounds.top - with(density) { 40.dp.toPx() }).roundToInt().coerceAtLeast(0)
                )
            }
        )
        Box(
            Modifier.offset { IntOffset(galleryBounds.left.roundToInt(), galleryBounds.top.roundToInt()) }
                .size(with(density) { galleryBounds.width.toDp() }, with(density) { galleryBounds.height.toDp() })
                .border(2.dp, Color.White, RoundedCornerShape(11.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenGallery
                )
                .semantics { contentDescription = "打开笔记页面" }
        )
    }
}
