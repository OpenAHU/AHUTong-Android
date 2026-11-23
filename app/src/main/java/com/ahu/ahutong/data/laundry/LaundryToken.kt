package com.ahu.ahutong.data.laundry

/**
 * 从用户粘贴的文本里抠出乐摇摇的 JWT。
 *
 * 为什么需要手工粘贴：token 只能由**微信客户端**走静默授权产生（code 与乐摇摇服务号
 * appid 绑定，兑换要它的 appsecret），有效期 2 小时且没有 refresh 端点。
 * 所以 App 内无法自动获取，只能请用户在微信里打开一次设备页 → ··· → 复制链接 → 粘回来。
 *
 * 支持两种粘贴形式：
 * 1. 微信里复制的整条链接：`https://m.leyaoyao.com/pages/newWasher.html#/...&url_token=eyJ...`
 * 2. 直接粘 JWT 本身（`eyJhbGciOi...`）
 */
object LaundryToken {

    private val urlToken = Regex("""[?&#]url_token=([^&#\s"']+)""")
    private val jwt = Regex("""eyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}""")

    /** 从任意粘贴文本里提取 token；提不出来返回 null。 */
    fun extract(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        urlToken.find(text)?.groupValues?.getOrNull(1)?.let { candidate ->
            val decoded = decodeUrlComponent(candidate).trim()
            if (jwt.matches(decoded)) return decoded
            jwt.find(decoded)?.value?.let { return it }
        }
        return jwt.find(text)?.value
    }

    /** JWT 里的 exp（毫秒时间戳）；解析不出来返回 null。 */
    fun expiresAtMillis(token: String): Long? {
        val payload = payloadJson(token) ?: return null
        val match = Regex(""""exp"\s*:\s*(\d{9,13})""").find(payload) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        // exp 是秒；万一给的是毫秒也不至于炸
        return if (value > 100_000_000_000L) value else value * 1000L
    }

    fun isExpired(token: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = expiresAtMillis(token) ?: return true
        return expiresAt <= nowMillis
    }

    private fun payloadJson(token: String): String? {
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val decoded = base64UrlDecode(parts[1])
            if (decoded.contains("\"exp\"")) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    private fun base64UrlDecode(value: String): String {
        // java.util.Base64.URL 解码器容忍无填充，minSdk 26 可用，且单测里能跑（不依赖 android.jar）
        return String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }

    /** 链接里的 url_token 一般没编码，但微信偶尔会把 `+`/`=` 转义，保险起见解一遍。 */
    private fun decodeUrlComponent(value: String): String = try {
        java.net.URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}
