package com.ahu.ahutong.data.notice

import android.net.Uri
import android.os.Build
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Walks the graduate notice pages until a non-pinned, previously seen article is found. */
internal class PostgraduateNoticeCrawler(private val fetcher: NoticePageFetcher) : CampusNoticeSource {
    override val sourceId: String = PostgraduateNoticeParser.SOURCE_ID

    override suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice> {
        val visited = mutableSetOf<String>()
        val collected = linkedMapOf<String, CampusNotice>()
        var url: String? = PostgraduateNoticeParser.FIRST_PAGE
        var foundAnchor = false
        while (url != null && visited.size < MAX_PAGES) {
            if (!visited.add(url)) throw CampusNoticeProtocolException("研究生院公告分页出现循环")
            val page = PostgraduateNoticeParser.parse(fetcher.fetch(url), url)
            for ((index, notice) in page.notices.withIndex()) {
                // The first row is reserved for a rolling notice on the live site; it is not
                // a safe history boundary even if its published date is refreshed later.
                val possibleFirstPagePinned = visited.size == 1 && index == 0 && page.notices.size > 1
                val isOutOfOrderPinned = page.notices.drop(index + 1)
                    .any { it.publishedOn > notice.publishedOn }
                if (!firstSync && notice.articleId in seenIds && !possibleFirstPagePinned && !isOutOfOrderPinned) {
                    foundAnchor = true
                    break
                }
                if (notice.articleId !in seenIds) collected.putIfAbsent(notice.articleId, notice)
            }
            if (firstSync || foundAnchor) break
            url = page.nextPageUrl
        }
        if (!firstSync && !foundAnchor) {
            throw CampusNoticeProtocolException("未追溯到已见研究生院公告，已暂停同步以免把历史公告当新通知")
        }
        return collected.values.toList()
    }

    private companion object {
        const val MAX_PAGES = 50
    }
}

/** Only public list URLs enter a WebView process with a separate data directory. */
internal class PostgraduateNoticeWebFetcher : NoticePageFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        if (!PostgraduateNoticeParser.isTrustedListUrl(url)) {
            throw CampusNoticeProtocolException("研究生院公告请求地址无效")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            throw CampusNoticeProtocolException("当前系统不支持隔离网页数据，请在浏览器查看")
        }
        val context = AppEnvironmentHolder.context()
        val provider = Uri.parse("content://${context.packageName}.notice_web")
        val response = runCatching {
            context.contentResolver.call(provider, NoticeWebProvider.METHOD_FETCH, url, null)
        }.getOrElse { throw CampusNoticeProtocolException("研究生院公告网页启动失败") }
        val error = response?.getString(NoticeWebProvider.KEY_ERROR)
        if (error != null) {
            if (error.contains("HTTP 412")) throw CampusNoticeAccessBlockedException()
            throw CampusNoticeProtocolException(error)
        }
        response?.getString(NoticeWebProvider.KEY_HTML)?.takeIf(String::isNotBlank)
            ?: throw CampusNoticeProtocolException("研究生院公告网页没有返回列表")
    }
}
