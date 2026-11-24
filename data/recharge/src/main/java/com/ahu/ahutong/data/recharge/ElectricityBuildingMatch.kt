package com.ahu.ahutong.data.recharge

/**
 * 本机宿舍楼栋与电控缴费楼栋选项的对应关系。
 *
 * 宿舍信息来自智慧安大（「榴园2号楼」「11号楼」），缴费系统的楼栋名由后台维护
 * （「榴园二号楼」「十一号楼」），数字写法与楼栋后缀都可能不同，因此统一归一成
 * 「园区名 + 阿拉伯数字」再比。
 *
 * 命中分两级：先要求归一化结果完全相同；都不相同时才退一步，允许一方多带前缀
 * （「榴园2」↔「磬苑榴园2」），但楼栋编号必须严格相同，避免「榴园2」误配「榴园21」。
 */
object ElectricityBuildingMatch {

    /** 唯一命中时的楼栋选项名；多候选或无候选返回 null，交回用户自己选。 */
    fun match(dormBuilding: String?, options: List<String>): String? =
        candidates(dormBuilding, options).singleOrNull()

    /** 所有可能命中的楼栋选项名，顺序与入参一致。 */
    fun candidates(dormBuilding: String?, options: List<String>): List<String> {
        val target = key(dormBuilding) ?: return emptyList()
        val keyed = options.mapNotNull { name -> key(name)?.let { name to it } }
        val exact = keyed.filter { it.second == target }
        if (exact.isNotEmpty()) return exact.map { it.first }
        val number = target.filter { it.isDigit() }
        if (number.isEmpty()) return emptyList()
        return keyed.filter { (_, key) ->
            key.filter { it.isDigit() } == number && (key.contains(target) || target.contains(key))
        }.map { it.first }.distinct()
    }

    /** 归一化：去空白、去校区与楼栋后缀，中文数字转阿拉伯数字。 */
    internal fun key(raw: String?): String? {
        val text = raw?.replace(WHITESPACE, "")?.replace("校区", "").orEmpty()
        if (text.isEmpty()) return null
        val stripped = text.replace(SUFFIX, "")
        return if (stripped.isEmpty()) null else digits(stripped)
    }

    private fun digits(text: String): String =
        text.replace(TENS) { found ->
            val tens = CN_DIGITS[found.groupValues[1].firstOrNull()] ?: 1
            val ones = CN_DIGITS[found.groupValues[2].firstOrNull()] ?: 0
            (tens * 10 + ones).toString()
        }.map { char -> CN_DIGITS[char]?.digitToChar() ?: char }.joinToString("")

    private val WHITESPACE = Regex("\\s+")

    /** 「第」「号」「栋」「幢」「楼」只表示楼栋，不参与比对。 */
    private val SUFFIX = Regex("[第号栋幢楼]")

    private val TENS = Regex("([一二三四五六七八九])?十([一二三四五六七八九])?")

    private val CN_DIGITS = mapOf(
        '〇' to 0,
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9
    )
}
