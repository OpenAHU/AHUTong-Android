package com.ahu.ahutong.assistant.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantModelLibraryTest {

    private fun profile(id: String, addedAt: Long) = AssistantModelProfile(
        id = id,
        name = id,
        baseUrl = "https://api.example.com/v1",
        model = id,
        addedAt = addedAt,
    )

    @Test
    fun `first added model is the default and list is ordered by time`() {
        val snapshot = AssistantModelLibrarySnapshot(
            activeId = null,
            profiles = listOf(profile("b", 20), profile("a", 10), profile("c", 30)),
        )
        val normalized = normalizeSnapshot(snapshot)
        assertEquals(listOf("a", "b", "c"), normalized.profiles.map { it.id })
        assertEquals("a", normalized.active?.id)
    }

    @Test
    fun `missing active id falls back to first`() {
        val snapshot = AssistantModelLibrarySnapshot(
            activeId = "gone",
            profiles = listOf(profile("a", 10), profile("b", 20)),
        )
        assertEquals("a", normalizeSnapshot(snapshot).active?.id)
    }

    @Test
    fun `deleting the active model falls back to the first remaining`() {
        val snapshot = AssistantModelLibrarySnapshot(
            activeId = "b",
            profiles = listOf(profile("a", 10), profile("b", 20), profile("c", 30)),
        )
        val next = nextSnapshotAfterDelete(snapshot, "b")
        assertEquals(listOf("a", "c"), next.profiles.map { it.id })
        assertEquals("a", next.active?.id)
    }

    @Test
    fun `deleting a non active model keeps the selection`() {
        val snapshot = AssistantModelLibrarySnapshot(
            activeId = "c",
            profiles = listOf(profile("a", 10), profile("b", 20), profile("c", 30)),
        )
        val next = nextSnapshotAfterDelete(snapshot, "a")
        assertEquals("c", next.active?.id)
        assertEquals(listOf("b", "c"), next.profiles.map { it.id })
    }

    @Test
    fun `deleting the last model leaves an empty library`() {
        val snapshot = AssistantModelLibrarySnapshot(activeId = "a", profiles = listOf(profile("a", 10)))
        val next = nextSnapshotAfterDelete(snapshot, "a")
        assertTrue(next.profiles.isEmpty())
        assertNull(next.active)
    }

    @Test
    fun `invalid entries are dropped during normalization`() {
        val snapshot = AssistantModelLibrarySnapshot(
            activeId = null,
            profiles = listOf(
                profile("ok", 10),
                AssistantModelProfile(id = "", baseUrl = "https://x", model = "x", addedAt = 20),
                AssistantModelProfile(id = "no-host", baseUrl = "", model = "x", addedAt = 30),
                AssistantModelProfile(id = "no-model", baseUrl = "https://x", model = "", addedAt = 40),
            ),
        )
        assertEquals(listOf("ok"), normalizeSnapshot(snapshot).profiles.map { it.id })
    }
}
