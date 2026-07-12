package com.ahu.ahutong.data.crawler

import android.app.Application
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.net.CrawlerAuthHooks
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.di.DataEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Wires app-only AHUCache + AuthRepository into the crawler network layer.
 * Call once from [Application.onCreate] after Hilt is ready.
 */
object CrawlerAuthInstaller {
    fun install(app: Application) {
        CrawlerAuthHooks.loadCredentials = loadCredentials@{
            val user = AHUCache.getCurrentUser() ?: return@loadCredentials null
            val password = AHUCache.getWisdomPassword() ?: return@loadCredentials null
            user.xh.toString() to password
        }
        CrawlerAuthHooks.performLogin = { username, password ->
            val auth = EntryPointAccessors.fromApplication(app, DataEntryPoint::class.java)
                .authRepository()
            auth.login(username, password) is AppResult.Success
        }
    }
}
