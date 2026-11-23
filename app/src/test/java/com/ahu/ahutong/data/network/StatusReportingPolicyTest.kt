package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.data.crawler.gmis.GmisProtocolException
import com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

class StatusReportingPolicyTest {

    @Test
    fun `campus errors are never reported when mock mode, unauthenticated, or auth expired`() {
        assertFalse(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = true,
                isSessionAuthenticated = true,
                error = AhuError.Server(502, "bad gateway")
            )
        )
        assertFalse(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = false,
                error = AhuError.Server(502, "bad gateway")
            )
        )
        assertFalse(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.Unauthorized("expired")
            )
        )
        assertFalse(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.Server(401, "unauthorized")
            )
        )
        assertFalse(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.ProtocolChanged("session expired"),
                cause = GmisSessionExpiredException()
            )
        )
        val http401 = HttpException(
            Response.error<String>(401, "unauthorized".toResponseBody("text/plain".toMediaType()))
        )
        assertFalse(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.Unknown("401"),
                cause = http401
            )
        )
    }

    @Test
    fun `campus errors are reported only when session is authenticated and failure is non-auth`() {
        assertTrue(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.Server(502, "bad gateway")
            )
        )
        assertTrue(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.ProtocolChanged("html changed"),
                cause = GmisProtocolException("html changed")
            )
        )
        assertTrue(
            StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = true,
                error = AhuError.Timeout
            )
        )
    }

    @Test
    fun `login failure excludes bad credentials and blank input`() {
        assertFalse(
            StatusReportingPolicy.shouldReportLoginFailure(
                isMockMode = false,
                error = AhuError.Unauthorized("账号或密码错误")
            )
        )
        assertFalse(
            StatusReportingPolicy.shouldReportLoginFailure(
                isMockMode = false,
                error = AhuError.Server(-1, "请将信息填写完整")
            )
        )
        assertTrue(
            StatusReportingPolicy.shouldReportLoginFailure(
                isMockMode = false,
                error = AhuError.ProtocolChanged("教务登录异常")
            )
        )
        assertTrue(
            StatusReportingPolicy.shouldReportLoginFailure(
                isMockMode = false,
                error = AhuError.Timeout
            )
        )
    }
}
