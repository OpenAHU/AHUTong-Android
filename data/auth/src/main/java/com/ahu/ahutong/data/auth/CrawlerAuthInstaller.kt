package com.ahu.ahutong.data.auth

import android.app.Application
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.auth.di.AuthEntryPoint
import com.ahu.ahutong.data.crawler.net.CrawlerAuthHooks
import com.ahu.ahutong.data.dao.AHUCache
import dagger.hilt.android.EntryPointAccessors

/**
 * Wires AHUCache + AuthRepository into the crawler network layer.
 * Call once from Application.onCreate after Hilt is ready.
 */
object CrawlerAuthInstaller {
    fun install(app: Application) {
        CrawlerAuthHooks.loadCredentials = loadCredentials@{
            val user = AHUCache.getCurrentUser() ?: return@loadCredentials null
            val password = AHUCache.getWisdomPassword() ?: return@loadCredentials null
            user.xh.toString() to password
        }
        CrawlerAuthHooks.performLogin = { username, password ->
            val auth = EntryPointAccessors.fromApplication(app, AuthEntryPoint::class.java)
                .authRepository()
            auth.login(username, password) is AppResult.Success
        }
    }
}
