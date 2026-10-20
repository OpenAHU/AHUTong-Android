package com.ahu.ahutong.feature.circle

import com.ahu.ahutong.core.plugin.PluginHttpClient
import com.ahu.ahutong.core.plugin.PluginHttpRequest
import com.ahu.ahutong.core.plugin.PluginHttpResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CircleApi 的解析与路由契约（两段式：索引拿 id → 只读口取正文）。
 * 传输层用契约里的 PluginHttpClient 假实现，不碰网络。
 */
class CircleApiTest {

    @Test
    fun feedParsesIdsCursorAndStatus() = runBlocking {
        val fake = FakeClient(
            """{"data":{"ids":["a1","a2"],"nextCursor":"cursor-1","hasMore":true,"sourceStatus":"ready"}}"""
        )

        val page = CircleApi(fake).feed(null)

        val request = fake.requests.single()
        val path = request.url.substringAfter("api.ahuplus.online").substringBefore("?")
        assertEquals("/market/readonly/feed", path)
        assertTrue(request.url.endsWith("limit=20"))
        assertEquals(listOf("a1", "a2"), page.ids)
        assertEquals("cursor-1", page.nextCursor)
        assertTrue(page.hasMore)
        assertTrue(page.isReady)
    }

    @Test
    fun feedEncodesCursorAndTreatsMissingFieldsAsEmpty() = runBlocking {
        val fake = FakeClient("""{"data":{}}""")

        val page = CircleApi(fake).feed("a b/c")

        val url = fake.requests.single().url
        assertTrue(url.contains("cursor=a+b%2Fc"), "cursor must be URL-encoded: $url")
        assertTrue(page.ids.isEmpty())
        assertEquals(null, page.nextCursor)
        assertFalse(page.hasMore)
    }

    @Test
    fun hotTopicsParseAliasesAndSkipEntriesWithoutAnId() = runBlocking {
        val fake = FakeClient(
            """{"data":[{"topic_id":"topic-1","name":"标题","text":"正文",
                "user":{"name":"作者"},"node_name":"校园","comment_count":"2",
                "likes":3,"views":4,"is_anon":1,"is_top":1,"imgs":["image-url",""]},
                {"title":"缺少 id"}]}"""
        )

        val topics = CircleApi(fake).fetchHotTopics()

        val request = fake.requests.single()
        assertEquals("/api/client/topics/top", request.url.substringAfter("api.zxs-bbs.cn").substringBefore("?"))
        assertTrue(request.url.contains("school_id=10681"))
        assertEquals("7", request.headers["Tenant"])

        val topic = topics.single()
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
    fun missingOptionalFieldsHaveSafeDefaults() = runBlocking {
        val topics = CircleApi(FakeClient("""{"data":[{"id":"1"}]}""")).fetchHotTopics()

        val topic = topics.single()
        assertEquals("1", topic.id)
        assertEquals("", topic.title)
        assertEquals("匿名", topic.authorName)
        assertEquals(0, topic.commentCount)
        assertTrue(topic.imageUrls.isEmpty())
    }

    @Test
    fun detailUsesReadOnlyEndpointAndUnwrapsData() = runBlocking {
        val fake = FakeClient("""{"data":{"topicId":"42","title":"详情"}}""")

        val topic = CircleApi(fake).fetchTopicDetail("42")

        val request = fake.requests.single()
        assertEquals("GET", request.method)
        assertEquals("/api/client/topics/read_only/42", request.url.substringAfter("api.zxs-bbs.cn"))
        assertEquals("42", topic.id)
        assertEquals("详情", topic.title)
    }

    @Test
    fun commentsParseRowsAndRequestTheSelectedPage() = runBlocking {
        val fake = FakeClient(
            """{"data":{"rows":[{"comment_id":"c-1","text":"回复",
                "userInfo":{"nickname":"同学"},"created_at":"2026-10-19"},{}]}}"""
        )

        val comments = CircleApi(fake).fetchComments("42", page = 2)

        val request = fake.requests.single()
        assertTrue(request.url.contains("topic_id=42"))
        assertTrue(request.url.contains("page=2"))
        assertTrue(request.url.contains("pageSize=${CircleApi.COMMENT_PAGE_SIZE}"))
        assertEquals(CircleComment("c-1", "同学", "回复", "2026-10-19"), comments.single())
    }

    @Test
    fun httpAndMalformedResponsesFailInsteadOfLookingLikeAnEmptyFeed() = runBlocking {
        assertEquals("HTTP 503", assertFailsWith<CircleApiException> {
            CircleApi(FakeClient("unavailable", code = 503)).fetchHotTopics()
        }.message)

        assertEquals("响应不是 JSON", assertFailsWith<CircleApiException> {
            CircleApi(FakeClient("<html>error</html>")).fetchHotTopics()
        }.message)

        assertEquals("索引服务响应异常", assertFailsWith<CircleApiException> {
            CircleApi(FakeClient("""{"data":null}""")).feed(null)
        }.message)

        assertEquals("帖子详情解析失败", assertFailsWith<CircleApiException> {
            CircleApi(FakeClient("""{"data":{}}""")).fetchTopicDetail("42")
        }.message)
    }

    /** 终端假传输层：不发请求、不依赖网络与账号。 */
    private class FakeClient(
        private val body: String,
        private val code: Int = 200
    ) : PluginHttpClient {
        val requests = mutableListOf<PluginHttpRequest>()

        override fun execute(request: PluginHttpRequest): PluginHttpResponse {
            requests += request
            return PluginHttpResponse(
                code = code,
                headers = emptyMap(),
                body = body.toByteArray(Charsets.UTF_8)
            )
        }
    }
}
