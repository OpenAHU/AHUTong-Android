package com.ahu.ahutong.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.ahu.ahutong.data.model.UiStyle

/**
 * 全局 UI 风格模式（Original / Liquid Glass / RadiantUI）。
 * 由 AHUTheme 从 PreferencesViewModel.uiStyle 提供。
 */
val LocalUiStyle = compositionLocalOf { UiStyle.LIQUID_GLASS }

/** 是否处于「曜光 RadiantUI」整合格局（新主页 + 学习通日历提级 + 扁平导航）。 */
val isRadiantUi: Boolean
    @Composable get() = LocalUiStyle.current == UiStyle.RADIANT_UI