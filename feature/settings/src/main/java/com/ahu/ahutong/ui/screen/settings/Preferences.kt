package com.ahu.ahutong.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahu.ahutong.feature.settings.R
import com.ahu.ahutong.ui.components.AhuDialog
import com.ahu.ahutong.ui.components.AhuInsetCard
import com.ahu.ahutong.ui.components.AhuPageHeader
import com.ahu.ahutong.ui.components.AhuPrimaryButton
import com.ahu.ahutong.ui.components.AhuScreen
import com.ahu.ahutong.ui.components.LiquidToggle
import com.ahu.ahutong.ui.shape.SmoothRoundedCornerShape
import com.ahu.ahutong.ui.state.PreferencesViewModel
import com.ahu.ahutong.ui.theme.AhuColors
import com.ahu.ahutong.ui.theme.AhuDimens
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop

@Composable
fun Preferences() {
    val preferencesViewModel: PreferencesViewModel = hiltViewModel()
    val context = LocalContext.current
    var isRequestingPermission by remember { mutableStateOf(false) }

    val showQRCode by preferencesViewModel.showQRCode.collectAsState()
    val useLiquidGlass by preferencesViewModel.useLiquidGlass.collectAsState()
    val courseReminderEnabled by preferencesViewModel.courseReminderEnabled.collectAsState()
    val courseReminderLiveCountdownEnabled by preferencesViewModel.courseReminderLiveCountdownEnabled.collectAsState()

    val cardColor = AhuColors.card
    val backdrop = rememberCanvasBackdrop { drawRect(cardColor) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isRequestingPermission = false
        if (granted) {
            preferencesViewModel.setCourseReminderEnabled(true)
            preferencesViewModel.courseReminderActions.reschedule(context)
        } else {
            preferencesViewModel.setCourseReminderEnabled(false)
            Toast.makeText(context, "未授予通知权限，无法开启课前提醒", Toast.LENGTH_SHORT).show()
        }
    }

    AhuScreen {
        AhuPageHeader(
            title = stringResource(id = R.string.preferences),
            titleStyle = MaterialTheme.typography.headlineLarge,
        )
        /*
        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            onClick = { preferencesViewModel.setShowQRCode(!preferencesViewModel.showQRCode.value) },
        ) {
            Text(text = "主页", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "主页默认显示支付二维码")
                LiquidToggle(
                    selected = { showQRCode },
                    onSelect = { preferencesViewModel.setShowQRCode(!preferencesViewModel.showQRCode.value) },
                    backdrop = backdrop
                )
            }
        }
        */

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            onClick = {
                if (!courseReminderEnabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        if (!isRequestingPermission) {
                            isRequestingPermission = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        preferencesViewModel.setCourseReminderEnabled(true)
                        preferencesViewModel.courseReminderActions.reschedule(context)
                    }
                } else {
                    preferencesViewModel.setCourseReminderEnabled(false)
                    preferencesViewModel.courseReminderActions.cancel(context)
                }
            },
        ) {
            Text(text = "通知", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "课前提醒")
                    Text(
                        text = "上课前 10 分钟提醒下一节课",
                        color = AhuColors.onSurface.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LiquidToggle(
                    selected = { courseReminderEnabled },
                    onSelect = { enabled ->
                        if (enabled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                if (!isRequestingPermission) {
                                    isRequestingPermission = true
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                preferencesViewModel.setCourseReminderEnabled(true)
                                preferencesViewModel.courseReminderActions.reschedule(context)
                            }
                        } else {
                            preferencesViewModel.setCourseReminderEnabled(false)
                            preferencesViewModel.courseReminderActions.cancel(context)
                        }
                    },
                    backdrop = backdrop
                )
            }
        }

        AhuInsetCard(cornerRadius = AhuDimens.CardCornerMedium) {
            Text(text = "通知增强", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "课前倒计时岛卡提醒（实验性）")
                    Text(
                        text = "仅部分系统支持 需同时开启课前提醒",
                        color = AhuColors.onSurface.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                LiquidToggle(
                    selected = {
                        courseReminderLiveCountdownEnabled && Build.VERSION.SDK_INT >= 36
                    },
                    onSelect = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT < 36) {
                            Toast.makeText(
                                context,
                                "当前 Android 版本暂不支持岛卡提醒",
                                Toast.LENGTH_SHORT
                            ).show()
                            preferencesViewModel.setCourseReminderLiveCountdownEnabled(false)
                        } else {
                            preferencesViewModel.setCourseReminderLiveCountdownEnabled(enabled)
                            if (!enabled) {
                                preferencesViewModel.courseReminderActions.cancelActiveReminder(context)
                            }
                        }
                    },
                    backdrop = backdrop
                )
            }
            TextButton(
                onClick = {
                    if (Build.VERSION.SDK_INT < 36) {
                        Toast.makeText(
                            context,
                            "当前 Android 版本暂不支持岛卡提醒",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val promotionIntent =
                            preferencesViewModel.courseReminderActions.createPromotionSettingsIntent(context)
                        val fallbackIntent =
                            preferencesViewModel.courseReminderActions.createNotificationSettingsIntent(context)
                        runCatching {
                            context.startActivity(promotionIntent)
                        }.getOrElse {
                            context.startActivity(fallbackIntent)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(text = "管理系统岛卡权限")
            }
        }

        AhuInsetCard(
            cornerRadius = AhuDimens.CardCornerMedium,
            onClick = { preferencesViewModel.setUseLiquidGlass(!preferencesViewModel.useLiquidGlass.value) },
        ) {
            Text(text = "液态玻璃", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "启用液态玻璃效果")
                LiquidToggle(
                    selected = { useLiquidGlass },
                    onSelect = { preferencesViewModel.setUseLiquidGlass(!preferencesViewModel.useLiquidGlass.value) },
                    backdrop = backdrop
                )
            }
        }

        ThemeColorSelector(preferencesViewModel)
    }
}

@Composable
fun ThemeColorSelector(
    viewModel: PreferencesViewModel,
) {
    val themeColor by viewModel.themeColor.collectAsState()
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customColorInput by remember { mutableStateOf("") }

    val colors = listOf(
        null to "默认",
        "#FF4A90E2" to "极光蓝",
        "#FFE07A9F" to "樱花粉",
        "#FFF4A261" to "落日橙",
        "#FF5C6BC0" to "靛夜蓝",
        "#FF6A994E" to "苔藓绿",
        "#FF9B7EDE" to "薰衣草紫",
        "#FFD64550" to "绯红花",
        "#FF4CC9F0" to "天空青",
        "#FF2E8B57" to "森林翡翠",
        "#FF6A4C93" to "午夜紫",
        "#FFFF6F61" to "珊瑚粉",
        "#FF7ED9C3" to "北极薄荷"
    )

    val isCustomColor = themeColor != null && colors.none { it.first == themeColor }

    if (showCustomColorDialog) {
        AhuDialog(
            onDismissRequest = { showCustomColorDialog = false },
            scrollable = false,
        ) {
            Text(
                text = "自定义主题颜色",
                modifier = Modifier.padding(horizontal = 24.dp),
                color = AhuColors.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "请输入ARGB Hex颜色代码 (例如 #FF007FAC)",
                    color = AhuColors.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = customColorInput,
                    onValueChange = { customColorInput = it },
                    label = { Text("Hex Color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SmoothRoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AhuColors.primaryAction,
                        unfocusedBorderColor = AhuColors.onSurface.copy(alpha = 0.4f),
                        focusedLabelColor = AhuColors.primaryAction,
                        unfocusedLabelColor = AhuColors.onSurface.copy(alpha = 0.4f),
                        cursorColor = AhuColors.primaryAction,
                        focusedTextColor = AhuColors.onSurface,
                        unfocusedTextColor = AhuColors.onSurface
                    )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "取消",
                    modifier = Modifier
                        .clickable { showCustomColorDialog = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = AhuColors.primaryAction,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                AhuPrimaryButton(
                    text = "确定",
                    onClick = {
                        try {
                            android.graphics.Color.parseColor(customColorInput)
                            viewModel.setThemeColor(customColorInput)
                            showCustomColorDialog = false
                        } catch (_: Exception) {
                            // Invalid color, ignore
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                )
            }
        }
    }

    AhuInsetCard(cornerRadius = AhuDimens.CardCornerMedium) {
        Text(text = "主题颜色", style = MaterialTheme.typography.headlineSmall)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    customColorInput = if (isCustomColor) themeColor ?: "" else ""
                    showCustomColorDialog = true
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .background(
                            if (isCustomColor && themeColor != null) Color(
                                android.graphics.Color.parseColor(themeColor)
                            ) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCustomColor) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Custom",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "自定义",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            colors.forEach { (colorHex, name) ->
                val isSelected = themeColor == colorHex
                val color = if (colorHex != null) {
                    Color(android.graphics.Color.parseColor(colorHex))
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        colorResource(id = android.R.color.system_accent1_500)
                    } else {
                        Color(0xFF007FAC)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.setThemeColor(colorHex) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(SmoothRoundedCornerShape(12.dp))
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = Color.White
                            )
                        }
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            AhuColors.primaryAction
                        } else {
                            AhuColors.onSurface
                        }
                    )
                }
            }
        }
    }
}
