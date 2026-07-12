package com.ahu.ahutong.data.auth

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie

@Singleton
class NativeCookieSyncer @Inject constructor(
    private val gateway: CampusNativeGateway,
) : AuthCookieSyncer {

    private val gson = Gson()

    override suspend fun syncFromNative() {
        try {
            val json = if (gateway.isLocalServiceReady()) {
                when (val list = gateway.httpGetCookiesList()) {
                    is AppResult.Success -> list.data
                    is AppResult.Error -> gateway.getCookiesList()
                }
            } else {
                gateway.getCookiesList()
            }

            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val cookies: List<Map<String, Any>> = gson.fromJson(json, listType) ?: emptyList()

            cookies.forEach { item ->
                val builder = Cookie.Builder()
                    .name(item["name"] as String)
                    .value(item["value"] as String)
                val domainObj = item["domain"]
                val path = item["path"] as String

                val domain = if (domainObj != null) {
                    domainObj as String
                } else {
                    if (path.contains("/cas")) "one.ahu.edu.cn" else "jw.ahu.edu.cn"
                }

                builder.domain(domain).path(path)
                if (item["secure"] == true) builder.secure()
                if (item["http_only"] == true) builder.httpOnly()

                CookieManager.cookieJar.addCookie(builder.build())
            }
            Log.d(TAG, "Cookies synced from native: ${cookies.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync cookies", e)
        }
    }

    private companion object {
        const val TAG = "NativeCookieSyncer"
    }
}
