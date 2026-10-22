package com.ahu.ahutong.data.xuexiaotong

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie

/** Versioned storage retains the attributes absent from a Cookie request header. */
internal object ChaoxingCookieCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(cookies: List<Cookie>): String = json.encodeToString(CookieFile(
        cookies = cookies.map { cookie ->
            StoredCookie(cookie.name, cookie.value, cookie.domain, cookie.path, cookie.expiresAt,
                cookie.secure, cookie.httpOnly, cookie.hostOnly, cookie.persistent, cookie.sameSite)
        }
    ))

    fun decode(raw: String, now: Long): List<Cookie> = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching emptyList()
        val cookies = if (root.containsKey("version")) {
            val file = json.decodeFromString<CookieFile>(raw)
            if (file.version != 2) return@runCatching emptyList()
            file.cookies.mapNotNull { stored -> stored.toCookie() }
        } else {
            // Old data has already lost its attributes. Preserve its old domain/path defaults
            // for one migration, without inventing a persistent expiry for an unknown lifetime.
            root.values.flatMap { element ->
                val legacy = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: return@flatMap emptyList()
                legacy.split("; ").mapNotNull { entry ->
                    val pipe = entry.indexOf('|')
                    val equals = entry.indexOf('=', pipe + 1)
                    if (pipe <= 0 || equals <= pipe + 1) return@mapNotNull null
                    runCatching {
                        Cookie.Builder().name(entry.substring(0, pipe))
                            .domain(entry.substring(pipe + 1, equals))
                            .value(entry.substring(equals + 1)).path("/").build()
                    }.getOrNull()
                }
            }
        }
        cookies.filter { it.expiresAt > now }
    }.getOrDefault(emptyList())

    @Serializable
    private data class CookieFile(val version: Int = 2, val cookies: List<StoredCookie>)

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
        val persistent: Boolean,
        val sameSite: String? = null
    ) {
        fun toCookie(): Cookie? = runCatching {
            Cookie.Builder().name(name).value(value).path(path).apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                if (persistent) expiresAt(expiresAt)
                if (secure) secure()
                if (httpOnly) httpOnly()
                sameSite?.let { sameSite(it) }
            }.build()
        }.getOrNull()
    }
}
