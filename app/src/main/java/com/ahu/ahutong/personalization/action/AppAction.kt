package com.ahu.ahutong.personalization.action

enum class ActionFamily {
    NAVIGATION,
    ACADEMIC,
    CAMPUS_SERVICE,
    REPOSITORY,
    PAYMENT_ENTRY,
    TRANSACTION,
    SETTINGS,
    DATA_OPERATION,
    TECHNICAL
}

enum class SideEffect {
    NONE,
    READ_ONLY,
    SENSITIVE_READ,
    REVERSIBLE_WRITE,
    TRANSACTION
}

enum class Sensitivity {
    NORMAL,
    ACADEMIC,
    FINANCIAL,
    AUTHENTICATION
}

enum class PrefetchPolicy {
    NONE,
    LOCAL_ONLY,
    NETWORK_READ_ONLY,
    SENSITIVE_MEMORY_ONLY
}

enum class PredictionMilestone {
    ACTION_INTENT_ACCEPTED
}


data class AppActionSpec(
    val id: AppActionId,
    val family: ActionFamily,
    val route: String?,
    val labelEligible: Boolean = true,
    val predictable: Boolean = true,
    val predictionMilestone: PredictionMilestone? = PredictionMilestone.ACTION_INTENT_ACCEPTED,
    val suggestible: Boolean = true,
    val prefetchPolicy: PrefetchPolicy = PrefetchPolicy.NONE,
    val sideEffect: SideEffect = SideEffect.NONE,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val title: String,
    val reasonLabel: String
)

object AppActionCatalog {
    const val ACTION_CATALOG_VERSION = 1
    const val OUTPUT_SCHEMA_VERSION = 1
    const val OTHER_OUTPUT_ID = "OTHER"
    const val NONE_OUTPUT_ID = "NONE"

    private fun spec(
        id: AppActionId,
        family: ActionFamily,
        route: String? = null,
        title: String,
        reason: String = "符合你的使用习惯",
        labelEligible: Boolean = true,
        predictable: Boolean = true,
        suggestible: Boolean = true,
        prefetch: PrefetchPolicy = PrefetchPolicy.NONE,
        sideEffect: SideEffect = SideEffect.NONE,
        sensitivity: Sensitivity = Sensitivity.NORMAL
    ) = AppActionSpec(
        id = id,
        family = family,
        route = route,
        labelEligible = labelEligible,
        predictable = predictable,
        predictionMilestone = if (predictable) PredictionMilestone.ACTION_INTENT_ACCEPTED else null,
        suggestible = suggestible,
        prefetchPolicy = prefetch,
        sideEffect = sideEffect,
        sensitivity = sensitivity,
        title = title,
        reasonLabel = reason
    )

    val specs: List<AppActionSpec> = listOf(
        spec(AppActionId.OPEN_HOME, ActionFamily.NAVIGATION, "home", "主页"),
        spec(AppActionId.VIEW_SCHEDULE, ActionFamily.ACADEMIC, "schedule", "课表", "你经常在这个时间查看", prefetch = PrefetchPolicy.NETWORK_READ_ONLY, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.OPEN_TOOLS, ActionFamily.NAVIGATION, "tools", "小工具"),
        spec(AppActionId.OPEN_SETTINGS, ActionFamily.SETTINGS, "settings", "设置"),
        spec(AppActionId.VIEW_SCHOOL_CALENDAR, ActionFamily.ACADEMIC, "school_calendar", "校历", prefetch = PrefetchPolicy.NETWORK_READ_ONLY, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.VIEW_GRADES, ActionFamily.ACADEMIC, "grade", "成绩单", prefetch = PrefetchPolicy.NETWORK_READ_ONLY, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.OPEN_PHONE_BOOK, ActionFamily.CAMPUS_SERVICE, "phone_book", "电话本"),
        spec(AppActionId.VIEW_EXAM_ROOM, ActionFamily.ACADEMIC, "exam", "考场查询", "考试临近，你最近常用此功能", prefetch = PrefetchPolicy.NETWORK_READ_ONLY, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.OPEN_EVALUATION, ActionFamily.ACADEMIC, "evaluation", "教评", sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.FIND_FREE_CLASSROOM, ActionFamily.ACADEMIC, "free_classroom", "空闲教室", sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.OPEN_LOST_FOUND, ActionFamily.CAMPUS_SERVICE, "lost_found", "失物招领", prefetch = PrefetchPolicy.NETWORK_READ_ONLY, sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.VIEW_WEATHER, ActionFamily.CAMPUS_SERVICE, "weather", "天气", sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.OPEN_PAYMENT_QR, ActionFamily.CAMPUS_SERVICE, null, "付款码", "你经常在这个时间使用付款码", prefetch = PrefetchPolicy.SENSITIVE_MEMORY_ONLY, sideEffect = SideEffect.SENSITIVE_READ, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.REFRESH_PAYMENT_QR, ActionFamily.DATA_OPERATION, null, "刷新付款码", suggestible = false, sideEffect = SideEffect.SENSITIVE_READ, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.OPEN_REPOSITORY, ActionFamily.REPOSITORY, "repository", "学习资料", sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.OPEN_REPOSITORY_DIRECTORY, ActionFamily.REPOSITORY, "repository/{path}", "资料目录", suggestible = false, sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.OPEN_REPOSITORY_DOWNLOADS, ActionFamily.REPOSITORY, "repository_downloads", "下载记录"),
        spec(AppActionId.OPEN_REPOSITORY_SETTINGS, ActionFamily.REPOSITORY, "repository_settings", "资料设置"),
        spec(AppActionId.OPEN_REPOSITORY_ITEM, ActionFamily.REPOSITORY, null, "打开资料", suggestible = false, sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.DOWNLOAD_REPOSITORY_ITEM, ActionFamily.REPOSITORY, null, "下载资料", suggestible = false, sideEffect = SideEffect.REVERSIBLE_WRITE),
        spec(AppActionId.OPEN_BATHROOM_DEPOSIT, ActionFamily.PAYMENT_ENTRY, "bathroom_deposit", "浴室缴费", sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.OPEN_ELECTRICITY_PAYMENT, ActionFamily.PAYMENT_ENTRY, "electricity_pay", "电控缴费", sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.OPEN_CARD_RECHARGE, ActionFamily.PAYMENT_ENTRY, "card_balance_deposit", "校园卡充值", "你的余额可能需要补充", sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.OPEN_CMB_CARD_RECHARGE, ActionFamily.PAYMENT_ENTRY, "cmb_card_recharge", "招商银行充值", sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.OPEN_NETWORK_RECHARGE, ActionFamily.PAYMENT_ENTRY, "network_recharge", "网费充值", sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.CONFIRM_BATHROOM_PAYMENT, ActionFamily.TRANSACTION, null, "确认浴室缴费", suggestible = false, sideEffect = SideEffect.TRANSACTION, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.CONFIRM_ELECTRICITY_PAYMENT, ActionFamily.TRANSACTION, null, "确认电费缴费", suggestible = false, sideEffect = SideEffect.TRANSACTION, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.SUBMIT_CARD_RECHARGE, ActionFamily.TRANSACTION, null, "提交校园卡充值", suggestible = false, sideEffect = SideEffect.TRANSACTION, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.SUBMIT_CMB_CARD_RECHARGE, ActionFamily.TRANSACTION, null, "提交招商银行充值", suggestible = false, sideEffect = SideEffect.TRANSACTION, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.SUBMIT_NETWORK_RECHARGE, ActionFamily.TRANSACTION, null, "提交网费充值", suggestible = false, sideEffect = SideEffect.TRANSACTION, sensitivity = Sensitivity.FINANCIAL),
        spec(AppActionId.OPEN_PREFERENCES, ActionFamily.SETTINGS, "preferences", "偏好设置"),
        spec(AppActionId.OPEN_LICENSES, ActionFamily.SETTINGS, "settings__license", "开源许可"),
        spec(AppActionId.OPEN_CONTRIBUTORS, ActionFamily.SETTINGS, "settings__contributors", "贡献者"),
        spec(AppActionId.OPEN_INFO, ActionFamily.SETTINGS, "info", "个人信息"),
        spec(AppActionId.EDIT_HOME, ActionFamily.SETTINGS, null, "编辑主页"),
        spec(AppActionId.MANUAL_REFRESH_SCHEDULE, ActionFamily.DATA_OPERATION, null, "刷新课表", suggestible = false, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.MANUAL_REFRESH_EXAM, ActionFamily.DATA_OPERATION, null, "刷新考场", suggestible = false, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.MANUAL_REFRESH_GRADE, ActionFamily.DATA_OPERATION, null, "刷新成绩", suggestible = false, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.MANUAL_REFRESH_REPOSITORY, ActionFamily.DATA_OPERATION, null, "刷新学习资料", suggestible = false, sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.RETRY_GRADE, ActionFamily.DATA_OPERATION, null, "重试成绩查询", suggestible = false, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.RETRY_EXAM, ActionFamily.DATA_OPERATION, null, "重试考场查询", suggestible = false, sideEffect = SideEffect.READ_ONLY, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.RETRY_REPOSITORY, ActionFamily.DATA_OPERATION, null, "重试资料加载", suggestible = false, sideEffect = SideEffect.READ_ONLY),
        spec(AppActionId.OPEN_COURSE_DETAIL, ActionFamily.ACADEMIC, null, "课程详情", suggestible = false, sensitivity = Sensitivity.ACADEMIC),
        spec(AppActionId.LOGIN, ActionFamily.TECHNICAL, "login", "登录", labelEligible = false, predictable = false, suggestible = false, sensitivity = Sensitivity.AUTHENTICATION),
        spec(AppActionId.SETUP, ActionFamily.TECHNICAL, "setup", "初始化", labelEligible = false, predictable = false, suggestible = false),
        spec(AppActionId.SPLASH, ActionFamily.TECHNICAL, "splash", "启动", labelEligible = false, predictable = false, suggestible = false)
    )

    private val specById = specs.associateBy(AppActionSpec::id)
    private val specByRoute = specs.mapNotNull { value -> value.route?.let { it to value } }.toMap()
    private val routeAliases = mapOf(
        "electricity_recent_rooms" to AppActionId.OPEN_ELECTRICITY_PAYMENT,
        "electricity_alert_settings" to AppActionId.OPEN_ELECTRICITY_PAYMENT,
        "xuexiaotong" to AppActionId.VIEW_SCHOOL_CALENDAR,
        "widgets" to AppActionId.OPEN_TOOLS
    )
    private val commandRoutePrefixes: Map<AppActionId, Set<String>> = mapOf(
        AppActionId.OPEN_PAYMENT_QR to setOf("home"),
        AppActionId.REFRESH_PAYMENT_QR to setOf("home"),
        AppActionId.OPEN_REPOSITORY_ITEM to setOf("repository"),
        AppActionId.DOWNLOAD_REPOSITORY_ITEM to setOf("repository"),
        AppActionId.CONFIRM_BATHROOM_PAYMENT to setOf("bathroom_deposit"),
        AppActionId.CONFIRM_ELECTRICITY_PAYMENT to setOf("electricity_pay"),
        AppActionId.SUBMIT_CARD_RECHARGE to setOf("card_balance_deposit"),
        AppActionId.SUBMIT_CMB_CARD_RECHARGE to setOf("card_balance_deposit", "cmb_card_recharge"),
        AppActionId.SUBMIT_NETWORK_RECHARGE to setOf("network_recharge"),
        AppActionId.EDIT_HOME to setOf("home"),
        AppActionId.MANUAL_REFRESH_SCHEDULE to setOf("schedule"),
        AppActionId.MANUAL_REFRESH_EXAM to setOf("exam"),
        AppActionId.MANUAL_REFRESH_GRADE to setOf("grade"),
        AppActionId.MANUAL_REFRESH_REPOSITORY to setOf("repository"),
        AppActionId.RETRY_GRADE to setOf("grade"),
        AppActionId.RETRY_EXAM to setOf("exam"),
        AppActionId.RETRY_REPOSITORY to setOf("repository"),
        AppActionId.OPEN_COURSE_DETAIL to setOf("schedule")
    )

    // This is the serialized output ABI. Never derive it from enum/spec iteration order.
    private val frozenPredictableActions = listOf(
        AppActionId.OPEN_HOME,
        AppActionId.VIEW_SCHEDULE,
        AppActionId.OPEN_TOOLS,
        AppActionId.OPEN_SETTINGS,
        AppActionId.VIEW_SCHOOL_CALENDAR,
        AppActionId.VIEW_GRADES,
        AppActionId.OPEN_PHONE_BOOK,
        AppActionId.VIEW_EXAM_ROOM,
        AppActionId.OPEN_EVALUATION,
        AppActionId.FIND_FREE_CLASSROOM,
        AppActionId.OPEN_LOST_FOUND,
        AppActionId.VIEW_WEATHER,
        AppActionId.OPEN_PAYMENT_QR,
        AppActionId.REFRESH_PAYMENT_QR,
        AppActionId.OPEN_REPOSITORY,
        AppActionId.OPEN_REPOSITORY_DIRECTORY,
        AppActionId.OPEN_REPOSITORY_DOWNLOADS,
        AppActionId.OPEN_REPOSITORY_SETTINGS,
        AppActionId.OPEN_REPOSITORY_ITEM,
        AppActionId.DOWNLOAD_REPOSITORY_ITEM,
        AppActionId.OPEN_BATHROOM_DEPOSIT,
        AppActionId.OPEN_ELECTRICITY_PAYMENT,
        AppActionId.OPEN_CARD_RECHARGE,
        AppActionId.OPEN_CMB_CARD_RECHARGE,
        AppActionId.OPEN_NETWORK_RECHARGE,
        AppActionId.CONFIRM_BATHROOM_PAYMENT,
        AppActionId.CONFIRM_ELECTRICITY_PAYMENT,
        AppActionId.SUBMIT_CARD_RECHARGE,
        AppActionId.SUBMIT_CMB_CARD_RECHARGE,
        AppActionId.SUBMIT_NETWORK_RECHARGE,
        AppActionId.OPEN_PREFERENCES,
        AppActionId.OPEN_LICENSES,
        AppActionId.OPEN_CONTRIBUTORS,
        AppActionId.OPEN_INFO,
        AppActionId.EDIT_HOME,
        AppActionId.MANUAL_REFRESH_SCHEDULE,
        AppActionId.MANUAL_REFRESH_EXAM,
        AppActionId.MANUAL_REFRESH_GRADE,
        AppActionId.MANUAL_REFRESH_REPOSITORY,
        AppActionId.RETRY_GRADE,
        AppActionId.RETRY_EXAM,
        AppActionId.RETRY_REPOSITORY,
        AppActionId.OPEN_COURSE_DETAIL
    )

    val outputIds: List<String> = frozenPredictableActions.map(AppActionId::stableId) +
        OTHER_OUTPUT_ID + NONE_OUTPUT_ID
    val outputIndex: Map<String, Int> = outputIds.withIndex().associate { it.value to it.index }

    val navigationRouteManifest: Set<String> = setOf(
        "home", "setup", "login", "info", "schedule", "tools", "school_calendar", "grade",
        "phone_book", "exam", "evaluation", "free_classroom", "lost_found", "weather",
        "repository", "repository/{path}", "repository_downloads", "repository_settings", "settings",
        "settings__license", "settings__contributors", "preferences", "electricity_pay",
        "card_balance_deposit", "bathroom_deposit", "cmb_card_recharge", "network_recharge",
        "electricity_recent_rooms", "electricity_alert_settings", "xuexiaotong", "widgets",
        "splash"
    )

    init {
        check(specs.map { it.id }.toSet().size == specs.size)
        check(outputIds.toSet().size == outputIds.size)
        check(frozenPredictableActions.toSet() == specs.filter(AppActionSpec::predictable).map(AppActionSpec::id).toSet())
        check(specs.filter(AppActionSpec::predictable).all { it.predictionMilestone != null })
        check(specs.filter { it.sideEffect == SideEffect.TRANSACTION }.none(AppActionSpec::suggestible))
        check(navigationRouteManifest.all { actionForRoute(it) != null })
        check(
            specs.filter { it.predictable && it.route == null }.map(AppActionSpec::id).toSet() ==
                commandRoutePrefixes.keys
        ) { "every non-route predictable action needs an objective availability rule" }
    }

    fun spec(id: AppActionId): AppActionSpec = requireNotNull(specById[id])

    fun actionForRoute(route: String?): AppActionId? {
        if (route == null) return null
        specByRoute[route]?.let { return it.id }
        routeAliases[route]?.let { return it }
        if (route.startsWith("repository/")) return AppActionId.OPEN_REPOSITORY_DIRECTORY
        return null
    }

    fun outputIndex(id: AppActionId): Int? = outputIndex[id.stableId]

    fun businessAvailability(route: String?): BooleanArray = BooleanArray(outputIds.size) { index ->
        val stableId = outputIds[index]
        if (stableId == OTHER_OUTPUT_ID || stableId == NONE_OUTPUT_ID) return@BooleanArray true
        val action = AppActionId.fromStableId(stableId) ?: return@BooleanArray false
        val spec = spec(action)
        if (spec.route != null) {
            route == null || actionForRoute(route) != action
        } else {
            val current = route ?: return@BooleanArray false
            commandRoutePrefixes.getValue(action).any { prefix ->
                current == prefix || current.startsWith("$prefix/")
            }
        }
    }
}
