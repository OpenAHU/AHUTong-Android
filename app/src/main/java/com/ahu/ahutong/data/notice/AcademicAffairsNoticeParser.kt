package com.ahu.ahutong.data.notice

import java.net.URI
import java.time.LocalDate
import org.jsoup.Jsoup

/**
 * 教务处「通知公告」栏目（jwc.ahu.edu.cn/10314）。
 *
 * 与校级通知共用同一套苏迪模板，但站点前置了 JS WAF：列表页必须由 WebView 取回后
 * 再交给这个解析器，不能直接走 OkHttp（裸请求固定 412）。
 */
internal object AcademicAffairsNoticeParser {
    const val SOURCE_ID = "jwc"
    const val FIRST_PAGE = "https://jwc.ahu.edu.cn/10314/list.htm"
    private val listPath = Regex("/10314/list\\d*\\.htm")
    private val articlePath = Regex("/\\d{4}/\\d{4}/c10314a\\d+/page\\.htm")
    private val articleKey = Regex("c10314a\\d+")

    fun isTrustedListUrl(raw: String?): Boolean = trustedUrl(raw, listPath)
    fun isTrustedArticleUrl(raw: String?): Boolean = trustedUrl(raw, articlePath)

    private fun trustedUrl(raw: String?, path: Regex): Boolean = runCatching {
        val url = URI(raw)
        url.scheme == "https" && url.host == "jwc.ahu.edu.cn" &&
            url.userInfo == null && url.port == -1 && path.matches(url.path)
    }.getOrDefault(false)

    fun parse(html: String, pageUrl: String): CampusNoticePage {
        if (!isTrustedListUrl(pageUrl)) throw CampusNoticeProtocolException("教务处通知列表地址无效")
        val document = Jsoup.parse(html, pageUrl)
        val rows = document.select("ul.news_list.list2 > li.news")
        if (rows.isEmpty()) throw CampusNoticeProtocolException("教务处通知列表结构已变化")

        val notices = rows.map { row ->
            val link = row.selectFirst("span.news_title a[href]")
                ?: throw CampusNoticeProtocolException("教务处通知缺少文章链接")
            val title = link.attr("title").ifBlank { link.text() }.trim()
                .takeIf(String::isNotBlank)
                ?: throw CampusNoticeProtocolException("教务处通知缺少标题")
            val date = row.selectFirst("span.news_meta")?.text()?.trim()
                ?: throw CampusNoticeProtocolException("教务处通知缺少日期")
            runCatching { LocalDate.parse(date) }
                .getOrElse { throw CampusNoticeProtocolException("教务处通知日期格式已变化") }
            val url = normalizedUrl(pageUrl, link.attr("href"), articlePath)
            val id = articleKey.find(URI(url).path)?.value
                ?: throw CampusNoticeProtocolException("教务处通知文章 ID 无效")
            CampusNotice(SOURCE_ID, id, title, date, url, discoveredAtMillis = 0, read = false)
        }

        val nextHref = document.selectFirst("li.page_nav a.next[href]")?.attr("href")
            ?.takeUnless { it.startsWith("javascript:", ignoreCase = true) || it == "#" }
        val next = nextHref?.let { normalizedUrl(pageUrl, it, listPath) }
        return CampusNoticePage(notices.distinctBy(CampusNotice::articleId), next)
    }

    private fun normalizedUrl(base: String, href: String, allowedPath: Regex): String {
        val url = runCatching { URI(base).resolve(href.trim()).normalize() }.getOrNull()
            ?: throw CampusNoticeProtocolException("教务处通知链接无效")
        if (url.scheme != "https" || url.host != "jwc.ahu.edu.cn" ||
            url.userInfo != null || url.port != -1 || !allowedPath.matches(url.path)
        ) throw CampusNoticeProtocolException("教务处通知链接超出受信任栏目")
        return URI(url.scheme, null, url.host, -1, url.path, null, null).toString()
    }
}
