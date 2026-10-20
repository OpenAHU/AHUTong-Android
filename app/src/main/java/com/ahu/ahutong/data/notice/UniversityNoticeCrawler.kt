package com.ahu.ahutong.data.notice

import com.ahu.ahutong.data.network.AhuHttp
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

internal fun interface NoticePageFetcher {
    suspend fun fetch(url: String): String
}

internal class UniversityNoticeCrawler(private val fetcher: NoticePageFetcher) : PagedCampusNoticeSource {
    override val sourceId: String = UniversityNoticeParser.SOURCE_ID

    override suspend fun fetchPage(pageUrl: String?): CampusNoticePage {
        val url = pageUrl ?: UniversityNoticeParser.FIRST_PAGE
        return UniversityNoticeParser.parse(fetcher.fetch(url), url)
    }

    /** On first use, establish one current page as read history; later walk until a known ID. */
    override suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice> {
        val visited = mutableSetOf<String>()
        val collected = linkedMapOf<String, CampusNotice>()
        var url: String? = UniversityNoticeParser.FIRST_PAGE
        var foundKnown = false
        while (url != null && visited.size < MAX_PAGES) {
            if (!visited.add(url)) throw CampusNoticeProtocolException("校级通知分页出现循环")
            val page = UniversityNoticeParser.parse(fetcher.fetch(url), url)
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
            throw CampusNoticeProtocolException("未追溯到已见公告，已暂停同步以免把历史公告当新通知")
        }
        return collected.values.toList()
    }

    private companion object {
        const val MAX_PAGES = 50
    }
}

/** Public university pages only; never attaches campus authentication cookies. */
internal class UniversityNoticeHttpFetcher : NoticePageFetcher {
    private val client by lazy {
        AhuHttp.plain(connectTimeoutSeconds = 10, readTimeoutSeconds = 20, callTimeoutSeconds = 25)
            .build()
    }

    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val address = URI(url)
        if (address.scheme != "https" || address.host != "www.ahu.edu.cn" ||
            !Regex("/15046/list\\d*\\.htm").matches(address.path)
        ) throw CampusNoticeProtocolException("公告请求地址不在校官网白名单内")
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code == 412) throw CampusNoticeAccessBlockedException()
            if (!response.isSuccessful) throw IOException("校官网公告请求失败（HTTP ${response.code}）")
            if (response.request.url.host != "www.ahu.edu.cn" ||
                response.request.url.scheme != "https" ||
                !Regex("/15046/list\\d*\\.htm").matches(response.request.url.encodedPath)
            ) {
                throw CampusNoticeProtocolException("校官网公告跳转到非受信任站点")
            }
            response.body?.string()?.takeIf(String::isNotBlank)
                ?: throw CampusNoticeProtocolException("校官网公告返回空页面")
        }
    }
}
