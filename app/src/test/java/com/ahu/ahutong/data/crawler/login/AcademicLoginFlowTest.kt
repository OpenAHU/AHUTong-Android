package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import com.google.gson.Gson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class AcademicLoginFlowTest {
    private val calls = mutableListOf<String>()
    private fun success(): AhuResult<User> = AhuResult.Success(User("Test Student", "G20260001"))
    private fun flow(
        bachelor: suspend () -> PortalLoginResult,
        graduate: suspend () -> PortalLoginResult,
        wisdom: AhuResult<User> = success()
    ) = AcademicLoginFlow(
        wisdom = { _, _ -> calls += "wisdom"; wisdom },
        undergraduate = { _, _, _ -> calls += "undergraduate"; bachelor() },
        postgraduate = { _, _, _ -> calls += "postgraduate"; graduate() }
    )

    @Test fun undergraduateSuccessDoesNotTryGraduatePortal() = runTest {
        val result = flow(
            { PortalLoginResult(PortalLoginStatus.SUCCESS) },
            { error("Graduate portal must not be called") }
        ).login("student", "secret")
        assertTrue(result.isSuccess)
        assertEquals(AcademicAccountType.UNDERGRADUATE,
            (result.valueOrNull() as LoginOutcome.Success).user.academicAccountType)
        assertEquals(listOf("wisdom", "undergraduate"), calls)
    }

    @Test fun graduateNeedsPositiveGraduateAuthenticationAfterUndergraduateRejection() = runTest {
        val result = flow(
            { PortalLoginResult(PortalLoginStatus.REJECTED) },
            { PortalLoginResult(PortalLoginStatus.SUCCESS) }
        ).login("student", "secret")
        assertTrue(result.isSuccess)
        assertEquals(AcademicAccountType.POSTGRADUATE,
            (result.valueOrNull() as LoginOutcome.Success).user.academicAccountType)
        assertEquals(listOf("wisdom", "undergraduate", "postgraduate"), calls)
    }

    @Test fun wisdomFailureDoesNotAttemptOtherSystems() = runTest {
        val denied = AhuResult.Failure(AhuError.Unauthorized("账号或密码错误"))
        val result = flow({ error("Unexpected login") }, { error("Unexpected login") }, denied)
            .login("student", "wrong")
        assertSame(denied, result)
        assertEquals(listOf("wisdom"), calls)
    }

    @Test fun bothPortalsFailWithoutCreatingStudentIdentity() = runTest {
        val result = flow(
            { PortalLoginResult(PortalLoginStatus.REJECTED) },
            { PortalLoginResult(PortalLoginStatus.REJECTED, "没有学生会话") }
        ).login("student", "secret")
        assertTrue(result.isFailure)
        assertNull(result.valueOrNull())
        assertTrue((result.errorOrNull() as AhuError.ProtocolChanged).detail.contains("身份尚未确认"))
    }

    @Test fun undergraduateOutageAloneDoesNotClassifyUserAsGraduate() = runTest {
        val result = flow(
            { throw IOException("unavailable") },
            { throw IOException("unavailable") }
        ).login("student", "secret")
        assertTrue(result.isFailure)
        assertNull(result.valueOrNull())
    }

    @Test fun graduateCanBeConfirmedEvenWhenUndergraduatePortalIsUnavailable() = runTest {
        val result = flow(
            { throw IOException("unavailable") },
            { PortalLoginResult(PortalLoginStatus.SUCCESS) }
        ).login("student", "secret")
        assertEquals(AcademicAccountType.POSTGRADUATE,
            (result.valueOrNull() as LoginOutcome.Success).user.academicAccountType)
    }

    @Test fun undergraduateBrowserVerificationRemainsAvailable() = runTest {
        val result = flow(
            { PortalLoginResult(PortalLoginStatus.VERIFICATION_REQUIRED) },
            { PortalLoginResult(PortalLoginStatus.REJECTED) }
        ).login("student", "secret")
        assertTrue(result.valueOrNull() is LoginOutcome.JwxtWebVerificationRequired)
    }

    @Test fun cancellationNeverTriggersFallback() = runTest {
        assertFailsWith<CancellationException> {
            flow(
                { throw CancellationException("cancel") },
                { error("Unexpected fallback") }
            ).login("student", "secret")
        }
        assertEquals(listOf("wisdom", "undergraduate"), calls)
    }

    @Test fun identitySurvivesPersistenceButDoesNotLeakToAnotherAccount() {
        val graduate = requireNotNull(success().valueOrNull()).apply {
            academicAccountType = AcademicAccountType.POSTGRADUATE
        }
        val restored = Gson().fromJson(Gson().toJson(graduate), User::class.java)
        assertEquals(AcademicAccountType.POSTGRADUATE, restored.academicAccountType)
        val legacyOrNewAccount = Gson().fromJson("""{"name":"Other","xh":"B20260001"}""", User::class.java)
        assertEquals(AcademicAccountType.UNDERGRADUATE, legacyOrNewAccount.academicAccountType)
    }
}
