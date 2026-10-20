package com.ahu.ahutong.data.notice

import android.net.Uri
import android.os.Build
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Walks the academic-affairs notice pages until a previously seen article is found. */
internal class AcademicAffairsNoticeCrawler(private val fetcher: NoticePageFetcher) : PagedCampusNoticeSource {
    override val sourceId: String = AcademicAffairsNoticeParser.SOURCE_ID

    override suspend fun fetchPage(pageUrl: String?): CampusNoticePage {
        val url = pageUrl ?: AcademicAffairsNoticeParser.FIRST_PAGE
        return AcademicAffairsNoticeParser.parse(fetcher.fetch(url), url)
    }

    override suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice> {
        val visited = mutableSetOf<String>()
        val collected = linkedMapOf<String, CampusNotice>()
        var url: String? = AcademicAffairsNoticeParser.FIRST_PAGE
        var foundKnown = false
        while (url != null && visited.size < MAX_PAGES) {
            if (!visited.add(url)) throw CampusNoticeProtocolException("教务处通知分页出现循环")
            val page = AcademicAffairsNoticeParser.parse(fetcher.fetch(url), url)
            for (notice in page.notices) {
                if (!firstSync && notice.articleId in seenIds) {
                    foundKnown = true
                    break
                }
                collected.putIfAbsent(notice.articleId, notice)
            }
            if (firstSync || foundKnown) break
            url = page.nextPageUrl
        }
        if (!firstSync && !foundKnown) {
            throw CampusNoticeProtocolException("未追溯到已见教务处通知，已暂停同步以免把历史公告当新通知")
        }
        return collected.values.toList()
    }

    private companion object {
        const val MAX_PAGES = 50
    }
}

/** Renders the WAF-protected public list in the isolated WebView process, never with login cookies. */
internal class AcademicAffairsNoticeWebFetcher : NoticePageFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (!AcademicAffairsNoticeParser.isTrustedListUrl(url)) {
            throw CampusNoticeProtocolException("教务处通知请求地址无效")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw CampusNoticeProtocolException("当前系统不支持隔离网页数据，请在浏览器查看")
        }
        val context = AppEnvironmentHolder.context()
        val provider = Uri.parse("content://${context.packageName}.notice_web")
        val response = runCatching {
            context.contentResolver.call(provider, NoticeWebProvider.METHOD_FETCH, url, null)
        }.getOrElse { throw CampusNoticeProtocolException("教务处通知网页启动失败") }
        val error = response?.getString(NoticeWebProvider.KEY_ERROR)
        if (error != null) throw CampusNoticeProtocolException(error)
        response?.getString(NoticeWebProvider.KEY_HTML)?.takeIf(String::isNotBlank)
            ?: throw CampusNoticeProtocolException("教务处通知网页没有返回列表")
    }
}
