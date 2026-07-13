package com.ahu.ahutong.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Full-screen black image gallery dialog with swipe pager.
 *
 * @param imageUrls absolute image URLs
 * @param initialIndex starting page
 */
@Composable
fun AhuImageViewer(
    imageUrls: List<String>,
    onDismissRequest: () -> Unit,
    initialIndex: Int = 0,
) {
    if (imageUrls.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, imageUrls.lastIndex),
        pageCount = { imageUrls.size },
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = imageUrls[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding(),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                )
            }

            if (imageUrls.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                )
            }
        }
    }
}
