package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.model.AcademicAccountType
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
    private fun success() = AHUResponse<User>().apply {
        code = 0
        data = User("Test Student", "G20260001")
    }
    private fun flow(
        bachelor: suspend () -> PortalLoginResult,
        graduate: suspend () -> PortalLoginResult,
        wisdom: AHUResponse<User> = success()
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
        assertTrue(result.isSuccessful)
        assertEquals(AcademicAccountType.UNDERGRADUATE, result.data.academicAccountType)
        assertEquals(listOf("wisdom", "undergraduate"), calls)
    }

    @Test fun graduateNeedsPositiveGraduateAuthenticationAfterUndergraduateRejection() = runTest {
        val result = flow(
            { PortalLoginResult(PortalLoginStatus.REJECTED) },
            { PortalLoginResult(PortalLoginStatus.SUCCESS) }
        ).login("student", "secret")
        assertTrue(result.isSuccessful)
        assertEquals(AcademicAccountType.POSTGRADUATE, result.data.academicAccountType)
        assertEquals(listOf("wisdom", "undergraduate", "postgraduate"), calls)
    }

    @Test fun wisdomFailureDoesNotAttemptOtherSystems() = runTest {
        val denied = AHUResponse<User>().apply { code = 10012; msg = "账号或密码错误" }
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
        assertFalse(result.isSuccessful)
        assertNull(result.data)
        assertTrue(result.msg.contains("身份尚未确认"))
    }

    @Test fun undergraduateOutageAloneDoesNotClassifyUserAsGraduate() = runTest {
        val result = flow(
            { throw IOException("unavailable") },
            { throw IOException("unavailable") }
        ).login("student", "secret")
        assertFalse(result.isSuccessful)
        assertNull(result.data)
    }

    @Test fun graduateCanBeConfirmedEvenWhenUndergraduatePortalIsUnavailable() = runTest {
        val result = flow(
            { throw IOException("unavailable") },
            { PortalLoginResult(PortalLoginStatus.SUCCESS) }
        ).login("student", "secret")
        assertEquals(AcademicAccountType.POSTGRADUATE, result.data.academicAccountType)
    }

    @Test fun undergraduateBrowserVerificationRemainsAvailable() = runTest {
        val result = flow(
            { PortalLoginResult(PortalLoginStatus.VERIFICATION_REQUIRED) },
            { PortalLoginResult(PortalLoginStatus.REJECTED) }
        ).login("student", "secret")
        assertEquals(412, result.code)
        assertNotNull(result.data)
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
        val graduate = success().data.apply { academicAccountType = AcademicAccountType.POSTGRADUATE }
        val restored = Gson().fromJson(Gson().toJson(graduate), User::class.java)
        assertEquals(AcademicAccountType.POSTGRADUATE, restored.academicAccountType)
        val legacyOrNewAccount = Gson().fromJson("""{"name":"Other","xh":"B20260001"}""", User::class.java)
        assertEquals(AcademicAccountType.UNDERGRADUATE, legacyOrNewAccount.academicAccountType)
    }
}
