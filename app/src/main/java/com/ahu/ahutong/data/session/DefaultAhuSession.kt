package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.EvaluationRepository
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.crawler.manager.TokenManager
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator

/**
 * [AhuSession] 的生产装配：真实的登录动作、凭据保险箱、身份存储与残留清理。
 *
 * 装配收在一处，是为了让"换实现"只发生一次；将来 `:core:auth` 真的抽成模块时，
 * 这里就是它的边界——届时四个适配器由 `:app` 注入。
 */
object DefaultAhuSession : AhuSession by createSession(wisdomOnly = false)

internal object DefaultWisdomSession : AhuSession by createSession(wisdomOnly = true)

private fun createSession(wisdomOnly: Boolean) = RepositoryAhuSession(
    login = SessionSignIn { username, password, preferNative ->
        AHURepository.loginWithCrawler(username, password, preferNative)
    },
    refreshLogin = SessionSignIn { username, password, _ ->
        if (wisdomOnly) AHURepository.refreshWisdomSession(username, password)
        else AHURepository.loginWithCrawler(username, password, preferNative = false)
    },
    refreshScope = if (wisdomOnly) SessionRefreshCoordinator.Scope.WISDOM
        else SessionRefreshCoordinator.Scope.ACADEMIC,
    credentials = SecureCredentialVault,
    account = SessionAccount { SessionStore.currentUser() },
    residue = object : SessionResidue {

        override fun clearRetainedServiceSessions() {
            com.ahu.ahutong.data.mail.StudentMailSession.clear()
        }

        override fun clearDerivedToken() {
            // 续期成功后只丢派生的校园卡令牌：第一方 Cookie 是刚建立的会话本身。
            TokenManager.clear()
        }

        override suspend fun clear() {
            com.ahu.ahutong.data.mail.StudentMailSession.clear()
            EvaluationRepository.clearSession()
            TokenManager.clear()
            CookieManager.cookieJar.clear()
        }
    }
)
