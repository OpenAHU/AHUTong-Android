package com.ahu.ahutong.ui.screen.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.data.model.AppUiTheme
import com.ahu.ahutong.data.model.DEFAULT_THEME_COLOR
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppSlider
import com.ahu.ahutong.ui.components.AppTextField
import com.ahu.ahutong.ui.components.SettingsBackdropContainer
import com.ahu.ahutong.ui.components.SettingsPageLayout
import com.ahu.ahutong.ui.components.SettingsSection
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.PreferencesViewModel
import java.util.Locale

private data class ThemeColorChoice(val value: String, val name: String, val color: Color)

private val themeColorChoices = listOf(
    ThemeColorChoice("#FF4A90E2", "极光蓝", Color(0xFF4A90E2)),
    ThemeColorChoice("#FFE07A9F", "樱花粉", Color(0xFFE07A9F)),
    ThemeColorChoice("#FFF4A261", "落日橙", Color(0xFFF4A261)),
    ThemeColorChoice("#FF6A994E", "苔藓绿", Color(0xFF6A994E)),
    ThemeColorChoice("#FF9B7EDE", "薰衣草", Color(0xFF9B7EDE)),
    ThemeColorChoice("#FF2E8B57", "翡翠", Color(0xFF2E8B57))
)

internal fun themeColorName(value: String?): String = when (value) {
    null -> "系统"
    DEFAULT_THEME_COLOR -> "默认"
    else -> themeColorChoices.firstOrNull { it.value.equals(value, ignoreCase = true) }?.name ?: "自定义"
}

@Composable
fun ThemeColorSettings(
    onBack: () -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val themeColor by viewModel.themeColor.collectAsState()
    val appUiTheme by viewModel.appUiTheme.collectAsState()
    val initialColor = remember(themeColor) {
        runCatching { android.graphics.Color.parseColor(themeColor) }.getOrNull()
    } ?: MaterialTheme.colorScheme.primary.toArgb()
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hue by rememberSaveable(themeColor) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by rememberSaveable(themeColor) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by rememberSaveable(themeColor) { mutableFloatStateOf(initialHsv[2]) }
    val draftColor = Color.hsv(hue, saturation, brightness)
    val draftHex = String.format(Locale.ROOT, "#%06X", draftColor.toArgb() and 0xFFFFFF)
    var hexInput by rememberSaveable(themeColor) { mutableStateOf(draftHex) }
    val validHex = hexInput.matches(Regex("#[0-9a-fA-F]{6}"))
    val updatePalette: (Float, Float) -> Unit = { nextSaturation, nextBrightness ->
        saturation = nextSaturation
        brightness = nextBrightness
        hexInput = String.format(
            Locale.ROOT, "#%06X",
            Color.hsv(hue, nextSaturation, nextBrightness).toArgb() and 0xFFFFFF
        )
    }

    SettingsBackdropContainer(Modifier.fillMaxSize()) { backdrop ->
        SettingsPageLayout(title = "主题色", onBack = onBack, backdrop = backdrop) {
            SettingsSection(
                title = "默认颜色",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (appUiTheme == AppUiTheme.MIUIX) {
                        ThemeColorSwatch("默认", Color(0xFF3482FF), themeColor == DEFAULT_THEME_COLOR) {
                            viewModel.setThemeColor(DEFAULT_THEME_COLOR)
                        }
                    }
                    ThemeColorSwatch("系统", MaterialTheme.colorScheme.primary, themeColor == null) {
                        viewModel.setThemeColor(null)
                    }
                    themeColorChoices.forEach { choice ->
                        ThemeColorSwatch(choice.name, choice.color, choice.value.equals(themeColor, true)) {
                            viewModel.setThemeColor(choice.value)
                        }
                    }
                }
            }

            SettingsSection(
                title = "自定义颜色",
                modifier = Modifier.padding(horizontal = 16.dp),
                backdrop = backdrop
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SaturationBrightnessPalette(hue, saturation, brightness, updatePalette)
                    Text("色相", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(12.dp)
                                .clip(SmoothRoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient((0..6).map {
                                    Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f)
                                }))
                        )
                        AppSlider(
                            value = hue,
                            onValueChange = {
                                hue = it
                                hexInput = String.format(
                                    Locale.ROOT, "#%06X", Color.hsv(it, saturation, brightness).toArgb() and 0xFFFFFF
                                )
                            },
                            valueRange = 0f..360f,
                            backdrop = backdrop,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "色相" }
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(SmoothRoundedCornerShape(16.dp))
                                .background(draftColor).semantics { contentDescription = "颜色预览 $draftHex" }
                        )
                        AppTextField(
                            value = hexInput,
                            onValueChange = { value ->
                                hexInput = value
                                if (value.matches(Regex("#[0-9a-fA-F]{6}"))) {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(value), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    brightness = hsv[2]
                                }
                            },
                            label = "颜色值",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AppButton(
                        onClick = { viewModel.setThemeColor("#FF${hexInput.drop(1).uppercase(Locale.ROOT)}") },
                        enabled = validHex,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("应用") }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(SmoothRoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(SmoothRoundedCornerShape(16.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(
                Icons.Rounded.Check, contentDescription = "已选择",
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White
            )
        }
        Text(name, style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SaturationBrightnessPalette(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onSelected: (Float, Float) -> Unit
) {
    val latestOnSelected by androidx.compose.runtime.rememberUpdatedState(onSelected)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(200.dp)
            .clip(SmoothRoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "取色板"
                stateDescription = "饱和度 ${(saturation * 100).toInt()}%，亮度 ${(brightness * 100).toInt()}%"
                customActions = listOf(
                    CustomAccessibilityAction("增加饱和度") {
                        onSelected((saturation + 0.05f).coerceAtMost(1f), brightness)
                        true
                    },
                    CustomAccessibilityAction("降低饱和度") {
                        onSelected((saturation - 0.05f).coerceAtLeast(0f), brightness)
                        true
                    },
                    CustomAccessibilityAction("增加亮度") {
                        onSelected(saturation, (brightness + 0.05f).coerceAtMost(1f))
                        true
                    },
                    CustomAccessibilityAction("降低亮度") {
                        onSelected(saturation, (brightness - 0.05f).coerceAtLeast(0f))
                        true
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun select(position: Offset) {
                        latestOnSelected(
                            (position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                            (1f - position.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                        )
                    }
                    select(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        select(change.position)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - brightness) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.4f), 10.dp.toPx(), center, style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, 10.dp.toPx(), center, style = Stroke(2.dp.toPx()))
    }
}
