package com.ahu.ahutong.data.notice

import java.net.URI
import java.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 学院列表页解析器。36 个教学单位共用苏迪模板，但列表有 7 种变体；
 * 按报告 §3.1 的顺序级联探测，命中即止。只解析列表，详情页交给浏览器。
 */
internal object CollegeNoticeParser {

    fun parse(html: String, pageUrl: String, config: CollegeNoticeConfig): CampusNoticePage {
        val pageUri = runCatching { URI(pageUrl) }.getOrNull()
            ?: throw CampusNoticeProtocolException("学院通知列表地址无效")
        if (pageUri.scheme != "https" || pageUri.host != config.host ||
            !config.listPathPattern.matches(pageUri.path)
        ) throw CampusNoticeProtocolException("学院通知列表地址不在配置栏目内")

        val document = Jsoup.parse(html, pageUrl)
        val rawRows = detectRows(document)
        if (rawRows.isEmpty()) throw CampusNoticeProtocolException("学院通知列表结构已变化")

        val notices = rawRows.mapNotNull { row ->
            val url = resolveDetailUrl(row.href, config.host, pageUrl) ?: return@mapNotNull null
            val date = parseDate(row.date) ?: return@mapNotNull null
            val title = row.title.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val articleKey = Regex("""c\d+a\d+""").find(url)?.value ?: url
            CampusNotice(
                sourceId = CollegeNoticeRegistry.SOURCE_ID,
                articleId = articleKey,
                title = title,
                publishedOn = date,
                originalUrl = url,
                discoveredAtMillis = 0,
                read = false
            )
        }
        if (notices.isEmpty()) throw CampusNoticeProtocolException("学院通知列表没有可解析的条目")

        val nextHref = document.selectFirst("ul.wp_paging a.next[href]")?.attr("href")
            ?.takeUnless { it.startsWith("javascript:", ignoreCase = true) || it == "#" }
        val nextPageUrl = nextHref?.let { resolveNextPage(it, pageUrl, config) }

        return CampusNoticePage(notices.distinctBy(CampusNotice::articleId), nextPageUrl)
    }

    private data class RawRow(val href: String, val title: String, val date: String)

    private fun detectRows(document: Document): List<RawRow> {
        return variantA(document)
            .ifEmpty { variantB(document) }
            .ifEmpty { variantD(document) }
            .ifEmpty { variantE(document) }
            .ifEmpty { variantF(document) }
            .ifEmpty { variantG(document) }
            .ifEmpty { variantH(document) }
    }

    private fun variantA(document: Document): List<RawRow> =
        document.select("ul.news_list li.news").mapNotNull { row ->
            val link = row.selectFirst("span.news_title > a[href]") ?: return@mapNotNull null
            RawRow(
                href = link.attr("href"),
                title = link.attr("title").ifBlank { link.text() },
                date = row.selectFirst("span.news_meta")?.text().orEmpty()
            )
        }

    private fun variantB(document: Document): List<RawRow> =
        document.select("ul.wp_article_list li.list_item").mapNotNull { row ->
            val link = row.selectFirst("span.Article_Title > a[href]") ?: return@mapNotNull null
            RawRow(
                href = link.attr("href"),
                title = link.attr("title").ifBlank { link.text() },
                date = row.selectFirst("span.Article_PublishDate")?.text().orEmpty()
            )
        }

    private fun variantD(document: Document): List<RawRow> =
        document.select(".list_box li").mapNotNull { row ->
            val link = row.selectFirst("span.bt_tt > a[href]") ?: return@mapNotNull null
            RawRow(
                href = link.attr("href"),
                title = link.attr("title").ifBlank { link.text() },
                date = row.selectFirst("span.info")?.text().orEmpty()
            )
        }

    private fun variantE(document: Document): List<RawRow> =
        document.select("div.inew > div.inewi").mapNotNull { row ->
            val link = row.selectFirst("a[href]") ?: return@mapNotNull null
            val yearMonth = row.selectFirst(".inewilb")?.text().orEmpty().trim()
            val day = row.selectFirst(".inewila")?.text().orEmpty().trim()
            RawRow(
                href = link.attr("href"),
                title = row.selectFirst(".inewira")?.text().orEmpty(),
                date = if (yearMonth.isNotBlank() && day.isNotBlank()) "$yearMonth-$day" else yearMonth
            )
        }

    private fun variantF(document: Document): List<RawRow> =
        document.select(".science-r-m div[id^=wp_news_] ul.fixed > li:not(.la1)").mapNotNull { row ->
            val link = row.children().firstOrNull { it.tagName() == "a" } ?: row.selectFirst("a[href]")
                ?: return@mapNotNull null
            RawRow(
                href = link.attr("href"),
                title = link.text(),
                date = row.children().firstOrNull { it.tagName() == "em" }?.text().orEmpty()
            )
        }

    private fun variantG(document: Document): List<RawRow> =
        document.select("div.newsList ul li").mapNotNull { row ->
            val link = row.selectFirst("a.flex[href]") ?: return@mapNotNull null
            RawRow(
                href = link.attr("href"),
                title = link.selectFirst("h3.eclips")?.text().orEmpty(),
                date = link.selectFirst("span")?.text().orEmpty()
            )
        }

    private fun variantH(document: Document): List<RawRow> =
        document.select("div.col_news_list div.jzlb").mapNotNull { row ->
            val link = row.selectFirst("div.btt3 > a[href]") ?: return@mapNotNull null
            RawRow(
                href = link.attr("href"),
                title = link.text(),
                date = row.selectFirst("div.fbsj4")?.text().orEmpty()
            )
        }

    private fun parseDate(raw: String): String? {
        val match = Regex("""\d{4}-\d{2}-\d{2}""").find(raw)?.value ?: return null
        return runCatching { LocalDate.parse(match).toString() }.getOrNull()
    }

    private fun resolveDetailUrl(href: String, host: String, pageUrl: String): String? {
        val value = href.trim()
        if (value.isEmpty() || value.startsWith("javascript:", ignoreCase = true)) return null
        val uri = runCatching {
            when {
                value.startsWith("https://", ignoreCase = true) ||
                    value.startsWith("http://", ignoreCase = true) -> URI(value)
                value.startsWith("//") -> URI("https:$value")
                value.startsWith("/") -> URI("https://$host$value")
                else -> URI(pageUrl).resolve(value)
            }
        }.getOrNull() ?: return null
        if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
        return if (uri.host == host) {
            URI("https", null, host, -1, uri.path, uri.query, null).toString()
        } else {
            uri.toString()
        }
    }

    private fun resolveNextPage(href: String, pageUrl: String, config: CollegeNoticeConfig): String {
        val uri = runCatching { URI(pageUrl).resolve(href.trim()).normalize() }.getOrNull()
            ?: throw CampusNoticeProtocolException("学院通知分页链接无效")
        if (uri.scheme != "https" || uri.host != config.host ||
            !config.listPathPattern.matches(uri.path)
        ) throw CampusNoticeProtocolException("学院通知分页超出配置栏目")
        return URI("https", null, uri.host, -1, uri.path, null, null).toString()
    }
}
