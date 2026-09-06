package com.ahu.ahutong.data.model

enum class AppUiTheme(val storageValue: String, val displayName: String) {
    MATERIAL("material", "Material"),
    MIUIX("miuix", "Miuix"),
    LIQUID_GLASS("liquid_glass", "LiquidGlass"),
    RADIANT("radiant_ui", "RadiantUI");

    val usesLiquidGlass: Boolean
        get() = this == LIQUID_GLASS || this == RADIANT

    companion object {
        fun fromStorage(
            value: String?,
            legacyUseLiquidGlass: Boolean?,
            legacyUiStyle: String? = null
        ): AppUiTheme =
            entries.firstOrNull { it.storageValue == value }
                ?: when (legacyUiStyle) {
                    "original" -> MATERIAL
                    "liquid_glass" -> LIQUID_GLASS
                    "radiant_ui" -> RADIANT
                    else -> null
                }
                ?: if (legacyUseLiquidGlass == false) MATERIAL else RADIANT
    }
}
