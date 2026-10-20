package com.ahu.ahutong.data.crawler.login

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response

/** 在账号设置页继续跳向 WebVPN 前交还登录流程，由调用方续接原教务入口。 */
internal fun casPostLoginRedirect(response: Response, casSessionIssued: Boolean): PortalPage? {
    val source = response.request.url
    if (!response.isRedirect || source.scheme != "https" ||
        source.host != "one.ahu.edu.cn" || source.port != 443
    ) return null
    val target = response.header("Location")?.let(source::resolve) ?: return null
    if (!isCasPostLoginUrl(target.toString())) return null
    return PortalPage(target.toString(), response.code, "", casSessionIssued)
}

/** CAS 认证后的账号设置与门户落点；这些页面还不能证明教务会话已建立。 */
internal fun isCasPostLoginUrl(url: String?): Boolean {
    val target = url?.toHttpUrlOrNull() ?: return false
    if (target.scheme != "https" || target.host != "one.ahu.edu.cn" || target.port != 443) {
        return false
    }
    return when (target.encodedPath.trimEnd('/')) {
        "/tp_tpass/h5" -> target.queryParameter("act") == "tpass/guide"
        "/tp_tpass/tpass/guide", "/tp_tpass/view",
        "/tp_up/view", "/tp_up/portal/viewhome" -> true
        else -> false
    }
}

/** 仅识别中央 CAS 刚签发、可用于 CAS 登录的会话凭证，不把删除 Cookie 当成认证成功。 */
internal fun hasIssuedCasSession(response: Response): Boolean {
    val url = response.request.url
    if (url.scheme != "https" || url.host != "one.ahu.edu.cn" || url.port != 443) return false
    val loginUrl = "https://one.ahu.edu.cn/cas/login".toHttpUrl()
    return Cookie.parseAll(url, response.headers).any { cookie ->
        cookie.name == "CASTGC" && cookie.value.isNotBlank() &&
            cookie.expiresAt > System.currentTimeMillis() && cookie.matches(loginUrl)
    }
}
