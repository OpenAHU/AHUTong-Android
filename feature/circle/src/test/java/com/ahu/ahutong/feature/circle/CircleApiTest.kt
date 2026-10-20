package com.ahu.ahutong.feature.circle

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CircleApiTest {
    @Test
    fun topicsParseWrappedAliasesAndSkipEntriesWithoutAnId() = runBlocking {
        var requested: Request? = null
        val api = apiWithResponse(
            """{"data":{"list":[{"topic_id":"topic-1","name":"标题","text":"正文",
                "user":{"name":"作者"},"node_name":"校园","comment_count":"2",
                "likes":3,"views":4,"is_anon":1,"is_top":1,"imgs":["image-url",""]},
                {"title":"缺少 id"}]}}"""
        ) { requested = it }

        val page = api.fetchTopics(2)

        assertEquals("/api/client/topics", requested!!.url.encodedPath)
        assertEquals("2", requested!!.url.queryParameter("page"))
        assertEquals("20", requested!!.url.queryParameter("pageSize"))
        assertEquals(2, page.page)
        assertFalse(page.hasMore)
        val topic = page.topics.single()
        assertEquals("topic-1", topic.id)
        assertEquals("标题", topic.title)
        assertEquals("正文", topic.content)
        assertEquals("作者", topic.authorName)
        assertEquals("校园", topic.nodeName)
        assertEquals(2, topic.commentCount)
        assertEquals(3, topic.likeCount)
        assertEquals(4, topic.viewCount)
        assertTrue(topic.isAnon)
        assertTrue(topic.isTop)
        assertEquals(listOf("image-url"), topic.imageUrls)
    }

    @Test
    fun fullPageHasMoreAndMissingOptionalFieldsHaveSafeDefaults() = runBlocking {
        val topics = (1..20).joinToString(",") { """{"id":"$it"}""" }
        val page = apiWithResponse("""{"data":[$topics]}""").fetchTopics(1)

        assertEquals(20, page.topics.size)
        assertTrue(page.hasMore)
        assertEquals("匿名", page.topics.first().authorName)
        assertEquals(0, page.topics.first().commentCount)
        assertTrue(page.topics.first().imageUrls.isEmpty())
    }

    @Test
    fun detailUsesReadOnlyEndpointAndUnwrapsData() = runBlocking {
        var requested: Request? = null
        val topic = apiWithResponse("""{"data":{"topicId":"42","title":"详情"}}""") {
            requested = it
        }.fetchTopicDetail("42")

        assertEquals("/api/client/topics/read_only/42", requested!!.url.encodedPath)
        assertEquals("GET", requested!!.method)
        assertEquals("42", topic.id)
        assertEquals("详情", topic.title)
    }

    @Test
    fun commentsParseAliasesAndRequestTheSelectedTopic() = runBlocking {
        var requested: Request? = null
        val comments = apiWithResponse(
            """{"data":{"comments":[{"comment_id":"c-1","text":"回复",
                "userInfo":{"nickname":"同学"},"created_at":"2026-10-19"},{}]}}"""
        ) { requested = it }.fetchComments("42")

        assertEquals("42", requested!!.url.queryParameter("topic_id"))
        assertEquals("100", requested!!.url.queryParameter("pageSize"))
        assertEquals(CircleComment("c-1", "同学", "回复", "2026-10-19"), comments.single())
    }

    @Test
    fun httpAndMalformedResponsesFailInsteadOfLookingLikeAnEmptyFeed() = runBlocking {
        assertEquals("HTTP 503", assertFailsWith<CircleApiException> {
            apiWithResponse("unavailable", code = 503).fetchTopics(1)
        }.message)
        assertEquals("响应不是 JSON", assertFailsWith<CircleApiException> {
            apiWithResponse("<html>error</html>").fetchTopics(1)
        }.message)
        assertEquals("帖子详情解析失败", assertFailsWith<CircleApiException> {
            apiWithResponse("""{"data":{}}""").fetchTopicDetail("42")
        }.message)
    }

    /** A terminal fake HTTP adapter: no sockets, accounts, or live BBS dependency. */
    private fun apiWithResponse(
        body: String,
        code: Int = 200,
        onRequest: (Request) -> Unit = {}
    ) = CircleApi(OkHttpClient.Builder().addInterceptor { chain ->
        onRequest(chain.request())
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .body(body.toResponseBody())
            .build()
    }.build())
}
