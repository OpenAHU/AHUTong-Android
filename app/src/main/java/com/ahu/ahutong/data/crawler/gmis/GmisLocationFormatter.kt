package com.ahu.ahutong.data.crawler.gmis

/** Campus is always Jianghuai for GMIS students; keep the building and room distinguishable. */
internal object GmisLocationFormatter {
    private val buildingNames = linkedMapOf(
        "教学主楼二楼阶梯" to "主楼二阶",
        "教学主楼北阶" to "主楼北阶",
        "教学主楼" to "主楼"
    )
    private val buildingPattern = Regex(buildingNames.keys.joinToString("|") { Regex.escape(it) })
    private val campus = Regex("[（(]\\s*江淮\\s*[）)]")

    fun display(location: String): String =
        location.replace(campus, "")
            .replace(Regex("\\s+"), "")
            .replace(buildingPattern) { buildingNames.getValue(it.value) }
            .trim()
}
