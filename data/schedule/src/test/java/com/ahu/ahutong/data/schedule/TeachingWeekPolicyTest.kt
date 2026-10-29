package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.ScheduleConfigBean
import java.time.ZoneId
import java.util.Date
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeachingWeekPolicyTest {

    @Test
    fun `in-semester response infers term start from current teaching week`() {
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = LocalDate.of(2026, 3, 18),
            remoteWeekIndex = 3,
            remoteIsInSemester = true,
            cachedStartDate = null,
            officialStartDate = null
        )

        assertTrue(position.isInSemester)
        assertEquals(3, position.week)
        assertEquals(LocalDate.of(2026, 3, 2), position.startDate)
    }

    @Test
    fun `holiday response uses official date and never becomes week one`() {
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = LocalDate.of(2026, 7, 21),
            remoteWeekIndex = 0,
            remoteIsInSemester = false,
            cachedStartDate = LocalDate.of(2026, 7, 20),
            officialStartDate = LocalDate.of(2026, 3, 2)
        )

        assertFalse(position.isInSemester)
        assertEquals(TeachingWeekPolicy.SCHEDULE_WEEK_COUNT, position.week)
        assertEquals(LocalDate.of(2026, 3, 2), position.startDate)
    }

    @Test
    fun `pre-semester holiday opens the first week with its real dates`() {
        val position = TeachingWeekPolicy.resolveRemotePosition(
            now = LocalDate.of(2026, 8, 15),
            remoteWeekIndex = 0,
            remoteIsInSemester = false,
            cachedStartDate = null,
            officialStartDate = LocalDate.of(2026, 9, 7)
        )

        assertFalse(position.isInSemester)
        assertEquals(1, position.week)
        assertEquals(LocalDate.of(2026, 9, 7), position.startDate)
    }

    @Test
    fun `stale holiday state expires when the semester begins`() {
        assertTrue(
            TeachingWeekPolicy.resolveLocalSemesterState(
                startDate = LocalDate.of(2026, 9, 7),
                date = LocalDate.of(2026, 9, 7),
                cachedState = false,
                stateObservedOn = LocalDate.of(2026, 9, 6)
            )
        )
    }

    @Test
    fun `stale in-semester state expires after the schedule range`() {
        assertFalse(
            TeachingWeekPolicy.resolveLocalSemesterState(
                startDate = LocalDate.of(2026, 3, 2),
                date = LocalDate.of(2026, 7, 20),
                cachedState = true,
                stateObservedOn = LocalDate.of(2026, 7, 19)
            )
        )
    }

    @Test
    fun `same-day remote holiday state remains authoritative`() {
        val date = LocalDate.of(2026, 10, 1)
        assertFalse(
            TeachingWeekPolicy.resolveLocalSemesterState(
                startDate = LocalDate.of(2026, 9, 7),
                date = date,
                cachedState = false,
                stateObservedOn = date
            )
        )
    }
    @Test
    fun `cached calendar follows the current date without mutating its semester anchor`() {
        val zone = ZoneId.of("Asia/Shanghai")
        for ((start, date, expected) in listOf(
            Triple("2026-10-05", "2026-10-22", 3 to 4),
            Triple("2026-10-05", "2026-10-26", 4 to 1),
            Triple("2026-12-14", "2027-01-01", 3 to 5),
            Triple("2026-01-05", "2026-07-29", 30 to 3)
        )) {
            val cached = ScheduleConfigBean().apply {
                startTime = Date.from(LocalDate.parse(start).atStartOfDay(zone).toInstant())
                week = 3
                weekDay = 3
                isShowAll = true
                isInSemester = true
            }
            val current = cached.forDate(LocalDate.parse(date),
                weekCount = PostgraduateTeachingWeek.MAX_WEEK, zoneId = zone)
            assertEquals(expected.first, current.week, date)
            assertEquals(expected.second, current.weekDay, date)
            assertTrue(current.isInSemester)
            assertTrue(current.isShowAll)
            assertEquals(3, cached.weekDay)
            assertNotSame(cached.startTime, current.startTime)
        }
        val expired = ScheduleConfigBean().apply {
            startTime = Date.from(LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant())
            isInSemester = true
        }.forDate(LocalDate.of(2027, 3, 1), zoneId = zone)
        assertFalse(expired.isInSemester)
        val missing = ScheduleConfigBean().apply { week = 0; startTime = null }
            .forDate(LocalDate.of(2026, 10, 22), zoneId = zone)
        assertEquals(0, missing.week)
        assertEquals(4, missing.weekDay)
        assertFalse(missing.isInSemester)
    }
}
