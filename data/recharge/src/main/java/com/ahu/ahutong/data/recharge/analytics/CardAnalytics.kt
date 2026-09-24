package com.ahu.ahutong.data.recharge.analytics

import com.ahu.ahutong.data.crawler.model.ycard.TurnoverRecord
import com.ahu.ahutong.data.debug.DebugClock
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * 校园卡消费分析引擎（移植自 Ahu_Plus 的 CardAnalytics，按本工程口径适配 5 处）：
 *
 *  1. ★支出识别★：以 TurnoverRecord.isExpense（typeFrom != "1"）为唯一权威口径——
 *     实测 tranamt 恒为正，方向由 typeFrom 给出。关键字启发式降级为 typeFrom 缺失时的兜底。
 *  2. 金额全程 Long 分运算（不碰 Double），与流水页整数口径一致，出参再格式化。
 *  3. today 走 DebugClock.nowDate()（mock 时间全局生效）。
 *  4. searchText 字段集合按 TurnoverRecord 实际字段收窄。
 *  5. 时间解析手写（yyyy-MM-dd HH:mm[:ss]），无共享 SimpleDateFormat → 后台线程安全。
 *
 *  保留的设计：逻辑日 04:00 分界、逐日趋势补零、一次算完切换查表、商户名归一化单点。
 */

/** 逻辑日的分界小时：凌晨 4 点前的消费归到前一天。 */
private const val DAY_BOUNDARY_HOUR = 4

/** ★ 校园专属：生活区 → 食堂名（账单写"北二区食堂一楼"，学生叫「榴园」）。 */
private val CANTEEN_MAPPING = mapOf(
    "北一区" to "桔园",
    "北二区" to "榴园",
    "北三区" to "蕙园",
    "南一区" to "梅园",
    "南二区" to "桂园",
    "南三区" to "梧桐园"
)

/**
 * 账单列表的认知版商户文本：只把生活区代号替换为学生叫法（「北二区食堂一楼」→「榴园食堂一楼」），
 * 其余原文保留——与引擎统计用的激进归一不同，展示层要保真细节。映射与 CANTEEN_MAPPING 单点共享。
 */
fun displayMerchantText(raw: String?): String? {
    if (raw.isNullOrEmpty()) return raw
    var text: String = raw
    CANTEEN_MAPPING.forEach { (zone, name) ->
        if (text.contains(zone)) text = text.replace(zone, name)
    }
    return text
}

/** 教务学期日历最小子集（第一期传空列表 → 「按学期」自动隐藏）。 */
data class AcademicSemester(
    val id: Int? = null,
    val nameZh: String? = null,
    val code: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

enum class AnalyticsPeriodKind(val label: String) {
    MONTH("按月"),
    SEMESTER("按学期")
}

data class AnalyticsPeriod(
    val id: String,
    val kind: AnalyticsPeriodKind,
    val label: String,
    val startDay: String,
    val endDay: String,
    val isCurrent: Boolean = false
)

data class CardAnalyticsReport(
    val monthPeriods: List<AnalyticsPeriod> = emptyList(),
    val semesterPeriods: List<AnalyticsPeriod> = emptyList(),
    val currentMonthId: String? = null,
    val currentSemesterId: String? = null,
    val summaries: Map<String, CardAnalyticsSummary> = emptyMap()
) {
    val currentMonth: CardAnalyticsSummary?
        get() = currentMonthId?.let { summaries[it] } ?: monthPeriods.firstOrNull()?.let { summaries[it.id] }

    fun summary(periodId: String?): CardAnalyticsSummary? = periodId?.let { summaries[it] }
}

/** 金额一律为「分」（Long）。share 为 0..1 比例，UI 只做展示不做算术。 */
data class CardAnalyticsSummary(
    val period: AnalyticsPeriod,
    val totalExpense: Long = 0,
    val expenseCount: Int = 0,
    val activeDays: Int = 0,
    val dailyAvg: Long = 0,
    val canteenShare: Double = 0.0,
    val dailyTrend: List<DailyPoint> = emptyList(),
    val splitRows: List<FoodSplitStat> = emptyList(),
    val canteenStats: List<CanteenStat> = emptyList(),
    val mealStats: List<CategoryStat> = emptyList(),
    val nonCanteenCategories: List<CategoryStat> = emptyList(),
    val nonCanteenMerchants: List<MerchantStat> = emptyList(),
    val highDays: List<DailyPoint> = emptyList(),
    val transactionsByDay: Map<String, List<TransactionItem>> = emptyMap()
)

data class DailyPoint(
    val date: String,
    val totalExpense: Long,
    val txnCount: Int
)

data class FoodSplitStat(
    val name: String,
    val totalAmount: Long,
    val count: Int,
    val share: Double
)

data class CanteenStat(
    val name: String,
    val totalAmount: Long,
    val count: Int,
    val share: Double,
    val avgAmount: Long
)

data class CategoryStat(
    val name: String,
    val totalAmount: Long,
    val count: Int,
    val share: Double,
    val avgAmount: Long
)

data class MerchantStat(
    val name: String,
    val totalAmount: Long,
    val count: Int,
    val avgAmount: Long
)

data class TransactionItem(
    val day: String,
    val time: String,
    val amount: Long,
    val merchant: String,
    val group: String,
    val category: String
)

/** 中间态：账单先解析成它，后续所有统计基于它，避免重复解析日期与分类。 */
private data class ParsedBill(
    val record: TurnoverRecord,
    val time: Date,
    val logicalDay: String,
    val amountFen: Long,
    val merchant: String,
    val canteenName: String?,
    val mealName: String?,
    val nonCanteenCategory: String?
)

/** ★ 唯一入口 ★ 账单列表 → 完整分析报告。纯函数、无 IO、后台线程安全。 */
fun List<TurnoverRecord>.toAnalyticsReport(
    today: Date = DebugClock.nowDate(),
    academicSemesters: List<AcademicSemester> = emptyList()
): CardAnalyticsReport {
    val expenses = mapNotNull { record ->
        if (!record.isExpenseRecord()) return@mapNotNull null
        val time = parseDateTime(record.effectdateStr) ?: parseDateTime(record.jndatetimeStr)
            ?: return@mapNotNull null
        val merchant = record.merchantText()
        val canteen = extractCanteenName(merchant)
        ParsedBill(
            record = record,
            time = time,
            logicalDay = logicalDayKey(time),
            amountFen = record.tranamt,
            merchant = merchant,
            canteenName = canteen,
            mealName = if (canteen != null) mealName(time) else null,
            nonCanteenCategory = if (canteen == null) classifyNonCanteen(merchant) else null
        )
    }.sortedBy { it.time }

    if (expenses.isEmpty()) return CardAnalyticsReport()

    val monthPeriods = buildMonthPeriods(expenses, today)
    val semesterPeriods = buildSemesterPeriods(expenses, today, academicSemesters)
    val summaries = (monthPeriods + semesterPeriods).associate { period ->
        period.id to buildSummary(period, expenses)
    }

    return CardAnalyticsReport(
        monthPeriods = monthPeriods,
        semesterPeriods = semesterPeriods,
        currentMonthId = monthPeriods.firstOrNull { it.isCurrent }?.id
            ?: monthPeriods.firstOrNull()?.id,
        currentSemesterId = semesterPeriods.firstOrNull { it.isCurrent }?.id
            ?: semesterPeriods.firstOrNull()?.id,
        summaries = summaries
    )
}

private fun buildSummary(
    period: AnalyticsPeriod,
    allExpenses: List<ParsedBill>
): CardAnalyticsSummary {
    val expenses = allExpenses.filter { it.logicalDay >= period.startDay && it.logicalDay <= period.endDay }
    val total = expenses.sumOf { it.amountFen }
    val activeDays = expenses.map { it.logicalDay }.distinct().size
    val dailyAvg = if (activeDays > 0) total / activeDays else 0L
    val transactionItems = expenses
        .sortedByDescending { it.time }
        .map { it.toTransactionItem() }

    val dailyTrend = buildDailyTrend(period, expenses)
    val foodTotal = expenses.filter { it.canteenName != null }.sumOf { it.amountFen }
    val canteenShare = if (total > 0) foodTotal.toDouble() / total else 0.0

    val splitRows = listOf(
        FoodSplitStat(
            name = "食堂",
            totalAmount = foodTotal,
            count = expenses.count { it.canteenName != null },
            share = canteenShare
        ),
        FoodSplitStat(
            name = "非食堂",
            totalAmount = total - foodTotal,
            count = expenses.count { it.canteenName == null },
            share = if (total > 0) (total - foodTotal).toDouble() / total else 0.0
        )
    )

    val canteenStats = expenses.filter { it.canteenName != null }
        .groupBy { it.canteenName.orEmpty() }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amountFen }
            CanteenStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                share = if (foodTotal > 0) amount.toDouble() / foodTotal else 0.0,
                avgAmount = amount / rows.size
            )
        }
        .sortedByDescending { it.totalAmount }

    val mealStats = expenses.filter { it.mealName != null }
        .groupBy { it.mealName.orEmpty() }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amountFen }
            CategoryStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                share = if (foodTotal > 0) amount.toDouble() / foodTotal else 0.0,
                avgAmount = amount / rows.size
            )
        }
        .sortedWith(compareBy<CategoryStat> { mealSortIndex(it.name) }.thenByDescending { it.totalAmount })

    val nonCanteen = expenses.filter { it.canteenName == null }
    val nonCanteenTotal = nonCanteen.sumOf { it.amountFen }
    val nonCanteenCategories = nonCanteen
        .groupBy { it.nonCanteenCategory.orEmpty() }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amountFen }
            CategoryStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                share = if (nonCanteenTotal > 0) amount.toDouble() / nonCanteenTotal else 0.0,
                avgAmount = amount / rows.size
            )
        }
        .sortedByDescending { it.totalAmount }

    val nonCanteenMerchants = nonCanteen
        .groupBy { normalizeMerchantName(it.merchant) }
        .map { (name, rows) ->
            val amount = rows.sumOf { it.amountFen }
            MerchantStat(
                name = name,
                totalAmount = amount,
                count = rows.size,
                avgAmount = amount / rows.size
            )
        }
        .sortedByDescending { it.totalAmount }
        .take(8)

    return CardAnalyticsSummary(
        period = period,
        totalExpense = total,
        expenseCount = expenses.size,
        activeDays = activeDays,
        dailyAvg = dailyAvg,
        canteenShare = canteenShare,
        dailyTrend = dailyTrend,
        splitRows = splitRows,
        canteenStats = canteenStats,
        mealStats = mealStats,
        nonCanteenCategories = nonCanteenCategories,
        nonCanteenMerchants = nonCanteenMerchants,
        highDays = dailyTrend.sortedByDescending { it.totalExpense }.take(5),
        transactionsByDay = transactionItems.groupBy { it.day }
    )
}

private fun ParsedBill.toTransactionItem(): TransactionItem {
    val group = if (canteenName != null) "食堂" else "非食堂"
    val category = mealName ?: nonCanteenCategory ?: "其他消费"
    return TransactionItem(
        day = logicalDay,
        time = record.effectdateStr ?: record.jndatetimeStr.orEmpty(),
        amount = amountFen,
        merchant = canteenName ?: normalizeMerchantName(merchant),
        group = group,
        category = category
    )
}

/** 逐日趋势：零消费日补 0 点，否则「没消费」与「没数据」在折线上同形。 */
private fun buildDailyTrend(period: AnalyticsPeriod, expenses: List<ParsedBill>): List<DailyPoint> {
    val perDay = expenses.groupBy { it.logicalDay }
    val points = mutableListOf<DailyPoint>()
    val cal = dayCalendar(period.startDay) ?: return emptyList()
    val end = dayCalendar(period.endDay) ?: return emptyList()
    while (!cal.after(end)) {
        val key = formatDay(cal)
        val rows = perDay[key].orEmpty()
        points += DailyPoint(
            date = key,
            totalExpense = rows.sumOf { it.amountFen },
            txnCount = rows.size
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return points
}

/** 月份周期：只列「实际有消费的月份」。 */
private fun buildMonthPeriods(expenses: List<ParsedBill>, today: Date): List<AnalyticsPeriod> {
    val currentMonthId = formatMonth(today)
    return expenses
        .groupBy { it.logicalDay.substring(0, 7) }
        .keys
        .sortedDescending()
        .map { monthId ->
            val cal = Calendar.getInstance(Locale.CHINA).apply {
                clear()
                set(
                    monthId.substring(0, 4).toInt(),
                    monthId.substring(5, 7).toInt() - 1,
                    1
                )
            }
            val start = formatDay(cal)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            val end = formatDay(cal)
            AnalyticsPeriod(
                id = "month-$monthId",
                kind = AnalyticsPeriodKind.MONTH,
                label = "%d年%d月".format(
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1
                ),
                startDay = start,
                endDay = end,
                isCurrent = monthId == currentMonthId
            )
        }
}

/** 学期周期：完全由教务日历驱动；非法项丢弃，不猜不按月份兜底。 */
private fun buildSemesterPeriods(
    expenses: List<ParsedBill>,
    today: Date,
    academicSemesters: List<AcademicSemester>
): List<AnalyticsPeriod> {
    val todayDay = formatDay(Calendar.getInstance(Locale.CHINA).apply { time = today })
    return academicSemesters
        .mapNotNull { semester ->
            val startDay = semester.startDate.toSemesterDayOrNull() ?: return@mapNotNull null
            val endDay = semester.endDate.toSemesterDayOrNull() ?: return@mapNotNull null
            if (startDay > endDay) return@mapNotNull null
            val label = semester.nameZh?.takeIf(String::isNotBlank)
                ?: semester.code?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            AnalyticsPeriod(
                id = "semester-${semester.id ?: "$startDay-$endDay"}",
                kind = AnalyticsPeriodKind.SEMESTER,
                label = label,
                startDay = startDay,
                endDay = endDay,
                isCurrent = todayDay in startDay..endDay
            )
        }
        .distinctBy(AnalyticsPeriod::id)
        .filter { period ->
            period.isCurrent || expenses.any { it.logicalDay in period.startDay..period.endDay }
        }
        .sortedByDescending(AnalyticsPeriod::startDay)
}

private fun String?.toSemesterDayOrNull(): String? {
    val day = this?.trim()?.take(10)?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        ?: return null
    return day.takeIf { dayCalendar(it) != null }
}

/* ==================== 时间与日期（手写解析，线程安全） ==================== */

/** 解析 "yyyy-MM-dd HH:mm[:ss]"（服务端本地化字符串），失败返回 null。 */
private fun parseDateTime(value: String?): Date? {
    val v = value?.trim() ?: return null
    val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})[ T](\\d{2}):(\\d{2})(?::(\\d{2}))?").find(v)
        ?: return null
    val cal = Calendar.getInstance(Locale.CHINA)
    cal.clear()
    cal.set(
        m.groupValues[1].toInt(),
        m.groupValues[2].toInt() - 1,
        m.groupValues[3].toInt(),
        m.groupValues[4].toInt(),
        m.groupValues[5].toInt(),
        m.groupValues[6].toIntOrNull() ?: 0
    )
    return cal.time
}

private fun dayCalendar(day: String): Calendar? {
    val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").matchEntire(day) ?: return null
    val cal = Calendar.getInstance(Locale.CHINA)
    cal.clear()
    cal.set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt())
    return cal
}

private fun formatDay(cal: Calendar): String = "%04d-%02d-%02d".format(
    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
)

private fun formatMonth(date: Date): String {
    val cal = Calendar.getInstance(Locale.CHINA).apply { time = date }
    return "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
}

/** ★ 逻辑日 ★ 04:00 之前算前一天。 */
private fun logicalDayKey(date: Date): String {
    val cal = Calendar.getInstance(Locale.CHINA).apply { time = date }
    if (cal.get(Calendar.HOUR_OF_DAY) < DAY_BOUNDARY_HOUR) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
    }
    return formatDay(cal)
}

/* ==================== 记录口径（按工程实测适配） ==================== */

/** ★ 支出识别 ★ typeFrom 为唯一权威（"2"=支出 "1"=收入）；缺失时关键字兜底。 */
private fun TurnoverRecord.isExpenseRecord(): Boolean {
    typeFrom?.let { return it != "1" }
    val text = searchText()
    if (listOf("充值", "退款", "退费", "转入", "入账").any { text.contains(it) }) return false
    if (listOf("消费", "支付", "扣款", "二维码", "付款").any { text.contains(it) }) return true
    return true // typeFrom 缺失且无关键字：tranamt 恒为正的消费场景，按支出计
}

private fun TurnoverRecord.searchText(): String {
    return listOf(
        resume.orEmpty(), turnoverType.orEmpty(),
        consumeTypeName.orEmpty(), toMerchant.orEmpty(), remark.orEmpty()
    ).joinToString(" ")
}

private fun TurnoverRecord.merchantText(): String {
    return listOf(resume.orEmpty(), toMerchant.orEmpty())
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .ifBlank { searchText() }
}

/* ==================== 校园约定（与 Ahu_Plus 同源同校，直接沿用） ==================== */

private fun extractCanteenName(text: String): String? {
    if (text.isBlank()) return null
    val isFoodPlace = text.contains("食堂") || text.contains("餐厅")
    val zone = Regex("(北[一二三四五六七八九十]+区|南[一二三四五六七八九十]+区)").find(text)?.value
    if (zone != null && isFoodPlace) {
        return CANTEEN_MAPPING[zone] ?: zone
    }
    return Regex("([\\u4e00-\\u9fa5]{1,10}(?:食堂|餐厅))").find(text)?.groupValues?.getOrNull(1)
}

private fun normalizeMerchantName(raw: String): String {
    val canteen = extractCanteenName(raw)
    if (canteen != null) return canteen
    return raw
        .replace(Regex("(扫码支付|刷卡|消费|扣款|支付|二维码|一楼|二楼|三楼|四楼|五楼)"), "")
        .replace(Regex("[\\-—_/【】（）()\\s]+"), "")
        .trim()
        .ifBlank { "其他" }
}

/** 餐点时段（按食堂营业时间）；只有食堂消费参与餐点统计。 */
private fun mealName(date: Date): String {
    val hour = Calendar.getInstance(Locale.CHINA).apply { time = date }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 6..9 -> "早餐"
        in 10..14 -> "午餐"
        in 16..21 -> "晚餐"
        else -> "夜宵/其他"
    }
}

private fun mealSortIndex(name: String): Int {
    return when (name) {
        "早餐" -> 0
        "午餐" -> 1
        "晚餐" -> 2
        else -> 3
    }
}

/** 非食堂分类：刻意不用 consumeTypeName（实测常为空或笼统），关键字启发式。 */
private fun classifyNonCanteen(text: String): String {
    return when {
        text.contains("浴室") || text.contains("水控") || text.contains("洗浴") -> "浴室水控"
        text.contains("开水") || text.contains("热水") -> "开水热水"
        text.contains("网费") || text.contains("网络") -> "网费"
        text.contains("电费") || text.contains("空调") || text.contains("照明") -> "水电缴费"
        text.contains("超市") || text.contains("商店") || text.contains("便利") || text.contains("水果") -> "校园零售"
        text.contains("转账") || text.contains("电子账户") -> "电子账户"
        else -> "其他消费"
    }
}
