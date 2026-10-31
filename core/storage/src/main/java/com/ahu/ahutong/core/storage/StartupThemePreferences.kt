package com.ahu.ahutong.core.storage

import com.ahu.ahutong.data.model.AppThemeMode
import com.ahu.ahutong.data.model.AppUiTheme

/**
 * 启动期的主题镜像。
 *
 * DataStore 是异步的，而主题必须在第一帧之前就位，所以启动路径读的是这份同步副本。
 */
data class StartupThemePreferences(
    val appUiTheme: AppUiTheme,
    val themeColor: String?,
    val themeMode: AppThemeMode,
    /** Theme Park 组件槽位覆盖（序列化字符串，解析在 core/designsystem 的 ComponentSlots）。 */
    val slotOverrides: String = "",
    val hapticFeedbackEnabled: Boolean = false
)

