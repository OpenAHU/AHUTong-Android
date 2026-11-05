package com.ahu.ahutong.assistant.capabilities

import com.ahu.ahutong.assistant.AssistantAnswer
import com.ahu.ahutong.assistant.AssistantCapability
import com.ahu.ahutong.assistant.AssistantCapabilitySpec
import com.ahu.ahutong.assistant.AssistantCard
import com.ahu.ahutong.assistant.AssistantCardItem
import com.ahu.ahutong.assistant.AssistantOutcome
import com.ahu.ahutong.assistant.AssistantPreferences
import com.ahu.ahutong.assistant.AssistantSensitivity
import com.ahu.ahutong.assistant.AssistantTrust
import com.ahu.ahutong.assistant.model.AssistantModelConfig
import com.ahu.ahutong.assistant.model.AssistantModelConfigStore
import com.ahu.ahutong.data.network.AhuHttp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DeepSeek 官方搜索（/v1/tools/search）。只在用户开启联网搜索且接口为 DeepSeek 时可用。
 * 搜索结果属于外部内容，标记 UNTRUSTED。
 */
class WebSearchCapability(
    private val config: () -> AssistantModelConfig? = { AssistantModelConfigStore.load() },
    private val enabled: () -> Boolean = { AssistantPreferences.searchEnabled() },
) : AssistantCapability {

    override val spec = AssistantCapabilitySpec(
        id = "web_search",
        title = "联网搜索",
        description = "使用 DeepSeek 搜索获取实时网页信息。适合新闻、政策、赛事、天气、校外资料等超出本地校园数据的问题。" +
            "query 为搜索词，count 默认 5，最多 8。需要 AI 设置中开启联网搜索。",
        parameters = schema(
            "query" to stringProp("搜索词，1-128 字"),
            "count" to intProp("结果数量，默认 5", minimum = 1, maximum = 8),
        ),
        sensitivity = AssistantSensitivity.PUBLIC,
        source = "DeepSeek 搜索",
    )

    override suspend fun execute(arguments: JsonObject): AssistantOutcome {
        if (!enabled()) {
            return AssistantOutcome.Failure("search_disabled", "联网搜索未开启，请先在 AI 设置中开启")
        }
        val query = arguments.stringArg("query")
            ?: return AssistantOutcome.Failure("invalid_arguments", "query 不能为空")
        val config = config()
            ?: return AssistantOutcome.Failure("not_configured", "请先在 AI 设置中配置模型接口和 API Key")
        if (!config.isSecureEndpoint) {
            return AssistantOutcome.Failure("insecure_endpoint", "Base URL 必须使用 HTTPS（本机开发地址除外）")
        }
        if (!config.baseUrl.contains("deepseek.com", ignoreCase = true)) {
            return AssistantOutcome.Failure("search_unsupported", "当前接口暂不支持联网搜索")
        }
        val count = (arguments.intArg("count") ?: 5).coerceIn(1, 8)
        return try {
            val results = withContext(Dispatchers.IO) { search(config, query, count) }
            AssistantOutcome.Success(
                AssistantAnswer(
                    summary = "找到 ${results.size} 条网页结果",
                    lines = results.map { result -> listOfNotNull(result.title, result.summary).joinToString(" · ") },
                    trust = AssistantTrust.UNTRUSTED,
                    card = AssistantCard(
                        kind = "search",
                        headline = results.size.toString(),
                        headlineSuffix = "条",
                        subtitle = query,
                        items = results.map { result ->
                            AssistantCardItem(
                                title = result.title.ifBlank { result.url },
                                subtitle = result.summary.ifBlank { result.url },
                                value = result.date,
                            )
                        },
                    ),
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            AssistantOutcome.Failure("search_failed", error.message ?: "联网搜索失败", retryable = true)
        }
    }

    private fun search(config: AssistantModelConfig, query: String, count: Int): List<SearchItem> {
        val payload = JsonObject().apply {
            addProperty("query", query)
            addProperty("count", count)
            addProperty("need_content", false)
            addProperty("ori_question", query)
        }
        val request = Request.Builder()
            .url(searchEndpoint(config.baseUrl))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val client = AhuHttp.plain(
            connectTimeoutSeconds = 15,
            readTimeoutSeconds = 30,
            captureDiagnostics = false,
        ).build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("搜索接口返回 ${response.code}：${body.take(300)}")
            }
            val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrElse {
                throw IOException("搜索响应无法解析：${body.take(300)}")
            }
            return root.getAsJsonArray("results")?.mapNotNull { element ->
                val item = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                SearchItem(
                    title = item.stringArg("title").orEmpty(),
                    url = item.stringArg("url").orEmpty(),
                    summary = item.stringArg("summary")
                        ?: item.stringArg("snippet").orEmpty(),
                    date = item.stringArg("date"),
                )
            }.orEmpty()
        }
    }

    private fun searchEndpoint(baseUrl: String): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith("/v1")) "$base/tools/search" else "$base/v1/tools/search"
    }

    private data class SearchItem(
        val title: String,
        val url: String,
        val summary: String,
        val date: String?,
    )
}
