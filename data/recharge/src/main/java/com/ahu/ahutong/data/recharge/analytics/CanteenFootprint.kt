package com.ahu.ahutong.data.recharge.analytics

import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.debug.DebugClock
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * 干饭足迹统计引擎（必吃榜个人端 V1）。
 *
 * 口径（2026-10-06 用户拍板）：
 * - 只统计正餐：午餐 10:30-14:00，晚餐 16:30-22:30（夜宵并入晚餐）；早餐不进任何数字。
 * - 餐次≠笔数：同一天同一终端同一餐段内、间隔 ≤5 分钟的连续消费合并为一餐（套餐+饮料分开刷很常见）。
 * - 食堂判定与名称归一复用 CardAnalytics 的 internal 助手（extractCanteenName 等），口径单点。
 * - 纯函数、无 IO、线程安全（手写日期解析沿用 CardAnalytics）。
 *
 * 窗口粒度来自 TurnoverRecord.locationName（终端位置码，如 "77-139"）；
 * 窗口名由映射表 windowNames 提供，未命中时 UI 层展示「XX 号窗 · 求认领」。
 */

enum class MealSlot(val label: String) {
    LUNCH("午餐"), DINNER("晚餐")
}

/** 合并后的「一餐」。 */
data class FootprintMeal(
    val terminal: String?,
    val canteen: String,
    /** 食堂+楼层（如「榴园一楼」）；无楼层信息时退化为食堂名。 */
    val location: String,
    val slot: MealSlot,
    val startTime: Date,
    val totalFen: Long,
    val logicalDay: String
)

data class WindowStat(
    val terminal: String,
    /** 映射表命中的窗口名；null = 未收录。 */
    val windowName: String?,
    val canteen: String,
    /** 食堂+楼层（该窗口消费记录里出现最多的 location）。 */
    val location: String,
    val mealCount: Int,
    val totalFen: Long,
    val avgFen: Long,
    val lastDay: String,
    /** 截至最近一次光顾的连续天数（每天都有 ≥1 餐）。 */
    val currentStreak: Int
)

data class CanteenFootprint(
    val mealCount: Int,
    val totalFen: Long,
    val avgFen: Long,
    /** 有终端码的去重窗口数（映射表覆盖目标）。 */
    val windowCount: Int,
    /** 带终端码的餐数（÷mealCount = 终端码覆盖率，枢纽验证指标）。 */
    val coveredMeals: Int,
    /** GitHub 式热力矩阵：列=周（周一到周日 7 格），值=当天正餐数（0..N）。 */
    val weeks: List<List<Int>>,
    /** 每列周一的日期（yyyy-MM-dd），用于悬浮标签。 */
    val weekStartDays: List<String>,
    val topWindows: List<WindowStat>,
    /** 本周首次光顾的窗口数（本周尝新）。 */
    val newWindowsThisWeek: Int,
    /** 均价最低的窗口（≥3 餐才参评）。 */
    val cheapestWindow: WindowStat?,
    /** 周六周日的正餐总数（周末坚守）。 */
    val weekendMealCount: Int
)

/** 唯一入口。windowNames = 终端码→窗口名映射表（V1 可为空表，全部「求认领」）。 */
fun List<TurnoverRecord>.toCanteenFootprint(
    windowNames: Map<String, String> = emptyMap(),
    weeksBack: Int = 18,
    today: Date = DebugClock.nowDate()
): CanteenFootprint {
    val meals = mergeMeals()
    if (meals.isEmpty()) {
        return CanteenFootprint(
            mealCount = 0, totalFen = 0, avgFen = 0, windowCount = 0, coveredMeals = 0,
            weeks = emptyList(), weekStartDays = emptyList(), topWindows = emptyList(),
            newWindowsThisWeek = 0, cheapestWindow = null, weekendMealCount = 0
        )
    }

    val totalFen = meals.sumOf { it.totalFen }
    val covered = meals.count { !it.terminal.isNullOrBlank() }

    // —— 热力矩阵：周一对齐的列，右端收尾到本周 ——
    val todayCal = Calendar.getInstance(Locale.CHINA).apply { time = today }
    val thisMonday = (todayCal.clone() as Calendar).apply {
        var delta = get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
        if (delta < 0) delta += 7
        add(Calendar.DAY_OF_MONTH, -delta)
    }
    val firstDay = meals.first().logicalDay
    val firstMonday = (thisMonday.clone() as Calendar).apply {
        add(Calendar.WEEK_OF_YEAR, -weeksBack + 1)
        // 如果数据起点比 weeksBack 更早，再往前扩到数据起点所在周
        val firstCal = Calendar.getInstance(Locale.CHINA).apply {
            val p = firstDay.split("-")
            clear(); set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        }
        while (after(firstCal)) add(Calendar.WEEK_OF_YEAR, -1)
    }
    val mealsByDay = meals.groupBy { it.logicalDay }.mapValues { it.value.size }
    val weeks = mutableListOf<List<Int>>()
    val weekStartDays = mutableListOf<String>()
    val cursor = firstMonday.clone() as Calendar
    while (!cursor.after(thisMonday)) {
        val col = (0..6).map { offset ->
            val dayCal = (cursor.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
            if (dayCal.after(todayCal)) 0 else mealsByDay[formatDayKey(dayCal)] ?: 0
        }
        weeks.add(col)
        weekStartDays.add(formatDayKey(cursor))
        cursor.add(Calendar.WEEK_OF_YEAR, 1)
    }

    // —— 窗口榜（仅带终端码的餐参评） ——
    val byTerminal = meals.filter { !it.terminal.isNullOrBlank() }.groupBy { it.terminal!! }
    val topWindows = byTerminal.map { (terminal, list) ->
        val sorted = list.sortedBy { it.startTime }
        WindowStat(
            terminal = terminal,
            windowName = windowNames[terminal],
            canteen = sorted.last().canteen,
            location = list.groupingBy { it.location }.eachCount().maxByOrNull { it.value }!!.key,
            mealCount = list.size,
            totalFen = list.sumOf { it.totalFen },
            avgFen = list.sumOf { it.totalFen } / list.size,
            lastDay = sorted.last().logicalDay,
            currentStreak = currentStreak(list.map { it.logicalDay }.toSet(), sorted.last().logicalDay)
        )
    }.sortedByDescending { it.mealCount }

    // —— 徽章数据 ——
    val weekStartKey = formatDayKey(thisMonday)
    val firstSeenDay = byTerminal.mapValues { (_, list) -> list.minOf { it.logicalDay } }
    val newThisWeek = firstSeenDay.count { it.value >= weekStartKey }
    val weekend = meals.count {
        val cal = Calendar.getInstance(Locale.CHINA).apply { time = it.startTime }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    return CanteenFootprint(
        mealCount = meals.size,
        totalFen = totalFen,
        avgFen = totalFen / meals.size,
        windowCount = byTerminal.size,
        coveredMeals = covered,
        weeks = weeks,
        weekStartDays = weekStartDays,
        topWindows = topWindows,
        newWindowsThisWeek = newThisWeek,
        cheapestWindow = topWindows.filter { it.mealCount >= 3 }.minByOrNull { it.avgFen },
        weekendMealCount = weekend
    )
}

/** 正餐时段过滤 + 同终端同餐段 ≤5 分钟连续消费合并为一餐。 */
private fun List<TurnoverRecord>.mergeMeals(): List<FootprintMeal> {
    val candidates = mapNotNull { record ->
        if (!record.isExpenseRecord()) return@mapNotNull null
        val time = parseDateTime(record.effectdateStr) ?: return@mapNotNull null
        val slot = mealSlotOf(time) ?: return@mapNotNull null
        val raw = record.merchantText()
        val canteen = extractCanteenName(raw) ?: return@mapNotNull null
        FootprintMeal(
            terminal = record.locationName?.trim()?.takeIf { it.isNotEmpty() },
            canteen = canteen,
            location = canteen + (extractFloor(raw) ?: ""),
            slot = slot,
            startTime = time,
            totalFen = record.tranamt,
            logicalDay = logicalDayKey(time)
        )
    }
    val result = mutableListOf<FootprintMeal>()
    candidates
        .groupBy { Triple(it.logicalDay, it.terminal, it.slot) }
        .forEach { (_, group) ->
            var current: FootprintMeal? = null
            for (meal in group.sortedBy { it.startTime }) {
                val prev = current
                if (prev != null && meal.startTime.time - prev.startTime.time <= MERGE_GAP_MS) {
                    // 同一餐：金额累加、保留首笔时间
                    current = prev.copy(totalFen = prev.totalFen + meal.totalFen)
                } else {
                    prev?.let { result.add(it) }
                    current = meal
                }
            }
            current?.let { result.add(it) }
        }
    return result.sortedBy { it.startTime }
}

private const val MERGE_GAP_MS = 5 * 60 * 1000L

/** 楼层提取：原始商户文本里的「一楼/二楼/负一楼」等；无则 null（extractCanteenName 会把楼层剥掉）。 */
internal fun extractFloor(text: String): String? =
    Regex("(负一|[一二三四五六七八])楼").find(text)?.value

/** 正餐时段：午 10:30-14:00，晚 16:30-22:30（夜宵并入晚餐）；其余时刻返回 null。 */
private fun mealSlotOf(time: Date): MealSlot? {
    val cal = Calendar.getInstance(Locale.CHINA).apply { this.time = time }
    val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    return when (minutes) {
        in 630 until 840 -> MealSlot.LUNCH        // 10:30-14:00
        in 990 until 1350 -> MealSlot.DINNER      // 16:30-22:30
        else -> null
    }
}

/** 从 lastDay 往回数每天都有餐的连续天数。 */
private fun currentStreak(days: Set<String>, lastDay: String): Int {
    var streak = 0
    val cal = Calendar.getInstance(Locale.CHINA)
    val p = lastDay.split("-")
    cal.clear(); cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
    while (days.contains(formatDayKey(cal))) {
        streak++
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    return streak
}

private fun formatDayKey(cal: Calendar): String = "%04d-%02d-%02d".format(
    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
)

/* ==================== 必吃榜二期：逐笔去标识交易上传 ==================== */

/** 上传行：去标识交易（终端 + 秒级时间 + 金额 + 食堂）。与服务器 txns 接口同构。 */
data class DeidentifiedTxn(
    val terminal: String,
    /** 账单原始时间字段规整为 "yyyy-MM-dd HH:mm:ss"（跨设备去重键的一致性全靠它）。 */
    val ts: String,
    val amountCents: Long,
    /** 食堂名（「榴园」）；楼层不进上传字段（服务端食堂榜按 canteen 分组，带楼层会拆碎分组）。 */
    val canteen: String,
    /** 楼层（「一楼」，可空）：不进服务端契约，由调用侧就地攒进本地「终端→楼层」学习表。 */
    val floor: String? = null
)

/**
 * 流水 → 去标识交易（服务端最终形态）。
 * - 只抽食堂类消费（extractCanteenName 命中）+ 有终端码的；非食堂/收入不出口
 * - **不做正餐时段过滤**（服务端统一过滤，客户端全量上传，口径单点在服务端）
 * - ts 用 effectdateStr 原值规整（秒级补齐），不重新生成——(POS, 秒, 金额) 是服务端去重键
 * - canteen 保持食堂名（食堂榜分组键）；floor 不发给服务端，调用侧用于本地楼层学习
 * - 不含任何用户/设备标识
 */
fun List<TurnoverRecord>.toDeidentifiedTxns(): List<DeidentifiedTxn> =
    mapNotNull { record ->
        if (!record.isExpenseRecord()) return@mapNotNull null
        val terminal = record.locationName?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val raw = record.merchantText()
        val canteen = extractCanteenName(raw) ?: return@mapNotNull null
        val ts = normalizeTs(record.effectdateStr) ?: return@mapNotNull null
        DeidentifiedTxn(
            terminal = terminal,
            ts = ts,
            amountCents = record.tranamt,
            canteen = canteen,
            floor = extractFloor(raw)
        )
    }

/** "yyyy-MM-dd HH:mm[:ss]" → 秒级补齐；无法解析返回 null。 */
private fun normalizeTs(raw: String?): String? {
    val v = raw?.trim() ?: return null
    val m = Regex("(\\d{4}-\\d{2}-\\d{2})[ T](\\d{2}:\\d{2})(?::(\\d{2}))?").find(v) ?: return null
    return "${m.groupValues[1]} ${m.groupValues[2]}:${m.groupValues[3].ifEmpty { "00" }}"
}

