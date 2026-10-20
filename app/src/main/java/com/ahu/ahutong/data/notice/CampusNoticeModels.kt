package com.ahu.ahutong.data.notice

data class CampusNotice(
    val sourceId: String,
    val articleId: String,
    val title: String,
    val publishedOn: String,
    val originalUrl: String,
    val discoveredAtMillis: Long,
    val read: Boolean
)

data class CampusNoticeSourceStatus(
    val sourceId: String,
    val lastSuccessfulSyncDate: String? = null,
    val lastSuccessfulSyncAtMillis: Long? = null,
    val lastError: String? = null,
    /** 历史浏览游标：已同步到的最新列表页之后，下一页的地址。 */
    val nextPageUrl: String? = null,
    /** 历史列表已经翻到底。 */
    val historyExhausted: Boolean = false,
    /** 历史翻页失败原因；与增量同步的 [lastError] 分开，避免互相覆盖。 */
    val historyError: String? = null,
    /** 动态信源的作用域（学院源用于识别院系切换）。 */
    val scopeKey: String? = null
)

data class CampusNoticeSnapshot(
    val accountId: String,
    val notices: List<CampusNotice> = emptyList(),
    val sourceStatuses: Map<String, CampusNoticeSourceStatus> = emptyMap(),
    val notificationsEnabled: Boolean = false
) {
    val unreadCount: Int get() = notices.count { !it.read }
}

internal data class CampusNoticePage(
    val notices: List<CampusNotice>,
    val nextPageUrl: String?
)

internal class CampusNoticeProtocolException(message: String) : java.io.IOException(message)
internal class CampusNoticeAccessBlockedException : java.io.IOException("公告网站返回 HTTP 412，暂不支持自动同步")

internal object CampusNoticeUrlPolicy {
    fun isTrustedArticle(sourceId: String, url: String?): Boolean = when (sourceId) {
        UniversityNoticeParser.SOURCE_ID -> UniversityNoticeParser.isTrustedArticleUrl(url)
        AcademicAffairsNoticeParser.SOURCE_ID -> AcademicAffairsNoticeParser.isTrustedArticleUrl(url)
        PostgraduateNoticeParser.SOURCE_ID -> PostgraduateNoticeParser.isTrustedArticleUrl(url)
        CollegeNoticeRegistry.SOURCE_ID -> CollegeNoticeRegistry.isTrustedArticleUrl(url)
        else -> false
    }
}

internal fun noticeSourceLabel(sourceId: String): String = when (sourceId) {
    AcademicAffairsNoticeParser.SOURCE_ID -> "教务处"
    PostgraduateNoticeParser.SOURCE_ID -> "研究生院"
    CollegeNoticeRegistry.SOURCE_ID -> "学院"
    UniversityNoticeParser.SOURCE_ID -> "安徽大学"
    else -> "校园通知"
}
