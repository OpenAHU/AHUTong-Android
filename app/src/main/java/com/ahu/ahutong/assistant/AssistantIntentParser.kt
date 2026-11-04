package com.ahu.ahutong.assistant

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * 确定性意图识别：不调用模型，把常见问句映射到能力 id，并抽取最常用的参数。
 *
 * 顺序有意义：动作类在前（充值/发布/发送），再匹配具体查询，最后才用泛化词兜底。
 * 未命中的问句返回 [AssistantIntent.Unknown]，由 UI 提示能力范围。
 */
data class AssistantIntent(
    val capabilityId: String?,
    val arguments: JsonObject = JsonObject(),
) {
    companion object {
        val Unknown = AssistantIntent(null)
    }
}

object AssistantIntentParser {

    private data class Rule(
        val capabilityId: String,
        val keywords: List<String>,
        val excluded: List<String> = emptyList(),
    )

    private val rules = listOf(
        Rule("get_bills", listOf("账单", "流水", "消费记录", "充值记录", "花了多少", "支出")),
        Rule("recharge_electricity", listOf("充电费", "交电费", "缴电费", "电费充值")),
        Rule("recharge_bathroom", listOf("浴室", "洗澡", "水卡", "热水卡")),
        Rule("recharge_network", listOf("网费", "校园网")),
        Rule("recharge_card", listOf("充值", "充钱", "充点", "充饭卡", "充校园卡", "充一卡通")),
        Rule("send_mail", listOf("发邮件", "发送邮件", "写邮件", "回邮件")),
        Rule("publish_lost_found", listOf("发布失物", "发布寻物", "发个失物", "发一条失物")),
        Rule("delete_lost_found", listOf("删除失物", "撤销失物", "删掉失物")),
        Rule("submit_evaluation", listOf("提交评教", "代评", "评教提交", "帮我评教")),
        Rule("get_evaluation_tasks", listOf("评教", "教学评价")),
        Rule("switch_electricity_condition", listOf("切换电控", "切换电费房间", "换电控")),
        Rule("get_electricity_conditions", listOf("电控条件", "最近房间", "电费房间")),
        Rule("get_free_rooms", listOf("空教室", "空闲教室", "自习室", "没课的教室", "教室空")),
        Rule("get_gpa_rank", listOf("绩点排名", "专业排名", "排名多少", "我的排名", "排第几")),
        Rule("get_grades", listOf("成绩", "绩点", "分数", "挂科")),
        Rule("get_exams", listOf("考试", "考场", "补考")),
        Rule("get_lost_found", listOf("失物", "寻物", "丢了", "捡到")),
        Rule("web_search", listOf("联网搜索", "网上搜索", "查新闻", "搜一下网上")),
        Rule("get_balance", listOf("余额", "饭卡", "一卡通", "卡里", "校园卡")),
        Rule("get_electricity", listOf("电费", "电量", "还剩多少电")),
        Rule("get_attendance", listOf("考勤", "缺勤", "迟到", "请假记录")),
        Rule("get_program_completion", listOf("培养方案", "毕业学分", "还差多少学分", "完成度")),
        Rule("get_notices", listOf("通知", "公告")),
        Rule("get_canteen_ranking", listOf("必吃榜", "食堂排行", "哪个窗口", "食堂热度", "食堂榜")),
        Rule("get_phone_book", listOf("电话", "号码", "电话本")),
        Rule("get_course_tasks", listOf("章节", "任务点", "学习进度", "网课进度")),
        Rule("get_assignments", listOf("作业"), excluded = listOf("写作业", "做作业")),
        Rule("get_weather", listOf("天气", "气温", "下雨", "冷不冷", "热不热")),
        Rule("get_schedule", listOf("课表", "有课", "上课", "下节", "什么课", "几节课")),
        Rule("get_mail", listOf("邮箱", "邮件", "收件箱")),
    )

    fun parse(raw: String): AssistantIntent {
        val text = raw.trim()
        if (text.isEmpty()) return AssistantIntent.Unknown
        val rule = rules.firstOrNull { rule ->
            rule.keywords.any { it in text } &&
                rule.excluded.none { it in text }
        } ?: return AssistantIntent.Unknown
        return AssistantIntent(rule.capabilityId, argumentsFor(rule.capabilityId, text))
    }

    private fun argumentsFor(capabilityId: String, text: String): JsonObject = JsonObject().apply {
        parseCampusId(text)?.let { addProperty("campusId", it) }
        parseCanteenCampus(text)?.let { addProperty("campus", it) }
        parseDays(text)?.let { addProperty("days", it) }
        parseDate(text)?.let { addProperty("date", it) }
        parseUnits(text)?.let { units -> add("units", JsonArray().apply { units.forEach { add(JsonPrimitive(it)) } }) }
        parseBuilding(text)?.let { addProperty("building", it) }
        parseSource(text)?.let { addProperty("source", it) }
        parseTerm(text)?.let { addProperty("term", it) }
        parsePage(text)?.let { addProperty("page", it) }
        parseLimit(text)?.let { addProperty("limit", it) }
        parseAmount(text)?.let { addProperty("amount", it) }
        parseBank(text)?.let { addProperty("bank", it) }
        parseBathroom(text)?.let { addProperty("bathroom", it) }
        parsePhone(text)?.let { addProperty("phone", it) }
        if ("全部" in text || "所有" in text) addProperty("all", true)
        parsePeriod(text)?.let { addProperty("period", it) }
        if ("刷新" in text || "同步" in text || "最新" in text) addProperty("refresh", true)
        if ("未读" in text) addProperty("unread_only", true)
        if (capabilityId == "get_lost_found") {
            addProperty("state", if ("寻物" in text || "丢了" in text) 2 else 1)
        }
        if (capabilityId == "get_mail") {
            parseMailFolder(text)?.let { addProperty("folder", it) }
        }
        extractQuery(capabilityId, text)?.let { addProperty("query", it) }
    }

    private fun parseCampusId(text: String): Int? = when {
        "龙河" in text -> 2
        "磬苑" in text || "新区" in text -> 1
        else -> null
    }

    private fun parseCanteenCampus(text: String): String? = when {
        "磬苑" in text || "新区" in text -> "qingyuan"
        "龙河" in text -> "longhe"
        "金寨路" in text -> "jinzailu"
        else -> null
    }

    private fun parseDays(text: String): Int? {
        Regex("(?:未来|近|最近|接下来)?\\s*(\\d{1,2})\\s*天").find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        Regex("(一|两|二|三|四|五|六|七|八|九|十|十四|三十)天").find(text)?.let { match ->
            return chineseNumber(match.groupValues[1])
        }
        if ("两周" in text) return 14
        if ("一周" in text) return 7
        if ("半个月" in text) return 15
        return null
    }

    private fun chineseNumber(text: String): Int? = when (text) {
        "一" -> 1
        "两", "二" -> 2
        "三" -> 3
        "四" -> 4
        "五" -> 5
        "六" -> 6
        "七" -> 7
        "八" -> 8
        "九" -> 9
        "十" -> 10
        "十四" -> 14
        "三十" -> 30
        else -> null
    }

    private fun parseDate(text: String): String? =
        Regex("\\d{4}-\\d{2}-\\d{2}").find(text)?.value

    private fun parseUnits(text: String): List<String>? {
        val range = Regex("第?\\s*(\\d{1,2})\\s*(?:-|~|到|—)\\s*(\\d{1,2})\\s*节").find(text)
        if (range != null) {
            val start = range.groupValues[1].toIntOrNull() ?: return null
            val end = range.groupValues[2].toIntOrNull() ?: return null
            if (start > end) return null
            return (start..end).filter { it in 1..13 }.map(Int::toString).ifEmpty { null }
        }
        val single = Regex("第\\s*(\\d{1,2})\\s*节").find(text)?.groupValues?.get(1)?.toIntOrNull()
        return single?.takeIf { it in 1..13 }?.let { listOf(it.toString()) }
    }

    private fun parseBuilding(text: String): String? {
        val match = Regex("([\\u4e00-\\u9fa5]{2,6}(?:教学楼|实验楼|楼))").find(text)?.groupValues?.get(1)
            ?: return null
        return match.takeIf { it != "教学楼" && it != "实验楼" }
    }

    private fun parseSource(text: String): String? = when {
        "教务处" in text -> "jwc"
        "研究生院" in text -> "postgraduate"
        "学院" in text -> "college"
        "官网" in text || "学校" in text -> "university"
        else -> null
    }

    private fun parseTerm(text: String): String? {
        val year = Regex("20\\d{2}\\s*[-—]\\s*20\\d{2}").find(text)?.value
            ?.replace(" ", "")
            ?.replace("—", "-")
        val upper = "上学期" in text || "第一学期" in text || "上半个学期" in text
        val lower = "下学期" in text || "第二学期" in text || "下半个学期" in text
        return when {
            year != null && upper -> "$year 上"
            year != null && lower -> "$year 下"
            year != null -> year
            upper -> "上学期"
            lower -> "下学期"
            else -> null
        }
    }

    private fun parsePage(text: String): Int? =
        Regex("第\\s*(\\d{1,2})\\s*页").find(text)?.groupValues?.get(1)?.toIntOrNull()

    private fun parseLimit(text: String): Int? {
        Regex("(?:前|最近|显示)?\\s*(\\d{1,2})\\s*条").find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun parseAmount(text: String): String? =
        Regex("(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块钱|块)").find(text)?.groupValues?.get(1)

    private fun parseBank(text: String): String? = when {
        "农业银行" in text || "农行" in text -> "agricultural"
        "招商银行" in text || "招行" in text -> "cmb"
        "支付宝" in text -> "alipay"
        else -> null
    }

    private fun parseBathroom(text: String): String? = when {
        "竹园" in text || "龙河" in text -> "竹园/龙河"
        "桔园" in text || "蕙园" in text -> "桔园/蕙园"
        else -> null
    }

    private fun parsePhone(text: String): String? =
        Regex("1\\d{10}").find(text)?.value

    private fun parsePeriod(text: String): String? = when {
        "本月" in text -> "month"
        "全部" in text || "总榜" in text || "历史" in text -> "all"
        "本周" in text || "这周" in text -> "week"
        else -> null
    }

    private fun parseMailFolder(text: String): String? = when {
        "收件箱" in text -> "收件箱"
        "已发送" in text -> "已发送"
        "草稿" in text -> "草稿"
        "垃圾" in text -> "垃圾"
        else -> null
    }

    private fun extractQuery(capabilityId: String, text: String): String? {
        Regex("[“\"「'](.+?)[”\"」']").find(text)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }
        return when (capabilityId) {
            "get_notices" -> Regex("关于(.+?)的?(?:通知|公告)").find(text)?.groupValues?.get(1)
            "get_phone_book" -> Regex("查(?:一下)?(.+?)(?:的)?(?:电话|号码)").find(text)?.groupValues?.get(1)
                ?: Regex("(.+?)(?:的)?(?:电话|号码)").find(text)?.groupValues?.get(1)
            "get_mail" -> Regex("搜索(.+?)(?:的)?(?:邮件|邮件吗)?").find(text)?.groupValues?.get(1)
            "get_lost_found" -> Regex("找(.+?)的?(?:失物|寻物)").find(text)?.groupValues?.get(1)
            "web_search" -> Regex("(?:联网搜索|网上搜索|搜一下|搜索)(.+)").find(text)?.groupValues?.get(1)
            else -> null
        }?.trim()?.takeIf { it.length >= 2 }
    }
}
