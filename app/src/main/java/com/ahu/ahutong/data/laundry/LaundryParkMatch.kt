package com.ahu.ahutong.data.laundry

/**
 * 宿舍楼栋 → 台账园区的匹配。
 *
 * 台账园区粒度不统一：榴园 / 11号楼 / 南7号楼 整栋算一个园区，兰园却拆成 A/B/C/D 栋，
 * 所以「兰园2号楼」这种楼栋名只能给出一组候选，由用户选一次再记住。匹配顺序：
 * 楼栋名命中园区名 → 楼栋名里的「XX园」命中园区名前缀 → 交给用户，不猜。
 */
sealed interface LaundryParkMatch {

    /** 唯一命中。 */
    data class Hit(val index: Int) : LaundryParkMatch

    /** 能定位到园区，但台账在该园区下有多个洗衣房（如兰园 A/B/C/D 栋）。 */
    data class Multiple(val indices: List<Int>) : LaundryParkMatch

    /** 没有住宿信息，或楼栋名认不出园区。 */
    data object None : LaundryParkMatch
}

object LaundryParkMatcher {

    /** 楼栋名去掉园区名后剩下的部分：2号楼 / 3栋 / 12 / 一号楼。 */
    private val REMAINDER = Regex("^[0-9一二三四五六七八九十]+(号楼|号|栋|楼)?.*$")

    /** 楼栋名里的「XX园」。 */
    private val AREA = Regex("[\\u4e00-\\u9fa5]{1,3}园")

    private val CAMPUS_PREFIXES =
        listOf("安徽大学", "磬苑校区", "龙河校区", "金寨路校区", "磬苑", "龙河", "金寨路", "校区")

    fun match(building: String?, parks: List<String>): LaundryParkMatch {
        if (parks.isEmpty()) return LaundryParkMatch.None
        val name = normalize(building) ?: return LaundryParkMatch.None
        val parkNames = parks.map { normalize(it).orEmpty() }

        // 1) 园区名落在楼栋名里：榴园2号楼→榴园、11号楼→11号楼、兰园B栋→兰园B栋
        val prefixHits = parkNames.indices.filter { index ->
            val park = parkNames[index]
            park.isNotEmpty() &&
                (name == park || (name.startsWith(park) && REMAINDER.matches(name.removePrefix(park))))
        }
        val longest = prefixHits.maxOfOrNull { parkNames[it].length } ?: 0
        if (longest > 0) {
            val best = prefixHits.filter { parkNames[it].length == longest }
            return if (best.size == 1) LaundryParkMatch.Hit(best.first()) else LaundryParkMatch.Multiple(best)
        }

        // 2) 退到园区名：兰园2号楼 → 兰园A/B/C/D栋，多候选不猜
        val area = AREA.find(name)?.value ?: return LaundryParkMatch.None
        val areaHits = parkNames.indices.filter { parkNames[it].startsWith(area) }
        return when {
            areaHits.size == 1 -> LaundryParkMatch.Hit(areaHits.first())
            areaHits.size > 1 -> LaundryParkMatch.Multiple(areaHits)
            else -> LaundryParkMatch.None
        }
    }

    /** 去校区前缀、去「宿舍」后缀、全角转半角、统一小写；认不出内容返回 null。 */
    private fun normalize(raw: String?): String? {
        var text = raw?.replace(Regex("[\\s\u3000]"), "") ?: return null
        CAMPUS_PREFIXES.forEach { prefix -> text = text.removePrefix(prefix) }
        text = text.removeSuffix("宿舍楼").removeSuffix("宿舍")
        if (text.isEmpty()) return null
        return buildString {
            text.forEach { ch ->
                append(
                    when (ch) {
                        in '０'..'９' -> '0' + (ch - '０')
                        in 'Ａ'..'Ｚ' -> 'A' + (ch - 'Ａ')
                        in 'ａ'..'ｚ' -> 'a' + (ch - 'ａ')
                        else -> ch
                    }
                )
            }
        }.lowercase()
    }
}
