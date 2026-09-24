package com.ahu.ahutong.data.network

import com.ahu.ahutong.data.crawler.net.AutoLoginInterceptor
import com.ahu.ahutong.data.crawler.net.SessionExpiryHook
import com.ahu.ahutong.data.crawler.net.TokenAuthenticator
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.OkHttpClient

/**
 * 校内第一方客户端的共享装配。
 *
 * 这三件事过去在每个客户端各写一遍：持久化 Cookie、登录跳转识别、会话续期。
 * 漏配一处不会编译报错，只会在运行时表现为"这个接口不会自动重登"——最难发现的一类缺陷。
 * 收口到这里之后，由边界规则 R7 保证不会有人绕过（见 docs/architecture/CONTEXT.md 第 2 节）。
 */

/**
 * Cookie 与重定向策略：校内站点共用同一套持久化 Cookie。
 *
 * 持久化 Cookie 的存放（Keystore 加密存储）由调用方提供，传输层不认识它。
 */
fun OkHttpClient.Builder.campusCookies(
    cookieJar: CookieJar,
    followRedirects: Boolean = true,
    followSslRedirects: Boolean = true
): OkHttpClient.Builder = apply {
    cookieJar(cookieJar)
    followRedirects(followRedirects)
    followSslRedirects(followSslRedirects)
}

/** 将登录页重定向标记为需续期的 401，由认证器决定最终登录态。 */
fun OkHttpClient.Builder.campusAutoLogin(): OkHttpClient.Builder = apply {
    addNetworkInterceptor(AutoLoginInterceptor())
}

/** 会话失效时用存储凭据续期；实现由调用方注入（生产实现见 data/session/RepositorySessionExpiryHook）。 */
fun OkHttpClient.Builder.campusSessionRefresh(
    sessionExpiryHook: SessionExpiryHook
): OkHttpClient.Builder = apply {
    authenticator(TokenAuthenticator(sessionExpiryHook))
}

/**
 * 登录专用客户端：探测登录页时不能带会话续期，也不能让"被踢回登录页"的识别改写响应，
 * 否则登录过程本身会被当成会话失效。
 */
fun OkHttpClient.Builder.withoutCampusSessionRefresh(): OkHttpClient.Builder = apply {
    authenticator(Authenticator.NONE)
    networkInterceptors().removeAll { it is AutoLoginInterceptor }
}
