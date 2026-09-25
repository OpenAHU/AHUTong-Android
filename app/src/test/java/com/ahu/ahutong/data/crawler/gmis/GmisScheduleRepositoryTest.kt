package com.ahu.ahutong.data.crawler.gmis

import com.ahu.ahutong.data.crawler.login.PortalPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import kotlin.test.assertFailsWith

class GmisScheduleRepositoryTest {
    private class MemoryCache : GmisScheduleCache {
        val termValues = mutableMapOf<String, List<GmisTerm>>()
        val tableValues = mutableMapOf<Pair<String, String>, GmisTimetable>()
        override fun terms(accountId: String) = termValues[accountId]
        override fun timetable(accountId: String, termCode: String) = tableValues[accountId to termCode]
        override fun saveTerms(accountId: String, terms: List<GmisTerm>) {
            termValues[accountId] = terms
        }
        override fun saveTimetable(accountId: String, termCode: String, timetable: GmisTimetable) {
            tableValues[accountId to termCode] = timetable
        }
    }

    private val session = "https://gmis.ahu.edu.cn/gmis5/(S(test))/student/default/index"
    private val termJson = """
        [{"termcode":"54","termname":"本学期","selected":true},
         {"termcode":"53","termname":"上学期","selected":false}]
    """.trimIndent()
    private val tableJson = """{"rows":[{"jcid":2,"sjbz":"上午","mc":"第2节<br/>(08:50-09:35)",
         "z5":"示例课程[2-10周] 示例教师 [教学楼101]"}],"week":"Friday"}"""

    private fun client(onRead: (String, Map<String, String>?) -> Unit) =
        GmisScheduleClient(openSession = { session }) { url, fields, _ ->
            onRead(url, fields)
            PortalPage(url, 200, if (fields == null) termJson else tableJson)
        }

    @Test fun reopeningCurrentTermUsesPerAccountCacheWithoutNetwork() = runTest {
        val cache = MemoryCache()
        var account = "graduate-A"
        var reads = 0
        val first = GmisScheduleRepository(cache, { client { _, _ -> reads++ } }, { account })
        assertEquals("54", first.current(account).selectedTerm.code)
        assertEquals(2, reads)
        assertEquals(1, first.current(account).timetable.courses.size)
        assertEquals(2, reads)
        val afterRestart = GmisScheduleRepository(cache, { error("Unexpected client") }, { account })
        assertEquals(1, afterRestart.current(account).timetable.courses.size)
        assertEquals(2, reads)
        assertNotNull(afterRestart.cachedCurrent(account))
    }

    @Test fun manualRefreshRequeriesTermAndTimetableWhileTermSwitchOnlyLoadsMissingTerm() = runTest {
        val cache = MemoryCache()
        var reads = 0
        val account = "graduate-A"
        val repository = GmisScheduleRepository(cache, { client { _, _ -> reads++ } }, { account })
        repository.current(account)
        val oldTerm = cache.termValues.getValue(account).first { it.code == "53" }
        assertEquals(1, repository.forTerm(account, oldTerm).courses.size)
        assertEquals(3, reads)
        repository.forTerm(account, oldTerm)
        assertEquals(3, reads)
        repository.refresh(account, preferredTermCode = "53")
        assertEquals(5, reads)
        assertEquals(1, repository.cachedForTerm(account, "54")?.courses?.size)
    }

    @Test fun cachesForDifferentAccountsNeverCross() = runTest {
        val cache = MemoryCache()
        var active = "graduate-A"
        var reads = 0
        val repository = GmisScheduleRepository(cache, { client { _, _ -> reads++ } }, { active })
        repository.current(active)
        active = "graduate-B"
        assertNull(repository.cachedCurrent("graduate-A"))
        repository.current(active)
        assertEquals(4, reads)
        assertEquals(setOf("graduate-A", "graduate-B"), cache.termValues.keys)
    }

    @Test fun accountSwitchDuringRequestNeverWritesOldAccountDataIntoNewAccount() = runTest {
        val cache = MemoryCache()
        var active = "graduate-A"
        val repository = GmisScheduleRepository(cache, {
            client { _, _ -> active = "graduate-B" }
        }, { active })
        assertFailsWith<CancellationException> { repository.current("graduate-A") }
        assertTrue(cache.termValues.isEmpty())
        assertTrue(cache.tableValues.isEmpty())
    }

    @Test fun malformedCacheIsDiscardedBeforeItCanBecomeAValidTimetable() {
        val term = GmisTerm("54", "本学期", true)
        val terms = listOf(term)
        assertEquals(terms, GmisCacheCodec.decodeTerms(GmisCacheCodec.encodeTerms(terms)))
        val timetable = GmisTimetable(
            listOf(GmisCourse("示例课程", "示例教师", "示例楼101", 2,
                2, 4, setOf(2, 4, 6), "2-6双周", "08:50-11:25", "")),
            listOf(GmisSection(2, "上午", "08:50-09:35"))
        )
        assertEquals(timetable, GmisCacheCodec.decodeTimetable(GmisCacheCodec.encodeTimetable(timetable)))
        assertNull(GmisCacheCodec.decodeTerms(null))
        assertNull(GmisCacheCodec.decodeTerms("""{"version":1,"terms":[{"termcode":"../54","termname":"错","selected":true}]}"""))
        assertNull(GmisCacheCodec.decodeTimetable("""{"version":1,"timetable":{"courses":null,"sections":[]}}"""))
        assertNull(GmisCacheCodec.decodeTimetable("""{"version":2,"timetable":{"courses":[],"sections":[]}}"""))
    }
}
