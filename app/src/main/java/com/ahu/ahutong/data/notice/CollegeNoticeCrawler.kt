package com.ahu.ahutong.data.notice

import android.net.Uri
import android.os.Build
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用户所在学院的动态通知源。栏目由 [CollegeNoticeRegistry] 根据学籍院系解析，
 * 只拉列表第一页（增量刷新）；历史页由已有的滑到底加载游标按需回翻。
 */
internal class CollegeNoticeSource(
    private val configProvider: () -> CollegeNoticeConfig?,
    private val fetcher: NoticePageFetcher
) : PagedCampusNoticeSource, ScopedCampusNoticeSource {

    override val sourceId: String = CollegeNoticeRegistry.SOURCE_ID

    override fun currentScopeKey(): String? = configProvider()?.key

    override suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice> {
        val page = fetchPage(null)
        if (firstSync) return page.notices
        return page.notices.filterNot { it.articleId in seenIds }
    }

    override suspend fun fetchPage(pageUrl: String?): CampusNoticePage {
        val config = configProvider()
            ?: throw CampusNoticeProtocolException("尚未匹配到用户所在学院")
        val url = pageUrl ?: config.listUrl
        if (CollegeNoticeRegistry.configForListUrl(url) != config) {
            throw CampusNoticeProtocolException("学院通知分页不属于当前学院栏目")
        }
        return CollegeNoticeParser.parse(fetcher.fetch(url), url, config)
    }
}

/** WAF 后的完整 HTML；解析与分页判断留给 [CollegeNoticeParser]。 */
internal class CollegeNoticeWebFetcher : NoticePageFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (CollegeNoticeRegistry.configForListUrl(url) == null) {
            throw CampusNoticeProtocolException("学院通知请求地址不在配置栏目内")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw CampusNoticeProtocolException("当前系统不支持隔离网页数据，请在浏览器查看")
        }
        val context = AppEnvironmentHolder.context()
        val provider = Uri.parse("content://${context.packageName}.notice_web")
        val response = runCatching {
            context.contentResolver.call(provider, NoticeWebProvider.METHOD_FETCH_HTML, url, null)
        }.getOrElse { throw CampusNoticeProtocolException("学院通知网页启动失败") }
        val error = response?.getString(NoticeWebProvider.KEY_ERROR)
        if (error != null) {
            if (error.contains("HTTP 412")) throw CampusNoticeAccessBlockedException()
            throw CampusNoticeProtocolException(error)
        }
        response?.getString(NoticeWebProvider.KEY_HTML)?.takeIf(String::isNotBlank)
            ?: throw CampusNoticeProtocolException("学院通知网页没有返回列表")
    }
}
