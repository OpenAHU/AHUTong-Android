package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.schedule.gmis.*

import com.ahu.ahutong.data.crawler.login.AcademicPortalHttp
import com.ahu.ahutong.data.crawler.login.PortalPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class GmisScheduleClientTest {
    private val session = "https://gmis.ahu.edu.cn/gmis5/(S(fresh))/student/default/index"

    @Test fun queriesOnlyReadOnlyTimetableEndpointUsingSelectedTermAndFreshSession() = runTest {
        val requests = mutableListOf<Triple<String, Map<String, String>?, String>>()
        val client = GmisScheduleClient(openSession = { session }) { url, fields, referer ->
            requests += Triple(url, fields, referer)
            PortalPage(url, 200, if (fields == null)
                """[{"termcode":"93","termname":"当前学期","selected":true}]"""
                else """{"rows":[],"week":"Friday"}""")
        }
        val term = client.terms().single()
        assertTrue(client.timetable(term).courses.isEmpty())
        assertEquals("$BASE/student/default/bindterm", requests[0].first)
        assertEquals("$BASE/student/pygl/py_kbcx_ew", requests[1].first)
        assertEquals(mapOf("kblx" to "xs", "termcode" to "93"), requests[1].second)
        assertEquals("$BASE/student/pygl/xskbcx", requests[1].third)
    }

    @Test fun expiredSessionRefreshesUrlOnceAndRetriesTheRead() = runTest {
        var opens = 0
        val client = GmisScheduleClient(openSession = {
            opens++
            session.replace("fresh", "session$opens")
        }) { url, _, _ ->
            if (opens == 1) PortalPage("https://gmis.ahu.edu.cn/gmis5/home/stulogin", 200, "<html>登录</html>")
            else PortalPage(url, 200, """[{"termcode":"7","termname":"测试学期","selected":true}]""")
        }
        assertEquals("7", client.terms().single().code)
        assertEquals(2, opens)
    }

    @Test fun persistentSessionExpiryDoesNotLoop() = runTest {
        var opens = 0
        val client = GmisScheduleClient(openSession = { opens++; session }) { _, _, _ ->
            PortalPage("https://one.ahu.edu.cn/cas/login", 200, "<form>login</form>")
        }
        assertFailsWith<GmisSessionExpiredException> { client.terms() }
        assertEquals(2, opens)
    }

    @Test fun malformedDataIsNotHiddenAsEmptyCoursesOrRepeatedAuthentication() = runTest {
        var opens = 0
        val client = GmisScheduleClient(openSession = { opens++; session }) { url, _, _ ->
            PortalPage(url, 200, """{"error":"invalid"}""")
        }
        assertFailsWith<GmisProtocolException> { client.timetable(GmisTerm("1", "测试", true)) }
        assertEquals(1, opens)
    }

    @Test fun rejectsExternalLoginAndNonStudentSessionUrls() {
        for (url in listOf("http://gmis.ahu.edu.cn/gmis5/student/default/index",
            "https://example.com/gmis5/student/default/index", "https://gmis.ahu.edu.cn/gmis5/home/stulogin")) {
            assertFailsWith<GmisProtocolException> { GmisScheduleClient.timetablePage(url) }
        }
    }

    @Test fun transportRejectsEnrollmentEndpointsAndUnexpectedPostFieldsBeforeNetwork() = runTest {
        val ref = "$BASE/student/pygl/xskbcx"
        assertFailsWith<IllegalArgumentException> {
            AcademicPortalHttp.readGmis("$BASE/student/pygl/enroll", mapOf("course" to "test"), ref)
        }
        assertFailsWith<IllegalArgumentException> {
            AcademicPortalHttp.readGmis("$BASE/student/pygl/py_kbcx_ew", mapOf("kblx" to "xs", "termcode" to "1", "enroll" to "true"), ref)
        }
    }

    companion object {
        const val BASE = "https://gmis.ahu.edu.cn/gmis5/(S(fresh))"
    }
}
