package com.ahu.ahutong.data.notice

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusNoticeSyncEngineTest {
    private val today = LocalDate.of(2026, 9, 25)
    private val sevenDaysMillis = 7L * 24 * 60 * 60 * 1_000
    private fun notice(source: String, id: String) = CampusNotice(
        source, id, "公告$id", "2026-09-25", "https://www.ahu.edu.cn/2026/0925/$id/page.htm", 0, false
    )

    @Test fun `first sync creates a read baseline and next new item becomes unread`() = runTest {
        val source = FakeSource("university", listOf(notice("university", "one")))
        val store = MemoryStore()
        val fence = FakeFence("student-a")
        val engine = engine(listOf(source), store, fence)

        val first = engine.sync("student-a")
        assertEquals(0, first.newUnread.size)
        assertEquals(0, first.snapshot!!.unreadCount)
        assertEquals(today.toString(), first.snapshot.sourceStatuses["university"]?.lastSuccessfulSyncDate)
        engine.sync("student-a")
        assertEquals(1, source.fetchCount)

        source.items = listOf(notice("university", "two"))
        val refreshed = engine.sync("student-a", force = true)
        assertEquals(listOf("two"), refreshed.newUnread.map(CampusNotice::articleId))
        assertEquals(1, refreshed.snapshot!!.unreadCount)
        assertEquals(2, source.fetchCount)
    }

    @Test fun `one source failure does not suppress another and is retried without recording success`() = runTest {
        val university = FakeSource("university", listOf(notice("university", "one")))
        val college = FakeSource("college-cs", emptyList(), IOException("offline"))
        val engine = engine(listOf(university, college), MemoryStore(), FakeFence("student-a"))

        val first = engine.sync("student-a").snapshot!!
        assertEquals(today.toString(), first.sourceStatuses["university"]?.lastSuccessfulSyncDate)
        assertNull(first.sourceStatuses["college-cs"]?.lastSuccessfulSyncDate)
        assertEquals("更新失败，请稍后重试", first.sourceStatuses["college-cs"]?.lastError)

        college.failure = null
        college.items = listOf(notice("college-cs", "college-one"))
        val second = engine.sync("student-a")
        assertEquals(1, university.fetchCount)
        assertEquals(2, college.fetchCount)
        assertEquals(today.toString(), second.snapshot!!.sourceStatuses["college-cs"]?.lastSuccessfulSyncDate)
    }

    @Test fun `HTTP 412 never becomes a successful or empty sync`() = runTest {
        val source = FakeSource("university", emptyList(), CampusNoticeAccessBlockedException())
        val engine = engine(listOf(source), MemoryStore(), FakeFence("student-a"))

        val first = engine.sync("student-a").snapshot!!
        assertNull(first.sourceStatuses["university"]?.lastSuccessfulSyncDate)
        assertEquals("访问受限（HTTP 412）", first.sourceStatuses["university"]?.lastError)
        engine.sync("student-a")
        assertEquals(2, source.fetchCount)
    }

    @Test fun `graduate source is skipped for undergraduates and enabled for graduate account`() = runTest {
        val university = FakeSource("university", listOf(notice("university", "university-one")))
        val graduate = FakeSource("postgraduate", listOf(notice("postgraduate", "graduate-one")))
        val fence = FakeFence("undergraduate")
        val store = MemoryStore()
        val engine = CampusNoticeSyncEngine(
            sources = listOf(university, graduate), store = store, fence = fence,
            isSourceEnabled = { source, account -> source.sourceId != "postgraduate" || account == "graduate" },
            today = { today }, nowMillis = { 123L }
        )

        val undergraduate = engine.sync("undergraduate").snapshot!!
        assertEquals(0, graduate.fetchCount)
        assertFalse("postgraduate" in undergraduate.sourceStatuses)

        fence.account = "graduate"
        fence.generation++
        val postgraduate = engine.sync("graduate").snapshot!!
        assertEquals(1, graduate.fetchCount)
        assertEquals(today.toString(), postgraduate.sourceStatuses["postgraduate"]?.lastSuccessfulSyncDate)
    }

    @Test fun `account switch during a request discards the old result`() = runTest {
        val fence = FakeFence("student-a")
        val source = FakeSource("university", listOf(notice("university", "one")))
        source.onFetch = { fence.account = "student-b"; fence.generation++ }
        val store = MemoryStore()
        val engine = engine(listOf(source), store, fence)

        val result = engine.sync("student-a")

        assertTrue(result.staleAccount)
        assertNull(result.snapshot)
        assertTrue(store.read("student-a").notices.isEmpty())
        assertTrue(store.read("student-b").notices.isEmpty())
    }

    @Test fun `read state and notification setting stay within the active account`() = runTest {
        val source = FakeSource("university", listOf(notice("university", "one")))
        val fence = FakeFence("student-a")
        val store = MemoryStore()
        val engine = engine(listOf(source), store, fence)
        engine.sync("student-a")
        source.items = listOf(notice("university", "two"))
        engine.sync("student-a", force = true)
        assertEquals(1, engine.snapshot("student-a").unreadCount)

        fence.account = "student-b"
        fence.generation++
        assertNull(engine.markRead("student-a", null))
        engine.setNotificationsEnabled("student-b", true)
        assertEquals(1, store.read("student-a").unreadCount)
        assertFalse(store.read("student-a").notificationsEnabled)
        assertTrue(store.read("student-b").notificationsEnabled)
        fence.account = "student-a"
        fence.generation++
        assertEquals(0, engine.markRead("student-a", "two")!!.unreadCount)
    }

    @Test fun `unread notices older than seven full days become read on open`() = runTest {
        val now = 2_000_000_000_000L
        val store = MemoryStore()
        store.states["student-a"] = CampusNoticeSnapshot(
            accountId = "student-a",
            notices = listOf(
                notice("university", "expired").copy(discoveredAtMillis = now - sevenDaysMillis - 1),
                notice("university", "boundary").copy(discoveredAtMillis = now - sevenDaysMillis),
                notice("university", "recent").copy(discoveredAtMillis = now - sevenDaysMillis + 1)
            )
        )
        val engine = CampusNoticeSyncEngine(
            sources = emptyList(), store = store, fence = FakeFence("student-a"),
            today = { today }, nowMillis = { now }
        )

        val opened = engine.snapshot("student-a")

        assertEquals(listOf(true, false, false), opened.notices.map(CampusNotice::read))
        assertEquals(2, opened.unreadCount)
        assertEquals(opened, store.read("student-a"))
    }

    @Test fun `expiry is persisted even when daily source refresh is skipped`() = runTest {
        val now = 2_000_000_000_000L
        val source = FakeSource("university", emptyList())
        val store = MemoryStore()
        store.states["student-a"] = CampusNoticeSnapshot(
            accountId = "student-a",
            notices = listOf(notice("university", "old").copy(discoveredAtMillis = now - sevenDaysMillis - 1)),
            sourceStatuses = mapOf("university" to CampusNoticeSourceStatus("university", lastSuccessfulSyncDate = today.toString()))
        )
        val engine = CampusNoticeSyncEngine(
            sources = listOf(source), store = store, fence = FakeFence("student-a"),
            today = { today }, nowMillis = { now }
        )

        assertEquals(0, engine.sync("student-a").snapshot!!.unreadCount)
        assertTrue(store.read("student-a").notices.single().read)
        assertEquals(0, source.fetchCount)
    }

    @Test fun `baseline seeds a history cursor and loadMore appends older read notices`() = runTest {
        val pages = mapOf<String?, CampusNoticePage>(
            null to CampusNoticePage(listOf(notice("university", "one")), "https://www.ahu.edu.cn/15046/list2.htm"),
            "https://www.ahu.edu.cn/15046/list2.htm" to CampusNoticePage(
                listOf(notice("university", "two")),
                "https://www.ahu.edu.cn/15046/list3.htm"
            ),
            "https://www.ahu.edu.cn/15046/list3.htm" to CampusNoticePage(listOf(notice("university", "three")), null)
        )
        val source = FakePagedSource("university", pages)
        val store = MemoryStore()
        val engine = engine(listOf(source), store, FakeFence("student-a"))

        val baseline = engine.sync("student-a").snapshot!!
        assertEquals(listOf("one"), baseline.notices.map(CampusNotice::articleId))
        assertEquals("https://www.ahu.edu.cn/15046/list2.htm", baseline.sourceStatuses["university"]?.nextPageUrl)
        assertFalse(baseline.sourceStatuses["university"]?.historyExhausted == true)

        val second = engine.loadMore("student-a").snapshot!!
        assertEquals(listOf("one", "two"), second.notices.map(CampusNotice::articleId))
        assertEquals(0, second.unreadCount)
        assertEquals("https://www.ahu.edu.cn/15046/list3.htm", second.sourceStatuses["university"]?.nextPageUrl)

        val third = engine.loadMore("student-a").snapshot!!
        assertEquals(listOf("one", "two", "three"), third.notices.map(CampusNotice::articleId))
        assertTrue(third.sourceStatuses["university"]?.historyExhausted == true)
        assertNull(third.sourceStatuses["university"]?.nextPageUrl)

        val fetches = source.fetchPageCount
        engine.loadMore("student-a")
        assertEquals(fetches, source.fetchPageCount)
    }

    @Test fun `legacy snapshots without a cursor skip the already seen first page`() = runTest {
        val pages = mapOf<String?, CampusNoticePage>(
            null to CampusNoticePage(listOf(notice("university", "one")), "https://www.ahu.edu.cn/15046/list2.htm"),
            "https://www.ahu.edu.cn/15046/list2.htm" to CampusNoticePage(listOf(notice("university", "two")), null)
        )
        val source = FakePagedSource("university", pages)
        val store = MemoryStore()
        store.states["student-a"] = CampusNoticeSnapshot(
            accountId = "student-a",
            notices = listOf(notice("university", "one")),
            sourceStatuses = mapOf(
                "university" to CampusNoticeSourceStatus(
                    sourceId = "university",
                    lastSuccessfulSyncDate = today.toString()
                )
            )
        )
        val engine = engine(listOf(source), store, FakeFence("student-a"))

        val result = engine.loadMore("student-a").snapshot!!

        assertEquals(listOf("one", "two"), result.notices.map(CampusNotice::articleId))
        assertEquals(2, source.fetchPageCount)
        assertTrue(result.sourceStatuses["university"]?.historyExhausted == true)
    }

    @Test fun `loadMore can target a single source`() = runTest {
        val university = FakePagedSource(
            "university",
            mapOf(null to CampusNoticePage(listOf(notice("university", "one")), "u2"))
        )
        val postgraduate = FakePagedSource(
            "postgraduate",
            mapOf(null to CampusNoticePage(listOf(notice("postgraduate", "g1")), "g2"))
        )
        val engine = engine(listOf(university, postgraduate), MemoryStore(), FakeFence("student-a"))
        engine.sync("student-a")

        engine.loadMore("student-a", sourceId = "postgraduate")

        assertEquals(1, university.fetchPageCount)
        assertEquals(2, postgraduate.fetchPageCount)
    }

    private fun engine(sources: List<CampusNoticeSource>, store: MemoryStore, fence: FakeFence) =
        CampusNoticeSyncEngine(sources, store, fence, today = { today }, nowMillis = { 123L })

    private class FakeSource(
        override val sourceId: String,
        var items: List<CampusNotice>,
        var failure: Exception? = null
    ) : CampusNoticeSource {
        var fetchCount = 0
        var onFetch: () -> Unit = {}
        override suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice> {
            fetchCount++
            onFetch()
            failure?.let { throw it }
            return items
        }
    }

    private class FakePagedSource(
        override val sourceId: String,
        private val pages: Map<String?, CampusNoticePage>
    ) : PagedCampusNoticeSource {
        var fetchPageCount = 0
        override suspend fun fetchSince(seenIds: Set<String>, firstSync: Boolean): List<CampusNotice> =
            error("paged source should not use fetchSince in these tests")

        override suspend fun fetchPage(pageUrl: String?): CampusNoticePage {
            fetchPageCount++
            return pages[pageUrl] ?: throw IOException("missing page: $pageUrl")
        }
    }

    private class MemoryStore : CampusNoticeStore {
        val states = mutableMapOf<String, CampusNoticeSnapshot>()
        override fun read(accountId: String) = states[accountId] ?: CampusNoticeSnapshot(accountId)
        override fun write(accountId: String, snapshot: CampusNoticeSnapshot) {
            states[accountId] = snapshot
        }
        override fun clearAll() = states.clear()
    }

    private class FakeFence(var account: String?) : CampusNoticeAccountFence {
        var generation = 1L
        override fun currentAccountId(): String? = account
        override fun generation(): Long = generation
        override suspend fun commitIfCurrent(generation: Long, accountId: String, action: () -> Unit): Boolean {
            if (this.generation != generation || account != accountId) return false
            action()
            return true
        }
    }
}
