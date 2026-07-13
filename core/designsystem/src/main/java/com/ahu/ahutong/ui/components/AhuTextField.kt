package com.ahu.ahutong.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import com.ahu.ahutong.core.designsystem.R
import com.ahu.ahutong.ui.theme.AhuColors
import com.kyant.capsule.ContinuousCapsule

/**
 * Capsule search field used by Grade / Exam / LostFound header search.
 */
@Composable
fun AhuSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    val placeholderText = placeholder ?: stringResource(id = R.string.search)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        shape = ContinuousCapsule,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AhuColors.onSurface,
            unfocusedTextColor = AhuColors.onSurface,
            cursorColor = AhuColors.primaryAction,
            focusedBorderColor = AhuColors.primaryAction,
            unfocusedBorderColor = AhuColors.onSurface.copy(alpha = 0.2f),
        ),
        placeholder = {
            Text(placeholderText, color = AhuColors.onSurface.copy(alpha = 0.45f))
        },
    )
}

/**
 * Labeled form field (publish sheet / payment forms).
 */
@Composable
fun AhuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    shape: Shape = RoundedCornerShape(percent = 20),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        label = { Text(label) },
        shape = shape,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AhuColors.onSurface,
            unfocusedTextColor = AhuColors.onSurface,
            focusedLabelColor = AhuColors.primaryAction,
            cursorColor = AhuColors.primaryAction,
            focusedBorderColor = AhuColors.primaryAction,
            unfocusedBorderColor = AhuColors.onSurface.copy(alpha = 0.25f),
        ),
    )
}
