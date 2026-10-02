package com.ahu.ahutong.data.xuexiaotong

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONObject

class PersistentCookieJar(private val context: Context) : CookieJar {

    companion object {
        const val PREFS_NAME = "ahutong_cx_cookies"

        /** 无实例可达时直接清掉磁盘上的 Cookie 持久化文件（隐私清理路径用）。 */
        fun clearPersisted(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private val cache = mutableMapOf<String, MutableList<Cookie>>()

    init {
        restore()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = cache.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { c ->
            list.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            list.add(c)
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        cache.forEach { (_, list) ->
            list.filter { it.expiresAt > now && it.matches(url) }.forEach { c ->
                result.add(c)
            }
        }
        return result
    }

    fun cookieString(): String {
        val now = System.currentTimeMillis()
        val map = linkedMapOf<String, String>()
        cache.forEach { (_, list) ->
            list.filter { it.expiresAt > now }.forEach { c ->
                map[c.name] = "${c.name}=${c.value}"
            }
        }
        return map.values.joinToString("; ")
    }

    fun clear() {
        cache.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    fun restoreFromString(cookieStr: String) {
        if (cookieStr.isEmpty()) return
        clear()
        val list = cookieStr.split("; ").mapNotNull { kv ->
            val eq = kv.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            try {
                Cookie.Builder()
                    .domain(".chaoxing.com")
                    .name(kv.substring(0, eq))
                    .value(kv.substring(eq + 1))
                    .expiresAt(Long.MAX_VALUE)
                    .build()
            } catch (e: Exception) { null }
        }
        if (list.isNotEmpty()) {
            cache[".chaoxing.com"] = list.toMutableList()
            persist()
        }
    }

    private fun persist() {
        try {
            val all = JSONObject()
            cache.forEach { (host, list) ->
                all.put(host, list.joinToString("; ") { "${it.name}|${it.domain}=${it.value}" })
            }
            context.getSharedPreferences("ahutong_cx_cookies", Context.MODE_PRIVATE)
                .edit().putString("cookies", all.toString()).apply()
        } catch (e: Exception) { }
    }

    private fun restore() {
        try {
            val raw = context.getSharedPreferences("ahutong_cx_cookies", Context.MODE_PRIVATE)
                .getString("cookies", null) ?: return
            val all = JSONObject(raw)
            all.keys().forEach { host ->
                val str = all.getString(host)
                val list = str.split("; ").mapNotNull { kv ->
                    val pipeIdx = kv.indexOf("|")
                    val eqIdx = kv.indexOf("=")
                    if (pipeIdx > 0 && eqIdx > pipeIdx) {
                        val name = kv.substring(0, pipeIdx)
                        val domain = kv.substring(pipeIdx + 1, eqIdx)
                        val value = kv.substring(eqIdx + 1)
                        try {
                            Cookie.Builder()
                                .domain(domain)
                                .name(name)
                                .value(value)
                                .expiresAt(Long.MAX_VALUE)
                                .build()
                        } catch (e: Exception) { null }
                    } else null
                }
                if (list.isNotEmpty()) cache[host] = list.toMutableList()
            }
        } catch (e: Exception) { }
    }
}