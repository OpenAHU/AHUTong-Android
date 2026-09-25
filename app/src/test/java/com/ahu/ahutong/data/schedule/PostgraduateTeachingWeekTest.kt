package com.ahu.ahutong.data.schedule

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class PostgraduateTeachingWeekTest {
    @Test fun enteredWeekAnchorsItsMondayRatherThanTheDayOfEntry() {
        assertEquals(LocalDate.parse("2026-09-07"),
            PostgraduateTeachingWeek.firstMonday(3, LocalDate.parse("2026-09-25")))
    }

    @Test fun advancesAtMondayWithoutChangingWithinAWeek() {
        val anchor = LocalDate.parse("2026-09-07")
        assertEquals(3, PostgraduateTeachingWeek.weekOn(anchor, LocalDate.parse("2026-09-25")))
        assertEquals(3, PostgraduateTeachingWeek.weekOn(anchor, LocalDate.parse("2026-09-27")))
        assertEquals(4, PostgraduateTeachingWeek.weekOn(anchor, LocalDate.parse("2026-09-28")))
    }

    @Test fun persistsAsDateAndAllowsChangingTheWeekLater() {
        val today = LocalDate.parse("2026-09-25")
        val old = PostgraduateTeachingWeek.firstMonday(3, today)
        val changed = PostgraduateTeachingWeek.firstMonday(4, today)
        assertNotEquals(old, changed)
        assertEquals(4, PostgraduateTeachingWeek.weekOn(PostgraduateTeachingWeek.parseStored(changed.toString())!!, today))
    }

    @Test fun rejectsInvalidInputAndCorruptStoredAnchors() {
        for (week in listOf(-1, 0, 61, Int.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { PostgraduateTeachingWeek.firstMonday(week, LocalDate.now()) }
        }
        assertNull(PostgraduateTeachingWeek.parseStored(null))
        assertNull(PostgraduateTeachingWeek.parseStored("not-a-date"))
        assertNull(PostgraduateTeachingWeek.parseStored("2026-09-25"))
    }

    @Test fun semestersHaveIndependentGraduateStorageKeys() {
        assertNotEquals(PostgraduateTeachingWeek.storageKey("54"), PostgraduateTeachingWeek.storageKey("55"))
        assertEquals("gmis.54.first_week_monday", PostgraduateTeachingWeek.storageKey("54"))
        assertFailsWith<IllegalArgumentException> { PostgraduateTeachingWeek.storageKey("../other") }
    }

    @Test fun handlesYearBoundaryAndDatesBeforeTheAnchor() {
        val anchor = LocalDate.parse("2026-12-28")
        assertEquals(1, PostgraduateTeachingWeek.weekOn(anchor, LocalDate.parse("2027-01-03")))
        assertEquals(2, PostgraduateTeachingWeek.weekOn(anchor, LocalDate.parse("2027-01-04")))
        assertEquals(0, PostgraduateTeachingWeek.weekOn(anchor, LocalDate.parse("2026-12-27")))
    }
}
