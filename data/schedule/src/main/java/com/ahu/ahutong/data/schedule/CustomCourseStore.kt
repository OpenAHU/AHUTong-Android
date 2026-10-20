package com.ahu.ahutong.data.schedule

import kotlinx.coroutines.flow.StateFlow

/**
 * 自定义课程的本地存储端口。
 *
 * 自定义课程不属于教务数据，不写进课表缓存（否则下一次刷新会把它们覆盖掉），
 * 而是按「账号 + 学期」单独落盘，读课表时再合并（[CustomCourseMapper.merge]）。
 * [revision] 在写入后自增，前台课表与后台小组件据此重读缓存。
 */
interface CustomCourseStore {

    /** 当前学期键（形如 2024-2025-1）；还没确定学期时为 null。 */
    fun currentSemesterKey(): String?

    /** 当前学期的自定义课程。 */
    fun currentCourses(): List<CustomCourse>

    /** 指定学期的自定义课程；读取失败时返回空列表。 */
    fun coursesFor(semesterKey: String): List<CustomCourse>

    /** 覆盖保存当前学期的自定义课程，并递增 [revision]。 */
    suspend fun saveCurrent(courses: List<CustomCourse>)

    /** 每次保存后自增；后台组件只关心「变过没有」。 */
    val revision: StateFlow<Long>
}
