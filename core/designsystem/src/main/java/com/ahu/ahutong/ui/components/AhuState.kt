package com.ahu.ahutong.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ahu.ahutong.ui.theme.AhuColors

/**
 * Show a Toast when [message] becomes non-null, then call [onConsumed].
 *
 * Replaces the repeated:
 * ```
 * LaunchedEffect(errorMessage) {
 *   errorMessage?.let { Toast...; vm.error = null }
 * }
 * ```
 */
@Composable
fun AhuErrorToastEffect(
    message: String?,
    duration: Int = Toast.LENGTH_LONG,
    onConsumed: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, duration).show()
            onConsumed()
        }
    }
}

/**
 * Centered loading spinner, optionally overlaid on [content].
 */
@Composable
fun AhuLoading(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = contentAlignment,
    ) {
        CircularProgressIndicator(color = AhuColors.primaryAction)
    }
}

/**
 * Compact list-footer spinner used when paging / load-more is in progress.
 */
@Composable
fun AhuLoadingMore(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AhuColors.primaryAction)
    }
}

/**
 * Full-size box that shows a spinner while [loading] is true, else [content].
 */
@Composable
fun AhuLoadingContent(
    loading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (loading) {
            AhuLoading()
        }
    }
}

/**
 * Simple empty-state placeholder.
 */
@Composable
fun AhuEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AhuColors.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
