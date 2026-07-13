package com.ahu.ahutong.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ahu.ahutong.ui.theme.AhuColors

/**
 * Text that highlights case-insensitive [keyword] matches with accent background.
 * Used by LostFound search results.
 */
@Composable
fun AhuHighlightText(
    text: String,
    keyword: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    highlightBackground: Color? = null,
) {
    val highlight = highlightBackground ?: AhuColors.primaryAction.copy(alpha = 0.35f)
    val annotated = buildAnnotatedString {
        if (keyword.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lowerText = text.lowercase()
        val lowerKeyword = keyword.lowercase()
        var start = 0
        while (true) {
            val match = lowerText.indexOf(lowerKeyword, start)
            if (match < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, match))
            withStyle(
                SpanStyle(
                    background = highlight,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(text.substring(match, match + keyword.length))
            }
            start = match + keyword.length
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
    )
}

/**
 * Build annotated string with keyword highlights (for composing multi-part lines).
 */
@Composable
fun ahuHighlightAnnotated(
    text: String,
    keyword: String,
    highlightBackground: Color? = null,
) = buildAnnotatedString {
    val highlight = highlightBackground ?: AhuColors.primaryAction.copy(alpha = 0.35f)
    if (keyword.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    val lowerText = text.lowercase()
    val lowerKeyword = keyword.lowercase()
    var start = 0
    while (true) {
        val match = lowerText.indexOf(lowerKeyword, start)
        if (match < 0) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, match))
        withStyle(
            SpanStyle(
                background = highlight,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(text.substring(match, match + keyword.length))
        }
        start = match + keyword.length
    }
}
