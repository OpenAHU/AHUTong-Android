package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.crawler.login.AcademicPortalHttp
import com.ahu.ahutong.data.crawler.login.AcademicPortalLogin
import com.ahu.ahutong.data.crawler.login.PortalLoginStatus
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.AcademicAccountType
import kotlinx.coroutines.CancellationException

internal object PostgraduateScheduleSource {
    fun forAccount(accountId: String): GmisScheduleClient = GmisScheduleClient(
        openSession = {
            val user = AHUCache.getCurrentUser()
            if (user?.xh != accountId || user.academicAccountType != AcademicAccountType.POSTGRADUATE) {
                throw CancellationException("Account changed")
            }
            val password = AHUCache.getWisdomPassword()?.takeIf { it.isNotBlank() }
                ?: throw GmisSessionExpiredException()
            val result = AcademicPortalLogin(request = AcademicPortalHttp::request)
                .login(AcademicPortalLogin.GMIS_ENTRY, accountId, password, user)
            if (result.status != PortalLoginStatus.SUCCESS) throw GmisSessionExpiredException()
            val sessionUrl = result.sessionPageUrl ?: throw GmisSessionExpiredException()
            val timetable = GmisScheduleClient.timetablePage(sessionUrl).toString()
            val page = AcademicPortalHttp.readGmis(timetable, null, timetable)
            if (page.status !in 200..299 || page.url.contains("login", ignoreCase = true)) {
                throw GmisSessionExpiredException()
            }
            sessionUrl
        },
        query = { url, fields, referer ->
            if (AHUCache.getCurrentUser()?.xh != accountId) throw CancellationException("Account changed")
            AcademicPortalHttp.readGmis(url, fields, referer)
        }
    )
}
