package com.ahu.ahutong.data.schedule.gmis

import org.junit.Assert.assertEquals
import org.junit.Test

class GmisCourseNameFormatterTest {
    @Test fun removesCohortFromPlacedAndUnplacedCourses() {
        val placed = GmisCourse("数字图像处理（26级研究生）", "教师", "教室", 5, 11, 13,
            setOf(8, 9), "8-9周", "19:00-21:25", "")
        val unplaced = placed.copy(name = "26级研究生-专题活动", startSection = null, endSection = null)

        val grid = GmisTimetableAdapter.adapt(GmisTimetable(listOf(placed, unplaced), emptyList()))

        assertEquals("数字图像处理", grid.courses.single().name)
        assertEquals("专题活动", grid.unplaced.single().name)
        assertEquals("数字图像处理（26级研究生）", placed.name)
    }

    @Test fun retainsReadableNamesWhenOnlyTheCohortLabelIsPresent() {
        assertEquals("高等数学", GmisCourseNameFormatter.display("高等数学【26级研究生】"))
        assertEquals("未命名课程", GmisCourseNameFormatter.display("26级研究生"))
        assertEquals("普通课程", GmisCourseNameFormatter.display("普通课程"))
    }
}
