package com.ahu.ahutong.data.xuexiaotong

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar internal constructor(
    private val readPersisted: () -> String?,
    private val writePersisted: (String?) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) : CookieJar {
    constructor(context: Context) : this(
        readPersisted = {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("cookies", null)
        },
        writePersisted = { raw ->
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                if (raw == null) clear() else putString("cookies", raw)
            }.apply()
        }
    )

    companion object {
        const val PREFS_NAME = "ahutong_cx_cookies"

        /** 无实例可达时直接清掉磁盘上的 Cookie 持久化文件（隐私清理路径用）。 */
        fun clearPersisted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    // Cookie identity is domain + path + name, regardless of which host received it.
    private val cache = linkedMapOf<CookieKey, Cookie>()

    init {
        readPersisted()?.let { raw ->
            ChaoxingCookieCodec.decode(raw, now()).forEach { cache[it.key()] = it }
            // Rewrite the legacy representation once; also remove expired/malformed entries.
            persist()
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val timestamp = now()
        cookies.forEach { cookie ->
            cache.remove(cookie.key())
            if (cookie.expiresAt > timestamp) cache[cookie.key()] = cookie
        }
        removeExpired(timestamp)
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (removeExpired(now())) persist()
        return cache.values.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }

    /** Compatibility marker for Store; never use this header to reconstruct a Cookie. */
    @Synchronized
    fun cookieString(): String = snapshot().associate { it.name to "${it.name}=${it.value}" }
        .values.joinToString("; ")

    @Synchronized
    fun clear() {
        cache.clear()
        writePersisted(null)
    }

    @Synchronized
    internal fun snapshot(): List<Cookie> {
        if (removeExpired(now())) persist()
        return cache.values.toList()
    }

    @Synchronized
    internal fun restoreSnapshot(cookies: List<Cookie>) {
        cache.clear()
        val timestamp = now()
        cookies.filter { it.expiresAt > timestamp }.forEach { cache[it.key()] = it }
        persist()
    }

    private fun removeExpired(timestamp: Long): Boolean =
        cache.entries.removeAll { it.value.expiresAt <= timestamp }

    private fun persist() = writePersisted(ChaoxingCookieCodec.encode(cache.values.toList()))
}

private data class CookieKey(val domain: String, val path: String, val name: String)
private fun Cookie.key() = CookieKey(domain, path, name)
