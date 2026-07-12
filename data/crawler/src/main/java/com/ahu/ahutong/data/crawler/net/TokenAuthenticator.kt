package com.ahu.ahutong.data.crawler.net

import android.util.Log
import com.ahu.ahutong.core.common.AppSessionState
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator : Authenticator {

    val TAG = "TokenAuthenticator"

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") != null && response.code == 302) {
            Log.e(TAG, "authenticate: 这是什么情况？")
            return null
        }

        // 每个接口发现重定向都可能会触发重新登录，这里保证只有一个请求在重新登录
        synchronized(AppSessionState.reLoginMutex) {
            if (!AppSessionState.sessionExpired) {
                Log.e(TAG, "authenticate: 成功登录了")
                return response.request.newBuilder().build()
            }

            Log.e(TAG, "authenticate: ${response.request.url} 尝试重新登录")
            return runBlocking {
                CookieManager.cookieJar.clear()
                TokenManager.clear()

                val credentials = CrawlerAuthHooks.loadCredentials?.invoke()
                if (credentials == null) {
                    Log.e(TAG, "authenticate: 未找到用户信息")
                    AppSessionState.sessionExpired = true
                    return@runBlocking null
                }

                val (username, password) = credentials
                val login = CrawlerAuthHooks.performLogin
                if (login == null) {
                    Log.e(TAG, "authenticate: CrawlerAuthHooks.performLogin not installed")
                    AppSessionState.sessionExpired = true
                    return@runBlocking null
                }

                val success = login(username, password)
                if (success) {
                    AppSessionState.sessionExpired = false
                    Log.e(TAG, "authenticate: 登录成功")
                    response.request.newBuilder().build()
                } else {
                    AppSessionState.sessionExpired = true
                    Log.e(TAG, "authenticate: 登录失败了")
                    null
                }
            }
        }
    }
}
