package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class WisdomLoginFlowTest {
    @Test
    fun wisdomAccountCanSignInWithoutAnyAcademicLogin() = runTest {
        val requests = mutableListOf<List<String>>()
        val flow = WisdomLoginFlow(
            fetchCaptcha = { byteArrayOf(1, 2) },
            solveCaptcha = { "ABCD" },
            submitLogin = { account, secret, captcha ->
                requests += listOf(account, secret, captcha)
                SUCCESS
            }
        )

        val response = flow.login("  G20260001\n", " password ")

        assertTrue(response.isSuccess)
        assertEquals("G20260001", response.valueOrNull()?.xh)
        assertEquals("Test User", response.valueOrNull()?.name)
        assertEquals(listOf(listOf("G20260001", " password ", "ABCD")), requests)
    }

    @Test
    fun wrongCredentialsAreReturnedImmediatelyWithoutFallback() = runTest {
        var requests = 0
        val flow = flow {
            requests++
            """{"code":10012,"msg":"账号或密码错误","object":null}"""
        }

        val response = flow.login("G20260001", "wrong")

        assertFalse(response.isSuccess)
        assertNull(response.valueOrNull())
        assertTrue(response.errorOrNull() is AhuError.Unauthorized)
        assertEquals("账号或密码错误", response.message())
        assertEquals(1, requests)
    }

    @Test
    fun captchaFailureFetchesAnotherCaptchaAndCanSucceed() = runTest {
        var fetched = 0
        val flow = WisdomLoginFlow(
            fetchCaptcha = { byteArrayOf((++fetched).toByte()) },
            solveCaptcha = { it.single().toString() },
            submitLogin = { _, _, captcha ->
                if (captcha == "1") CAPTCHA_FAILURE else SUCCESS
            }
        )

        assertTrue(flow.login("G20260001", "secret").isSuccess)
        assertEquals(2, fetched)
    }

    @Test
    fun repeatedCaptchaFailureStopsAfterFiveAttempts() = runTest {
        var requests = 0
        val response = flow { requests++; CAPTCHA_FAILURE }.login("G20260001", "secret")

        assertEquals(5, requests)
        assertFalse(response.isSuccess)
        assertEquals("验证码错误", response.message())
    }

    @Test
    fun nonCaptchaBusinessFailureIsNotRetriedOrAcceptedAsSuccess() = runTest {
        var requests = 0
        val response = flow {
            requests++
            """{"code":0,"msg":"账号未启用","object":null}"""
        }.login("G20260001", "secret")

        assertFalse(response.isSuccess)
        assertEquals("账号未启用", response.message())
        assertEquals(1, requests)
    }

    @Test
    fun incompleteSuccessResponseDoesNotCreateLoggedInUser() = runTest {
        for (payload in listOf("null", "{}", """{"user":{"userName":"Test User"}}""")) {
            val response = flow {
                """{"code":10000,"msg":"成功","object":$payload}"""
            }.login("G20260001", "secret")

            assertFalse(response.isSuccess)
            assertNull(response.valueOrNull())
            assertEquals("智慧安大登录响应缺少用户信息，请稍后重试", response.message())
        }
    }

    @Test
    fun blankCredentialsDoNotSendAnyRequest() = runTest {
        val flow = WisdomLoginFlow(
            fetchCaptcha = { error("Should not request a captcha") },
            solveCaptcha = { error("Should not run OCR") },
            submitLogin = { _, _, _ -> error("Should not submit credentials") }
        )

        assertFalse(flow.login(" \n", "secret").isSuccess)
        assertFalse(flow.login("G20260001", " ").isSuccess)
    }

    @Test
    fun networkFailureAndCancellationPropagateWithoutFallback() = runTest {
        assertFailsWith<IOException> {
            flow { throw IOException("offline") }.login("G20260001", "secret")
        }
        assertFailsWith<CancellationException> {
            flow { throw CancellationException("cancelled") }.login("G20260001", "secret")
        }
    }

    private fun flow(response: suspend () -> String) = WisdomLoginFlow(
        fetchCaptcha = { byteArrayOf(1) },
        solveCaptcha = { "ABCD" },
        submitLogin = { _, _, _ -> response() }
    )

    private fun AhuResult<*>.message(): String = when (val error = errorOrNull()) {
        is AhuError.Unauthorized -> error.message
        is AhuError.Server -> error.message
        else -> ""
    }

    private companion object {
        const val SUCCESS =
            """{"code":10000,"msg":"成功","object":{"user":{"userName":"Test User","idNumber":"G20260001"}}}"""
        const val CAPTCHA_FAILURE = """{"code":-1,"msg":"验证码错误","object":null}"""
    }
}
