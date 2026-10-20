package com.ahu.ahutong.data.notice

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicAffairsNoticeCrawlerTest {
    private fun article(number: Int, title: String = "通知$number"): String =
        """<li class="news"><span class="news_title"><a href="/2026/1016/c10314a$number/page.htm" title="$title">$title</a></span><span class="news_meta">2026-10-16</span></li>"""

    private fun page(vararg articles: String, next: String? = null): String =
        """<html><ul class="news_list list2">${articles.joinToString("")}</ul><li class="page_nav"><a class="next" href="${next ?: "javascript:void(0);"}">下一页</a></li></html>"""

    @Test
    fun `parses academic affairs list and canonicalises article urls`() {
        val parsed = AcademicAffairsNoticeParser.parse(
            page(article(403355), next = "/10314/list2.htm"),
            AcademicAffairsNoticeParser.FIRST_PAGE
        )

        val notice = parsed.notices.single()
        assertEquals("c10314a403355", notice.articleId)
        assertEquals("通知403355", notice.title)
        assertEquals("2026-10-16", notice.publishedOn)
        assertEquals("https://jwc.ahu.edu.cn/2026/1016/c10314a403355/page.htm", notice.originalUrl)
        assertEquals("https://jwc.ahu.edu.cn/10314/list2.htm", parsed.nextPageUrl)
    }

    @Test
    fun `last page with javascript next stops pagination`() {
        val parsed = AcademicAffairsNoticeParser.parse(
            page(article(403355)),
            "https://jwc.ahu.edu.cn/10314/list42.htm"
        )
        assertEquals(null, parsed.nextPageUrl)
    }

    @Test
    fun `first sync seeds one page without traversing history`() = runTest {
        var requests = 0
        val crawler = AcademicAffairsNoticeCrawler { _ ->
            requests++
            page(article(403355), next = "/10314/list2.htm")
        }

        val notices = crawler.fetchSince(emptySet(), firstSync = true)

        assertEquals(listOf("c10314a403355"), notices.map(CampusNotice::articleId))
        assertEquals(1, requests)
    }

    @Test
    fun `walks pages until an exact seen article`() = runTest {
        val requested = mutableListOf<String>()
        val crawler = AcademicAffairsNoticeCrawler { url ->
            requested += url
            if (url.endsWith("list.htm")) page(article(403355), next = "/10314/list2.htm")
            else page(article(403070), article(403064))
        }

        val notices = crawler.fetchSince(setOf("c10314a403064"), firstSync = false)

        assertEquals(listOf("c10314a403355", "c10314a403070"), notices.map(CampusNotice::articleId))
        assertEquals(2, requested.size)
    }

    @Test
    fun `missing anchor fails instead of treating history as new`() = runTest {
        val crawler = AcademicAffairsNoticeCrawler { _ -> page(article(403355)) }
        try {
            crawler.fetchSince(setOf("c10314a403064"), firstSync = false)
            throw AssertionError("Expected history gap")
        } catch (error: CampusNoticeProtocolException) {
            assertTrue(error.message.orEmpty().contains("已见"))
        }
    }

    @Test
    fun `rejects links outside the academic affairs column`() {
        val bad = page("""<li class="news"><span class="news_title"><a href="https://example.com/post" title="越界">越界</a></span><span class="news_meta">2026-10-16</span></li>""")
        try {
            AcademicAffairsNoticeParser.parse(bad, AcademicAffairsNoticeParser.FIRST_PAGE)
            throw AssertionError("Expected protocol error")
        } catch (error: CampusNoticeProtocolException) {
            assertTrue(error.message.orEmpty().contains("受信任"))
        }
    }
}
