package com.ahu.ahutong.data.laundry

/**
 * 龙河（趣智校园）纯逻辑：楼栋 ↔ 机房匹配、机器名解析、状态换算。
 *
 * 机房名就是楼栋名（402栋 / 301幢洗衣房 / 205栋1楼洗衣机 / 博士1楼），
 * 机器名里混着楼栋、楼层、机位（「602号1楼203栋」「402幢1楼155号」两种顺序都有），
 * 所以按「3 位楼栋号相等」匹配机房，按 `xx号` 取机位、`xx楼` 取楼层。
 */
object QiekjLaundryMatcher {

    private const val STATUS_IDLE = 10
    private const val STATUS_IN_USE = 20

    private val CAMPUS_PREFIXES =
        listOf("安徽大学", "龙河校区", "磬苑校区", "金寨路校区", "龙河", "磬苑", "校区")

    /** 楼栋 / 机房名里的 3 位编号（402栋 → 402，301幢洗衣房 → 301）。 */
    private val BUILDING_NO = Regex("(\\d{3})\\s*[幢栋]")

    /** 机器名里的楼栋原文（「205幢084号」→ 205幢）。 */
    private val BUILDING_LABEL = Regex("\\d{3}\\s*[幢栋]")

    /** 机位号：紧跟在「号」前的数字（「602号1楼203栋」→ 602，「402幢1楼155号」→ 155）。 */
    private val SLOT = Regex("(\\d+)号")

    /** 楼层：`xx楼`/`xx层`/`xxF`，楼号有中文写法（「300幢二楼536号」→ 2）。 */
    private val FLOOR = Regex("([0-9一二三四五六七八九十]{1,3})\\s*[楼层Ff]")

    private val CN_DIGITS = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    private val HOUR = Regex("(\\d+)\\s*(小时|时)")
    private val MINUTE = Regex("(\\d+)\\s*分")
    private val SECOND = Regex("(\\d+)\\s*秒")

    /** 宿舍楼栋 → 机房。唯一命中才算自动定位，多候选交给用户选，认不出不猜。 */
    fun match(building: String?, points: List<String>): LaundryParkMatch {
        if (points.isEmpty()) return LaundryParkMatch.None
        val key = buildingOf(building) ?: return LaundryParkMatch.None
        val hits = points.indices.filter { buildingOf(points[it]) == key }
        return when {
            hits.size == 1 -> LaundryParkMatch.Hit(hits.first())
            hits.size > 1 -> LaundryParkMatch.Multiple(hits)
            else -> LaundryParkMatch.None
        }
    }

    fun buildingLabel(goodsName: String?): String? =
        goodsName?.let { BUILDING_LABEL.find(it)?.value?.replace(" ", "") }

    /** 楼栋分组：编号楼栋优先（205幢 → 205 / 205幢），没有编号的退到中文楼名（外招南3楼 → 外招南 / 外招南楼）。 */
    data class BuildingGroup(val key: String, val label: String)

    /** 中文楼名 + 楼层：「外招南3楼412号」→ 外招南。 */
    private val NAMED_BUILDING = Regex("([\\u4e00-\\u9fa5]{2,})[0-9一二三四五六七八九十]{1,3}\\s*[楼层]")

    /**
     * 整店下发机型的楼栋分组键：洗鞋机没有机房分组，
     * 只能从机器名里认出所属楼栋，才能像洗衣机那样按楼栋进。
     */
    fun buildingGroupOf(goodsName: String?): BuildingGroup? {
        val no = buildingOf(goodsName)
        if (no != null) return BuildingGroup(no, buildingLabel(goodsName) ?: "${no}幢")
        val text = goodsName?.replace(Regex("[\\s\\u3000]"), "") ?: return null
        val named = NAMED_BUILDING.find(text)?.groupValues?.get(1) ?: return null
        return BuildingGroup(named, "${named}楼")
    }

    /** 整店机器 → 楼栋列表：编号楼栋按号升序在前，中文楼名在后。 */
    fun buildingGroupsOf(names: List<String?>): List<BuildingGroup> =
        names.mapNotNull { buildingGroupOf(it) }
            .distinctBy { it.key }
            .sortedBy { if (it.key.all { ch -> ch.isDigit() }) "0${it.key}" else "1${it.label}" }

    fun slotOf(goodsName: String?): String? = goodsName?.let { SLOT.find(it)?.groupValues?.get(1) }

    fun floorOf(goodsName: String?): Int? = goodsName
        ?.let { FLOOR.find(it)?.groupValues?.get(1) }
        ?.let { cnNumber(it) }

    /** 「十一」→ 11、「二」→ 2、「3」→ 3；认不出返回 null。 */
    private fun cnNumber(token: String): Int? {
        token.toIntOrNull()?.let { return it }
        var tens = 0
        var unit = 0
        token.forEach { ch ->
            when (ch) {
                '十' -> {
                    tens = (if (unit == 0) 1 else unit) * 10
                    unit = 0
                }
                else -> unit = CN_DIGITS[ch] ?: return null
            }
        }
        return tens + unit
    }

    /** 剩余时间文案（「20分钟」「49秒」）→ 毫秒；认不出返回 null。 */
    fun remainingMillis(text: String?): Long? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        var millis = 0L
        var matched = false
        HOUR.find(raw)?.let {
            millis += it.groupValues[1].toLong() * 3_600_000L
            matched = true
        }
        MINUTE.find(raw)?.let {
            millis += it.groupValues[1].toLong() * 60_000L
            matched = true
        }
        SECOND.find(raw)?.let {
            millis += it.groupValues[1].toLong() * 1_000L
            matched = true
        }
        return if (matched) millis else null
    }

    /**
     * 平台状态 → 占用。10 空闲；20 使用中（剩余时间实时下发）；
     * 30/40 实测无剩余时间，按「暂不可用」展示，不猜成故障或空闲。
     */
    fun occupancyOf(
        status: Int?,
        endUseTimeValue: String?,
        clockOffsetMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): LaundryOccupancy = when (status) {
        STATUS_IDLE -> LaundryOccupancy.Idle
        STATUS_IN_USE -> {
            val remaining = remainingMillis(endUseTimeValue)
            if (remaining == null) {
                LaundryOccupancy.Unknown("使用中")
            } else {
                LaundryOccupancy.InUse(
                    endAtMillis = nowMillis + clockOffsetMillis + remaining,
                    clockOffsetMillis = clockOffsetMillis,
                    serviceName = null,
                    price = null
                )
            }
        }
        else -> LaundryOccupancy.Unknown("暂不可用")
    }

    /** 去校区前缀、去空白、全角转半角后的 3 位楼栋号（「300幢2楼408号」→ 300）。 */
    fun buildingOf(raw: String?): String? {
        var text = raw?.replace(Regex("[\\s\\u3000]"), "") ?: return null
        CAMPUS_PREFIXES.forEach { prefix -> text = text.removePrefix(prefix) }
        val normalized = buildString {
            text.forEach { ch ->
                append(if (ch in '０'..'９') '0' + (ch - '０') else ch)
            }
        }
        return BUILDING_NO.find(normalized)?.groupValues?.get(1)
            ?: normalized.takeIf { it.length == 3 && it.all { ch -> ch in '0'..'9' } }
    }
}
