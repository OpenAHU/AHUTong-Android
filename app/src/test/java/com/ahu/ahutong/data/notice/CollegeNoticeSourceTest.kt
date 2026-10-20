package com.ahu.ahutong.data.notice

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CollegeNoticeSourceTest {

    @Test
    fun `source resolves the user college and parses its list`() = runTest {
        val config = requireNotNull(CollegeNoticeRegistry.resolve("电子信息工程学院"))
        val html = """
            <ul class="news_list list2">
              <li class="news"><span class="news_title">
                <a href="/2026/0930/c11062a402044/page.htm" title="学院通知">学院通知</a>
              </span><span class="news_meta">2026-09-30</span></li>
            </ul>
            <ul class="wp_paging"><a class="next" href="/11062/list2.htm">下一页</a></ul>
        """.trimIndent()
        val source = CollegeNoticeSource({ config }) { html }

        assertEquals("college", source.sourceId)
        assertEquals("dy", source.currentScopeKey())
        val page = source.fetchPage(null)
        assertEquals("学院通知", page.notices.single().title)
        assertEquals("https://dy.ahu.edu.cn/11062/list2.htm", page.nextPageUrl)
    }
}
