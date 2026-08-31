package com.ahu.ahutong.data.model

/**
 * 整套 UI 的风格模式。
 *
 * - ORIGINAL：经典原样式（无玻璃、原始组件与交互）
 * - LIQUID_GLASS：液态玻璃风格（Apple 风格玻璃控件 + 浮动导航）
 * - RADIANT_UI：曜光 RadiantUI（本项目新主页 + 玻璃 + 扁平导航的整合风格）
 */
enum class UiStyle(val storageValue: String) {
    ORIGINAL("original"),
    LIQUID_GLASS("liquid_glass"),
    RADIANT_UI("radiant_ui");

    /** 是否启用液态玻璃底层（玻璃是 RADIANT_UI 的基础材质之一）。 */
    val usesGlass: Boolean
        get() = this != ORIGINAL

    companion object {
        fun fromStorage(value: String?): UiStyle =
            entries.firstOrNull { it.storageValue == value } ?: RADIANT_UI
    }
}