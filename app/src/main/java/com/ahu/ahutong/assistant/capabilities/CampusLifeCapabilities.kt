package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.canteen.CanteenGateway
import com.ahu.ahutong.data.notice.CampusNoticeRepository
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.ui.state.TelDirectoryViewModel
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 校园生活类只读能力：必吃榜、失物招领、电话本、校园通知。
 * 通知/失物招领属于外部内容，统一标记 UNTRUSTED。
 */
class GetCanteenRankingCapability(
    private val insights: suspend (String, Int, String?) -> com.ahu.ahutong.data.canteen.InsightsResponse? =
        { period, limit, canteens -> CanteenGateway.insights(period, limit, canteens) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_canteen_ranking",
        title = "查询必吃榜",
        description = "查询校园食堂窗口热度榜。period: week/month/all；campus: all/qingyuan/longhe/jinzailu。返回冠军窗口与排名。",
        parameters = schema(
            "period" to stringProp("统计周期：week=本周，month=本月，all=全部；默认 week"),
            "campus" to stringProp(
                "校区：all/qingyuan/longhe/jinzailu，默认 all",
                enumValues = listOf("all", "qingyuan", "longhe", "jinzailu"),
            ),
            "limit" to intProp("返回条目数，默认 10", minimum = 1, maximum = 50),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "必吃榜",
        openRoute = "canteen_ranking",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val period = when (arguments.stringArg("period")?.lowercase()) {
            "month" -> "month"
            "all" -> "all"
            else -> "week"
        }
        val campusKey = arguments.stringArg("campus")?.lowercase()
        val campusName = when (campusKey) {
            "qingyuan" -> "磬苑"
            "longhe" -> "龙河"
            "jinzailu" -> "金寨路"
            else -> null
        }
        val canteens = when (campusName) {
            "磬苑" -> "榴园,桔园,梅园,桂园,蕙园食堂,梧桐园食堂"
            "龙河" -> "本部二食堂,本部三食堂,二食堂餐厅,三食堂,外招餐厅"
            "金寨路" -> "江淮学院食堂"
            else -> null
        }
        val limit = (arguments.intArg("limit") ?: DEFAULT_LIMIT).coerceIn(1, 50)
        val data = insights(period, limit, canteens)
            ?: return AssistantOutcome.Failure("canteen_failed", "必吃榜暂时不可用，稍后再试", retryable = true)
        val ranking = data.ranking.orEmpty()
        val lines = ranking.take(limit).map { item ->
            buildString {
                append(item.rank)
                append(". ")
                append(item.name ?: "未命名窗口")
                item.canteen?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                    item.floor?.takeIf { floor -> floor.isNotBlank() }?.let { floor ->
                        append(floor)
                        append("F")
                    }
                }
                append(" · ")
                append(item.txns)
                append(" 人次")
                item.avgCentsPerTxn?.let {
                    append(" · 人均 ¥")
                    append(formatMoney(it / 100.0))
                }
            }
        }
        val top = data.superlatives?.topWindow
        val summary = if (top != null) {
            "冠军窗口：${top.name ?: "未命名窗口"}（${top.canteen.orEmpty()}）"
        } else {
            "${campusName ?: "全部"}食堂 ${ranking.size} 个窗口上榜"
        }
        return AssistantOutcome.Success(
            AssistantAnswer(summary = summary, lines = lines, route = spec.openRoute)
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
    }
}

class GetLostFoundCapability(
    private val load: suspend (Int, Int, Int) -> AhuResult<com.ahu.ahutong.data.crawler.model.adwnh.LostFoundResponse> =
        { page, size, state -> AHURepository.getLostFoundList(page, size, state) },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_lost_found",
        title = "查询失物招领",
        description = "查询校园失物招领或寻物启事。state: 1=失物招领，2=寻物启事；可按校区、类型和关键词筛选。",
        parameters = schema(
            "state" to intProp("1=失物招领，2=寻物启事；默认 1", minimum = 1, maximum = 2),
            "campus" to stringProp("校区名称，支持模糊匹配"),
            "type" to stringProp("物品类型，支持模糊匹配"),
            "query" to stringProp("标题关键词"),
            "page" to intProp("页码，默认 1", minimum = 1, maximum = 20),
            "page_size" to intProp("每页条数，默认 20", minimum = 1, maximum = 50),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "失物招领",
        openRoute = "lost_found",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        if (!SessionStore.isLoggedIn()) return notLoggedIn(spec)
        val state = (arguments.intArg("state") ?: 1).coerceIn(1, 2)
        val page = (arguments.intArg("page") ?: 1).coerceIn(1, 20)
        val size = (arguments.intArg("page_size") ?: 20).coerceIn(1, 50)
        return when (val result = load(page, size, state)) {
            is AhuResult.Failure -> result.error.asAssistantFailure()
            is AhuResult.Success -> {
                val response = result.value
                val campus = arguments.stringArg("campus")
                val type = arguments.stringArg("type")
                val query = arguments.stringArg("query")
                val items = response.data.list.filter { item ->
                    (campus == null || item.campusName?.contains(campus, ignoreCase = true) == true) &&
                        (type == null || item.lostType?.typeName?.contains(type, ignoreCase = true) == true) &&
                        (query == null || item.title.contains(query, ignoreCase = true))
                }
                val stateLabel = if (state == 1) "失物招领" else "寻物启事"
                val lines = items.take(MAX_ITEMS).map { item ->
                    buildString {
                        append(item.title)
                        item.lostType?.typeName?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                        item.campusName?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                        item.createtime?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it.take(16))
                        }
                    }
                }
                AssistantOutcome.Success(
                    AssistantAnswer(
                        summary = "${stateLabel}共 ${response.data.total} 条，当前显示 ${items.size} 条",
                        lines = lines,
                        route = spec.openRoute,
                        trust = AssistantTrust.UNTRUSTED,
                    )
                )
            }
        }
    }

    private companion object {
        const val MAX_ITEMS = 15
    }
}

class GetPhoneBookCapability(
    private val directory: Map<String, List<com.ahu.ahutong.data.model.Tel>> =
        TelDirectoryViewModel.TelBook,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_phone_book",
        title = "查询电话本",
        description = "查询安徽大学校内公开电话。可按部门、名称或号码搜索；结果包含所属分组与号码。",
        parameters = schema(
            "query" to stringProp("关键词，例如 教务处、报修、6386"),
            "department" to stringProp("限定分组，例如 磬苑后勤、报警电话"),
            "limit" to intProp("最多返回条数，默认 30", minimum = 1, maximum = 100),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "校内电话本",
        openRoute = "phone_book",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val query = arguments.stringArg("query")
        val department = arguments.stringArg("department")
        if (query == null && department == null) {
            return AssistantOutcome.Failure("invalid_arguments", "请提供 query 或 department")
        }
        val limit = (arguments.intArg("limit") ?: 30).coerceIn(1, 100)
        val matches = directory
            .filterKeys { group -> department == null || group.contains(department) }
            .flatMap { (group, entries) -> entries.map { group to it } }
            .filter { (group, entry) ->
                query == null ||
                    entry.name.contains(query, ignoreCase = true) ||
                    group.contains(query, ignoreCase = true) ||
                    entry.tel?.contains(query) == true ||
                    entry.tel2?.contains(query) == true
            }
            .take(limit)
        if (matches.isEmpty()) {
            return AssistantOutcome.Failure("phone_book_empty", "没有找到匹配的电话")
        }
        val lines = matches.map { (group, entry) ->
            buildString {
                append(group)
                append(" · ")
                append(entry.name)
                append(" · ")
                append(entry.tel.orEmpty())
                val second = entry.tel2
                if (!second.isNullOrBlank() && second != entry.tel) {
                    append(" / ")
                    append(second)
                }
            }
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "找到 ${matches.size} 个号码",
                lines = lines,
                route = spec.openRoute,
            )
        )
    }
}

class GetNoticesCapability(
    private val accountId: () -> String? = { SessionStore.currentUser()?.xh },
    private val open: (String) -> Unit = CampusNoticeRepository::open,
    private val sync: (Boolean) -> Unit = CampusNoticeRepository::requestSyncIfDue,
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "get_notices",
        title = "查询校园通知",
        description = "查询校园通知。source: all/university(官网)/jwc(教务处)/college(学院)/postgraduate(研究生院)。" +
            "默认读本地已同步的通知；refresh=true 时重新同步。",
        parameters = schema(
            "source" to stringProp(
                "来源：all/university/jwc/college/postgraduate，默认 all",
                enumValues = listOf("all", "university", "jwc", "college", "postgraduate"),
            ),
            "query" to stringProp("标题关键词"),
            "limit" to intProp("最多返回条数，默认 20", minimum = 1, maximum = 50),
            "refresh" to boolProp("是否强制重新同步，默认 false"),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "校园通知",
        openRoute = "campus_notices",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        val account = accountId()?.takeIf { it.isNotBlank() } ?: return notLoggedIn(spec)
        val refresh = arguments.boolArg("refresh", false)
        val limit = (arguments.intArg("limit") ?: 20).coerceIn(1, 50)
        val source = arguments.stringArg("source")?.takeIf { it != "all" }
        val query = arguments.stringArg("query")

        open(account)
        if (refresh) {
            sync(true)
            withTimeoutOrNull(SYNC_TIMEOUT_MS) {
                CampusNoticeRepository.syncingAccountId.first { it != account }
            }
        }
        val snapshot = CampusNoticeRepository.snapshot.value?.takeIf { it.accountId == account }
            ?: withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                CampusNoticeRepository.snapshot.first { it?.accountId == account }
            }
        if (snapshot == null) {
            return AssistantOutcome.Success(
                AssistantAnswer("通知还没同步过，打开通知页同步一次", route = spec.openRoute)
            )
        }
        val notices = snapshot.notices
            .filter { source == null || it.sourceId == source }
            .filter { query == null || it.title.contains(query, ignoreCase = true) }
            .sortedByDescending { it.publishedOn }
            .take(limit)
        val lines = notices.map { notice ->
            "${sourceLabel(notice.sourceId)} · ${notice.title} · ${notice.publishedOn}"
        }
        return AssistantOutcome.Success(
            AssistantAnswer(
                summary = "校园通知 ${notices.size} 条，未读 ${snapshot.unreadCount} 条",
                lines = lines,
                route = spec.openRoute,
                trust = AssistantTrust.UNTRUSTED,
            )
        )
    }

    private fun sourceLabel(sourceId: String): String = when (sourceId) {
        "university" -> "安徽大学"
        "jwc" -> "教务处"
        "college" -> "学院"
        "postgraduate" -> "研究生院"
        else -> sourceId
    }

    private companion object {
        const val SYNC_TIMEOUT_MS = 20_000L
        const val LOAD_TIMEOUT_MS = 2_500L
    }
}
