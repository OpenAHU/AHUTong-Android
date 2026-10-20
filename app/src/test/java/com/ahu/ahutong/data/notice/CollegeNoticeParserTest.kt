package com.ahu.ahutong.data.notice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollegeNoticeParserTest {

    private fun config(name: String): CollegeNoticeConfig =
        requireNotNull(CollegeNoticeRegistry.resolve(name))

    @Test
    fun `variant A parses title date next page and relative article URL`() {
        val config = config("电子信息工程学院")
        val html = """
            <ul class="news_list list2">
              <li class="news n1"><span class="news_title">
                <a href='/2026/0930/c11062a402044/page.htm' title='2027年推免生复试成绩'>显示文本</a>
              </span><span class="news_meta">2026-09-30</span></li>
            </ul>
            <ul class="wp_paging">
              <a class="next" href="/11062/list2.htm">下一页</a>
            </ul>
        """.trimIndent()

        val page = CollegeNoticeParser.parse(html, config.listUrl, config)

        assertEquals(1, page.notices.size)
        assertEquals("2027年推免生复试成绩", page.notices.single().title)
        assertEquals("2026-09-30", page.notices.single().publishedOn)
        assertEquals("c11062a402044", page.notices.single().articleId)
        assertEquals("https://dy.ahu.edu.cn/2026/0930/c11062a402044/page.htm", page.notices.single().originalUrl)
        assertEquals("https://dy.ahu.edu.cn/11062/list2.htm", page.nextPageUrl)
    }

    @Test
    fun `variant A keeps external URLs and ignores javascript next`() {
        val config = config("电子信息工程学院")
        val html = """
            <ul class="news_list list2">
              <li class="news"><span class="news_title">
                <a href='https://mp.weixin.qq.com/s/example' title='微信通知'>微信通知</a>
              </span><span class="news_meta">2026-09-30</span></li>
            </ul>
            <ul class="wp_paging"><a class="next" href="javascript:void(0);">下一页</a></ul>
        """.trimIndent()

        val page = CollegeNoticeParser.parse(html, config.listUrl, config)

        assertEquals("https://mp.weixin.qq.com/s/example", page.notices.single().originalUrl)
        assertNull(page.nextPageUrl)
        assertTrue(CollegeNoticeRegistry.isTrustedArticleUrl(page.notices.single().originalUrl))
    }

    @Test
    fun `variant B parses Article_Title and Article_PublishDate`() {
        val config = config("电气工程与自动化学院")
        val html = """
            <ul class="wp_article_list">
              <li class="list_item"><span class="Article_Title">
                <a href="/2026/0920/c6135a401000/page.htm" title="电气通知">电气通知</a>
              </span><span class="Article_PublishDate">2026-09-20</span></li>
            </ul>
        """.trimIndent()

        val notice = CollegeNoticeParser.parse(html, config.listUrl, config).notices.single()
        assertEquals("电气通知", notice.title)
        assertEquals("2026-09-20", notice.publishedOn)
    }

    @Test
    fun `variant D extracts date from info text`() {
        val config = config("历史学院")
        val html = """
            <div class="list_box"><ul>
              <li><span class="bt_tt"><a href="/2026/0920/c11126a401000/page.htm" title="历史通知">历史通知</a></span>
                <span class="info">日期：2026-09-20 点击：(10)</span></li>
            </ul></div>
        """.trimIndent()

        val notice = CollegeNoticeParser.parse(html, config.listUrl, config).notices.single()
        assertEquals("历史通知", notice.title)
        assertEquals("2026-09-20", notice.publishedOn)
    }

    @Test
    fun `variant E joins split year-month and day nodes`() {
        val config = config("机器人学院")
        val html = """
            <div class="inew"><div class="inewi">
              <a href="/2026/0920/c24836a401000/page.htm"><span class="inewira">机器人通知</span></a>
              <span class="inewilb">2026-10</span><span class="inewila">09</span>
            </div></div>
        """.trimIndent()

        val notice = CollegeNoticeParser.parse(html, config.listUrl, config).notices.single()
        assertEquals("机器人通知", notice.title)
        assertEquals("2026-10-09", notice.publishedOn)
    }

    @Test
    fun `variant F uses science scope and ignores la1 entries`() {
        val config = config("经济学院")
        val html = """
            <div class="science-r-m">
              <div id="wp_news_w4"><ul class="fixed">
                <li class="la1"><a href="/9830/list.htm"><span class="la1">栏目入口</span></a><em>2026-10-09</em></li>
              </ul></div>
              <div id="wp_news_w7"><ul class="fixed">
                <li><a href="/2026/0918/c9830a401000/page.htm">经济通知</a><em>2026-09-18</em></li>
              </ul></div>
            </div>
        """.trimIndent()

        val page = CollegeNoticeParser.parse(html, config.listUrl, config)
        assertEquals(1, page.notices.size)
        assertEquals("经济通知", page.notices.single().title)
    }

    @Test
    fun `variant G reads eclips title and span date`() {
        val config = config("体育军事教学部")
        val html = """
            <div class="newsList"><ul>
              <li><a class="flex" href="/2026/0918/c14500a401000/page.htm">
                <h3 class="eclips">体育通知</h3><span>2026-09-18</span>
              </a></li>
            </ul></div>
        """.trimIndent()

        val notice = CollegeNoticeParser.parse(html, config.listUrl, config).notices.single()
        assertEquals("体育通知", notice.title)
        assertEquals("2026-09-18", notice.publishedOn)
    }

    @Test
    fun `variant H reads btt3 title and fbsj4 date`() {
        val config = config("国际教育学院")
        val html = """
            <div class="col_news_list"><div class="jzlb">
              <div class="btt3"><a href="/2026/0918/c11473a401000/page.htm">国际通知</a></div>
              <div class="fbsj4">2026-09-18</div>
            </div></div>
        """.trimIndent()

        val notice = CollegeNoticeParser.parse(html, config.listUrl, config).notices.single()
        assertEquals("国际通知", notice.title)
        assertEquals("2026-09-18", notice.publishedOn)
    }
}
