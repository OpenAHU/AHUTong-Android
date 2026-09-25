package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.model.User
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class AcademicPortalLoginTest {
    private val student = User("Test Student", "G20260001")
    private val graduateHome = PortalPage(
        "https://gmis.ahu.edu.cn/gmis5/(S(session))/student/default/home", 200,
        """<h1>研究生教育管理信息系统</h1><span>G20260001</span><a href="logout">退出</a><a>个人培养计划</a>"""
    )

    @Test fun authenticatedGraduateStudentPageIsAccepted() {
        assertTrue(GmisSessionVerifier.isStudentSession(graduateHome, student.xh, student.name))
    }

    @Test fun http200LoginErrorOrRoleChoicePagesAreRejected() {
        val pages = listOf(
            graduateHome.copy(url = "https://gmis.ahu.edu.cn/gmis5/home/login"),
            graduateHome.copy(url = "https://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx"),
            graduateHome.copy(html = "<title>研究生教育管理信息系统登录</title><input type=password>"),
            graduateHome.copy(html = graduateHome.html + "<span>请选择角色</span>"),
            graduateHome.copy(html = graduateHome.html + "<div id=errorInfo>账号异常</div>"),
            graduateHome.copy(status = 500)
        )
        pages.forEach { assertFalse(it.toString(), GmisSessionVerifier.isStudentSession(it, student.xh, student.name)) }
    }

    @Test fun teacherSessionsAndUnrelatedAccountsAreRejected() {
        assertFalse(GmisSessionVerifier.isStudentSession(graduateHome.copy(html = graduateHome.html + "教师端"), student.xh, student.name))
        assertFalse(GmisSessionVerifier.isStudentSession(graduateHome, "another-account", "Another User"))
        assertFalse(GmisSessionVerifier.isStudentSession(graduateHome.copy(url = "https://gmis.ahu.edu.cn.evil.test/gmis5/home/index"), student.xh, student.name))
    }

    @Test fun reusesEstablishedCasSessionWithoutPostingPasswordAgain() = runTest {
        var requests = 0
        val login = AcademicPortalLogin { _, fields ->
            assertNull(fields)
            requests++
            graduateHome
        }
        assertEquals(PortalLoginStatus.SUCCESS, login.login(AcademicPortalLogin.GMIS_ENTRY, student.xh, "secret", student).status)
        assertEquals(1, requests)
    }

    @Test fun casFormRetainsExactGmisServiceAndDynamicExecution() = runTest {
        val casUrl = "https://one.ahu.edu.cn/cas/login?service=http://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx"
        val calls = mutableListOf<Pair<String, Map<String, String>?>>()
        val login = AcademicPortalLogin { url, fields ->
            calls += url to fields
            when (calls.size) {
                1 -> PortalPage(casUrl, 200, """
                    <form id="loginForm" action="/cas/login?service=http://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx">
                    <input name="lt" value="LT-test"><input name="execution" value="e9s8"></form>
                """.trimIndent())
                2 -> PortalPage(url, 200, """{"info":"success"}""")
                else -> graduateHome
            }
        }
        assertEquals(PortalLoginStatus.SUCCESS, login.login(AcademicPortalLogin.GMIS_ENTRY, student.xh, "secret", student).status)
        assertEquals(casUrl, calls.last().first)
        assertEquals("e9s8", calls.last().second?.get("execution"))
        assertEquals("LT-test", calls.last().second?.get("lt"))
        assertFalse(calls.last().second!!.containsValue("secret"))
    }

    @Test fun undergraduateSuccessRequiresTheCorrectHostAndHomePath() = runTest {
        for (url in listOf(
            "https://jw.ahu.edu.cn/student/sso/error?errorMessage=failed",
            "https://one.ahu.edu.cn/student/home",
            "https://jw.ahu.edu.cn/student/home?ignored=1"
        )) {
            val result = AcademicPortalLogin { _, _ -> PortalPage(url, 200, "") }
                .login(AcademicPortalLogin.UNDERGRADUATE_ENTRY, student.xh, "secret", student)
            assertEquals(if (url.contains("jw.ahu.edu.cn/student/home")) PortalLoginStatus.SUCCESS else PortalLoginStatus.REJECTED, result.status)
        }
    }

    @Test fun untrustedCasFormNeverReceivesCredentials() = runTest {
        var calls = 0
        val login = AcademicPortalLogin { _, _ ->
            calls++
            PortalPage("https://one.ahu.edu.cn/cas/login", 200,
                """<form id="loginForm" action="https://example.com/collect"><input name="lt" value="test"></form>""")
        }
        assertEquals(PortalLoginStatus.UNAVAILABLE, login.login(AcademicPortalLogin.GMIS_ENTRY, student.xh, "secret", student).status)
        assertEquals(1, calls)
    }

    @Test fun gmisTicketTransportIsHttpsWithoutRewritingServiceParameter() {
        val url = AcademicPortalHttp.securePortalUrl("http://gmis.ahu.edu.cn/gmis5/oauthLogin/ahdx?ticket=ST-test")
        assertEquals("https", url.scheme)
        assertEquals(443, url.port)
        assertEquals("ST-test", url.queryParameter("ticket"))
        assertFailsWith<IOException> { AcademicPortalHttp.securePortalUrl("https://evil.test/") }
        assertFailsWith<IOException> { AcademicPortalHttp.securePortalUrl("http://one.ahu.edu.cn/cas/login") }
    }

    private val studentShell = PortalPage(
        "https://gmis.ahu.edu.cn/gmis5/(S(session))/student/default/index", 200,
        """<html><title>学生端</title><iframe src="home"></iframe></html>"""
    )

    @Test fun liveStudentShellIsConfirmedUsingItsProtectedHomeFrame() = runTest {
        val visited = mutableListOf<String>()
        val login = AcademicPortalLogin { url, fields ->
            assertNull(fields)
            visited += url
            if (visited.size == 1) studentShell
            else PortalPage(url, 200, "<html><title>学生首页</title><div>通知公告</div></html>")
        }
        assertEquals(PortalLoginStatus.SUCCESS, login.login(
            AcademicPortalLogin.GMIS_ENTRY, student.xh, "secret", student
        ).status)
        assertEquals("https://gmis.ahu.edu.cn/gmis5/(S(session))/student/default/home", visited.last())
    }

    @Test fun studentShellDoesNotProveLoginIfProtectedFrameRedirectsToLogin() = runTest {
        var visited = 0
        val login = AcademicPortalLogin { _, _ ->
            if (++visited == 1) studentShell
            else PortalPage("https://gmis.ahu.edu.cn/gmis5/(S(session))/home/stulogin", 200,
                "<title>研究生教育管理信息系统登录</title><input type=password>")
        }
        assertEquals(PortalLoginStatus.REJECTED, login.login(
            AcademicPortalLogin.GMIS_ENTRY, student.xh, "secret", student
        ).status)
    }

    @Test fun studentShellRejectsFramesOnAnotherHostOrSession() {
        assertNull(GmisSessionVerifier.studentHomeUrl(studentShell.copy(
            html = """<title>学生端</title><iframe src="https://evil.test/student/default/home"></iframe>"""
        )))
        assertNull(GmisSessionVerifier.studentHomeUrl(studentShell.copy(
            html = """<title>学生端</title><iframe src="/gmis5/(S(other))/student/default/home"></iframe>"""
        )))
    }

    @Test fun expiredFrameCannotFallBackToIdentityTextInStudentShell() {
        val shell = studentShell.copy(html = studentShell.html + graduateHome.html)
        assertFalse(GmisSessionVerifier.isStudentSession(shell, student.xh, student.name,
            PortalPage("https://gmis.ahu.edu.cn/gmis5/home/stulogin", 200, "<input type=password>")))
        assertFalse(GmisSessionVerifier.isStudentSession(
            graduateHome.copy(url = "https://gmis.ahu.edu.cn/gmis5/home/index"), student.xh, student.name
        ))
    }
}
