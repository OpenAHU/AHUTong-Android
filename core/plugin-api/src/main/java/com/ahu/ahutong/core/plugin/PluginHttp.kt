package com.ahu.ahutong.core.plugin

import java.nio.charset.Charset

/**
 * 契约级 HTTP 抽象：宿主内部用 OkHttp 实现，但 OkHttp 类型不出现在契约里
 * （插件自带 okhttp 时类型不同，跨加载器不可用）。
 *
 * 实施方是宿主；插件只调用 [PluginHttpClient.execute]（阻塞式，须在后台线程）。
 */
interface PluginHttpClient {
    fun execute(request: PluginHttpRequest): PluginHttpResponse
}

/** 无默认参数：避免 `$default` 合成方法成为跨加载器的额外解析面。 */
class PluginHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val connectTimeoutMs: Int,
    val readTimeoutMs: Int
)

class PluginHttpResponse(
    val code: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray
) {
    fun bodyText(): String = body.toString(Charsets.UTF_8)
}
