package com.ahu.ahutong.data.crawler

import com.ahu.ahutong.data.crawler.model.jwxt.GradeResponse
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleSemesterParserTest {
    @Test
    fun `semester select preserves real nonsequential ids and ignores other selects`() {
        val catalog = ScheduleSemesterParser.parse(
            """
                <select name="semesterId">
                  <option value="">请选择</option><option value="-1">全部</option>
                  <option value="42" selected>2026-2027-1</option>
                  <option value="7">2025-2026-2</option>
                </select>
                <select name="campus"><option value="999">磬苑</option></select>
                <script>var currentSemester = {"id":42,"name":"2026-2027-1"};</script>
            """.trimIndent()
        )
        assertEquals(setOf(42, 7), catalog.semesters.map { it.id }.toSet())
        assertTrue(catalog.semesters.single { it.id == 42 }.isCurrent)
        assertFalse(catalog.semesters.single { it.id == 7 }.isCurrent)
    }

    @Test
    fun `script directory supports nested dates and delimiters inside strings`() {
        val catalog = ScheduleSemesterParser.parse(
            """
                <script>
                  const currentSemester = {"id":42,"nameZh":"2026-2027-1"};
                  let semesters = [
                    {"id":"7","nameZh":"2025-2026-2","startDate":{"year":2026,"monthOfYear":3,"dayOfMonth":2},"extra":"[}]"},
                    {"id":3,"name":"2025-2026-1","startDate":"2025-09-01"},
                    {"name":"没有真实 ID 的学期"}
                  ];
                </script>
            """.trimIndent()
        )
        assertEquals(setOf(42, 7, 3), catalog.semesters.map { it.id }.toSet())
        assertEquals("2026-03-02", catalog.semesters.single { it.id == 7 }.startDate)
    }

    @Test
    fun `JSON parse semester directory is decoded without inferring adjacent ids`() {
        val catalog = ScheduleSemesterParser.parse(
            """<script>
                var currentSemester = JSON.parse('{"id":81,"name":"2026-2027-1"}');
                var semesterList = JSON.parse('[{"id":4,"name":"2024-2025-2"}]');
            </script>"""
        )
        assertEquals(setOf(81, 4), catalog.semesters.map { it.id }.toSet())
    }

    @Test
    fun `current only page still exposes its actual id and start date`() {
        val catalog = ScheduleSemesterParser.parse(
            """<script>var currentSemester = {"id":42,"name":"2026-2027-1","startDate":{"year":2026,"monthOfYear":9,"dayOfMonth":7}};</script>"""
        )
        assertEquals(1, catalog.semesters.size)
        assertEquals(42, catalog.current.id)
        assertEquals("2026-09-07", catalog.current.startDate)
    }

    @Test
    fun `grade keys supplement semesters even when no grades are present`() {
        val response = Gson().fromJson(
            """{"semesterId2studentGrades":{"7":[{"semesterId":7,"semesterName":"2025-2026-2"}],"3":[],"garbage":[],"-1":[]}}""",
            GradeResponse::class.java
        )
        val semesters = ScheduleSemesterParser.fromGrades(response)
        assertEquals(setOf(7, 3), semesters.map { it.id }.toSet())
        assertEquals("2025-2026-2", semesters.single { it.id == 7 }.name)
    }

    @Test
    fun `login or unrecognized page fails instead of inventing current semester`() {
        assertFailsWith<IllegalStateException> {
            ScheduleSemesterParser.parse("<html><form action='/cas/login'>登录</form></html>")
        }
    }
}
