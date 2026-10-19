package com.ahu.ahutong.data.mock

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.schedule.ScheduleSemester

/** 仅 Debug 使用；固定模拟 ID 不参与生产学期发现。 */
internal object MockSemesterFixtures {
    const val currentId = 202620271
    val semesters = listOf(
        ScheduleSemester(currentId, "2026-2027-1", "2026-09-07", isCurrent = true),
        ScheduleSemester(202520262, "2025-2026-2", "2026-03-02"),
        ScheduleSemester(202520261, "2025-2026-1", "2025-09-01"),
        ScheduleSemester(202420252, "2024-2025-2", "2025-02-24"),
        ScheduleSemester(202420251, "2024-2025-1", "2024-09-02")
    )
    val historyIds = semesters.filterNot { it.isCurrent }.map { it.id }.toSet()

    fun courses(id: Int): List<Course> = when (id) {
        202520262 -> listOf(
            course(id, "编译原理", 1, 1, "博学南楼 A201"),
            course(id, "算法设计与分析", 2, 3, "文典阁 302"),
            course(id, "计算机组成原理", 3, 6, "笃行北楼 B305"),
            course(id, "软件测试", 4, 1, "博学南楼 A208")
        )
        202520261 -> listOf(
            course(id, "数据结构", 1, 3, "博学南楼 A102"),
            course(id, "概率论与数理统计", 2, 1, "文典阁 205"),
            course(id, "面向对象程序设计", 4, 6, "实验中心 301")
        )
        202420252 -> listOf(
            course(id, "高等数学 II", 1, 1, "博学南楼 A101"),
            course(id, "大学英语 II", 3, 3, "文典阁 206"),
            course(id, "程序设计基础", 5, 6, "实验中心 202")
        )
        // 目录存在但没有排课，用于验证历史预览的空课表状态。
        202420251 -> emptyList()
        else -> emptyList()
    }

    private fun course(id: Int, title: String, day: Int, start: Int, room: String): Course = Course().apply {
        name = title
        teacher = "历史课表 Mock 老师"
        location = room
        courseId = "MOCK-HISTORY-$id-$day-$start"
        setWeekday(day.toString())
        setStartTime(start.toString())
        setLength("2")
        setStartWeek("1")
        setEndWeek("16")
        weekIndexes = (1..16).toList()
    }
}
