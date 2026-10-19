package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.data.crawler.api.adwmh.createAdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.createJwxtApi
import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 固定客户端选择的参数契约；校方两参数的服务端含义仍需真实账号验证。 */
class ScheduleSemesterProtocolContractTest {
    @Test
    fun `history request uses selected real id in path and current id as context`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody(CURRENT_PAGE))
            server.enqueue(MockResponse().setBody("""{"studentTableVms":[]}"""))

            val courses = gateway(server).getSemesterSchedule(7).valueOrNull()

            assertNotNull(courses)
            assertTrue(courses.isEmpty())
            assertEquals("/student/for-std/course-table", server.takeRequest().requestUrl?.encodedPath)
            val request = server.takeRequest()
            assertEquals("/student/for-std/course-table/semester/7/print-data", request.requestUrl?.encodedPath)
            assertEquals("42", request.requestUrl?.queryParameter("semesterId"))
            assertEquals("false", request.requestUrl?.queryParameter("hasExperiment"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `all profile grade responses supplement history even when page already has a past semester`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody(
                    CURRENT_PAGE + """<select id="semester"><option value="7">2025-2026-2</option></select>"""
                )
            )

            val majorGrades = Gson().fromJson(
                """{"semesterId2studentGrades":{"3":[{"semesterId":3,"semesterName":"2024-2025-1"}]}}""",
                GradeResponse::class.java
            )
            val minorGrades = Gson().fromJson(
                """{"semesterId2studentGrades":{"2":[],"5":[{"semesterId":5,"semesterName":"2024-2025-2"}]}}""",
                GradeResponse::class.java
            )
            var gradeReads = 0
            val semesters = gateway(server, semesterGrades = {
                gradeReads += 1
                listOf(majorGrades, minorGrades)
            }).getScheduleSemesters().valueOrNull()

            assertNotNull(semesters)
            assertEquals(setOf(42, 7, 3, 2, 5), semesters.map { it.id }.toSet())
            assertEquals(1, gradeReads)
            // 原始成绩读操作由 provider 注入；HTTP fixture 只负责课表页。
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `current plus future page still merges real history from academic responses`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setBody(
                    CURRENT_PAGE + """<select id="semester"><option value="81">2026-2027-2</option></select>"""
                )
            )
            val response = Gson().fromJson(
                """{"semesterId2studentGrades":{"3":[{"semesterId":3,"semesterName":"2024-2025-1"}]}}""",
                GradeResponse::class.java
            )

            val semesters = gateway(server, semesterGrades = { listOf(response) })
                .getScheduleSemesters().valueOrNull()

            assertNotNull(semesters)
            assertEquals(setOf(42, 81, 3), semesters.map { it.id }.toSet())
            assertEquals("2024-2025-1", semesters.single { it.id == 3 }.name)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun gateway(
        server: MockWebServer,
        semesterGrades: suspend () -> List<GradeResponse> = { emptyList() }
    ): CrawlerDataSource {
        val client = OkHttpClient()
        val url = server.url("/").toString()
        return CrawlerDataSource(
            createJwxtApi(client, url),
            createAdwmhApi(client, url),
            semesterResponseProvider = semesterGrades
        )
    }

    private companion object {
        const val CURRENT_PAGE = """<script>var currentSemester = {"id":42,"name":"2026-2027-1"};</script>"""
    }
}
