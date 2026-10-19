package com.ahu.ahutong.feature.circle

import android.util.LruCache
import com.ahu.ahutong.core.plugin.PluginHttpClient
import com.ahu.ahutong.core.plugin.PluginHttpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 校园圈子数据客户端（v0.2 两段式架构）。
 *
 * ```
 * ① 索引服务 /market/readonly/feed ─▶ 帖子 id 序列 + 签名游标（无鉴权）
 * ② zxs-bbs 只读口按 id 直连        ─▶ 详情 / 评论 / 十大热帖（无鉴权）
 * ```
 *
 * 背景（2026-10 实测）：zxs-bbs 已把列表接口纳入鉴权（401 token 为空），
 * 但「按 ID 读详情 / 评论 / 热榜」三个口保持开放；索引服务把要鉴权的列表
 * 转成免鉴权的 ID 序列，客户端全程零凭证可读（纪律：低频只读、不批量抓全站）。
 *
 * 防御式解析：列表/评论包裹层多形态兼容，字段多别名兜底（沿用 v0.1 策略）。
 */
class CircleApi(private val client: PluginHttpClient) {

    /** 详情按 id 幂等，缓存命中直接返回（借鉴 AHU Plus MarketReadOnlyCacheEntry 的策略）。 */
    private val topicCache = object : LruCache<String, CircleTopic>(64) {}

    /* ---------------- ① 索引服务 ---------------- */

    /** 拉一页索引（cursor 为空取第一页）。 */
    suspend fun feed(cursor: String?): CircleIndexPage = withContext(Dispatchers.IO) {
        val query = buildString {
            append("limit=").append(FEED_PAGE_SIZE)
            cursor?.takeIf { it.isNotBlank() }?.let {
                append("&cursor=").append(java.net.URLEncoder.encode(it, "UTF-8"))
            }
        }
        val root = getJson("$INDEX_BASE/market/readonly/feed?$query")
        val data = root.optJSONObject("data")
            ?: throw CircleApiException("索引服务响应异常")
        val ids = data.optJSONArray("ids")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf(String::isNotBlank) }
        } ?: emptyList()
        CircleIndexPage(
            ids = ids,
            nextCursor = data.optString("nextCursor").takeIf { it.isNotBlank() },
            hasMore = data.optBoolean("hasMore", false),
            sourceStatus = data.optString("sourceStatus")
        )
    }

    /* ---------------- ② zxs-bbs 只读口 ---------------- */

    /** 帖子详情（只读端点，免 token；带 64 条 LRU 缓存）。 */
    suspend fun fetchTopicDetail(id: String): CircleTopic = withContext(Dispatchers.IO) {
        topicCache.get(id)?.let { return@withContext it }
        val json = getJson("$BBS_BASE/api/client/topics/read_only/$id")
        val obj = extractObject(json)
        val topic = parseTopic(obj) ?: throw CircleApiException("帖子详情解析失败")
        topicCache.put(id, topic)
        topic
    }

    /** 十大热帖（免 token，直接含正文；客户端自行缓存 5-10 分钟）。 */
    suspend fun fetchHotTopics(): List<CircleTopic> = withContext(Dispatchers.IO) {
        val json = getJson("$BBS_BASE/api/client/topics/top?school_id=$SCHOOL_ID")
        val arr = json.optJSONArray("data") ?: JSONArray()
        (0 until arr.length()).mapNotNull { parseTopic(arr.optJSONObject(it)) }
    }

    /** 评论列表。服务端会重写 pageSize（实测固定 6），分页以 page 递增。 */
    suspend fun fetchComments(topicId: String, page: Int): List<CircleComment> =
        withContext(Dispatchers.IO) {
            val json = getJson(
                "$BBS_BASE/api/client/comments?topic_id=$topicId&page=$page&pageSize=$COMMENT_PAGE_SIZE"
            )
            val arr = extractCommentsArray(json)
            (0 until arr.length()).mapNotNull { parseComment(arr.optJSONObject(it)) }
        }

    /* ---------------- 传输与解析 ---------------- */

    private fun getJson(url: String): JSONObject {
        // 小程序伪装头只加给 zxs-bbs；索引服务是普通公开 HTTP，不带这些头
        val headers = if (url.startsWith(BBS_BASE)) {
            mapOf(
                "Tenant" to TENANT,
                "xweb_xhr" to "1",
                "Referer" to MINI_PROGRAM_REFERER,
                "User-Agent" to DESKTOP_UA
            )
        } else {
            emptyMap()
        }
        val response = client.execute(
            PluginHttpRequest(
                method = "GET",
                url = url,
                headers = headers,
                body = null,
                connectTimeoutMs = 15_000,
                readTimeoutMs = 30_000
            )
        )
        val body = response.bodyText()
        if (response.code !in 200..299) throw CircleApiException("HTTP ${response.code}")
        return runCatching { JSONObject(body) }.getOrElse {
            // 有的端点直接返回数组
            throw CircleApiException("响应不是 JSON")
        }
    }

    /** 防御式取出列表数组：兼容 {data:{list:[]}} / {data:[]} / {list:[]} 等常见包裹。 */
    private fun extractArray(root: JSONObject): JSONArray {
        root.optJSONObject("data")?.let { data ->
            data.optJSONArray("list")?.let { return it }
            data.optJSONArray("topics")?.let { return it }
            data.optJSONArray("comments")?.let { return it }
            data.optJSONArray("items")?.let { return it }
        }
        root.optJSONArray("data")?.let { return it }
        root.optJSONArray("list")?.let { return it }
        root.optJSONArray("topics")?.let { return it }
        root.optJSONArray("comments")?.let { return it }
        return JSONArray()
    }

    /** 评论包裹层：实测主形态 {data:{rows:[...]}}，其余沿用旧容错链。 */
    private fun extractCommentsArray(root: JSONObject): JSONArray {
        root.optJSONObject("data")?.optJSONArray("rows")?.let { return it }
        return extractArray(root)
    }

    private fun extractObject(root: JSONObject): JSONObject =
        root.optJSONObject("data") ?: root

    private fun parseTopic(obj: JSONObject?): CircleTopic? {
        obj ?: return null
        val id = obj.optFirstString("id", "topic_id", "topicId") ?: return null
        val user = obj.optJSONObject("userInfo") ?: obj.optJSONObject("user")
        // node 在只读口是字符串（"话题"/"新鲜事"），在包裹形态里可能是对象
        val nodeName = when (val node = obj.opt("node")) {
            is JSONObject -> node.optFirstString("name")
            is String -> node.takeIf { it.isNotBlank() }
            else -> null
        } ?: obj.optFirstString("node_name", "nodeName") ?: ""
        // 上游无标题帖统一写 "none"，展示层当空处理
        val rawTitle = obj.optFirstString("title", "name") ?: ""
        val title = if (rawTitle.equals("none", ignoreCase = true)) "" else rawTitle
        val imageUrls = buildList {
            obj.optJSONArray("imgs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf(String::isNotBlank)?.let(::add)
                }
            }
            if (isEmpty()) {
                // 有的形态把图片塞在 data 数组里（AHU Plus 模型：imgs 详情在 data 或 imgs）
                obj.optJSONArray("data")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optString(i).takeIf { it.startsWith("http") }?.let(::add)
                    }
                }
            }
        }
        return CircleTopic(
            id = id,
            title = title,
            content = obj.optFirstString("content", "text") ?: "",
            nodeName = nodeName,
            authorName = user?.optFirstString("nickname", "name") ?: "匿名",
            commentCount = obj.optFirstInt("commentCount", "comment_count", "comments"),
            likeCount = obj.optFirstInt("likeCount", "like_count", "likes", "like"),
            viewCount = obj.optFirstInt("viewCount", "view_count", "views"),
            isAnon = obj.optFirstInt("is_anon", "isAnon") == 1,
            isTop = obj.optFirstInt("is_top", "isTop") == 1,
            createTime = obj.optFirstString("createTime", "create_time", "created_at") ?: "",
            imageUrls = imageUrls
        )
    }

    private fun parseComment(obj: JSONObject?): CircleComment? {
        obj ?: return null
        val id = obj.optFirstString("id", "comment_id") ?: return null
        val user = obj.optJSONObject("userInfo") ?: obj.optJSONObject("user")
        return CircleComment(
            id = id,
            authorName = user?.optFirstString("nickname", "name") ?: "匿名",
            content = obj.optFirstString("content", "text") ?: "",
            createTime = obj.optFirstString("createTime", "create_time", "created_at") ?: ""
        )
    }

    private fun JSONObject.optFirstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k ->
            if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotBlank() && it != "null" } else null
        }

    private fun JSONObject.optFirstInt(vararg keys: String): Int =
        keys.firstNotNullOfOrNull { k ->
            if (has(k) && !isNull(k)) optString(k).toIntOrNull() else null
        } ?: 0

    companion object {
        /** zxs-bbs 官方接口（详情/评论/热榜，只读口免 token）。 */
        private const val BBS_BASE = "https://api.zxs-bbs.cn"

        /** 索引服务（第三方聚合源，无鉴权；自建服务备案完成后可切换）。 */
        private const val INDEX_BASE = "https://api.ahuplus.online"

        private const val ZXS_BBS_HOST = "api.zxs-bbs.cn"
        private const val TENANT = "7" // 安徽大学租户号
        private const val SCHOOL_ID = "10681" // 热榜参数用的学校编号
        private const val MINI_PROGRAM_REFERER =
            "https://servicewechat.com/wxc56be16e96fc1df1/66/page-frame.html"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val FEED_PAGE_SIZE = 20

        /** 服务端会把 pageSize 重写为 6（实测）；分页判断以该值为准。 */
        const val COMMENT_PAGE_SIZE = 6
    }
}

class CircleApiException(message: String) : Exception(message)