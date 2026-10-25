package com.ahu.ahutong.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryIndexRefreshPolicyTest {
    @Test
    fun `fresh compatible index is reused even when UI observes progress`() {
        val now = 50_000_000L

        assertTrue(
            RepositoryIndexRefreshPolicy.canReuse(
                cachedAtMillis = now - RepositoryIndexRefreshPolicy.AUTO_REFRESH_INTERVAL_MS + 1L,
                cachedVersion = 7,
                expectedVersion = 7,
                hasRootContents = true,
                nowMillis = now
            )
        )
    }

    @Test
    fun `stale incompatible or incomplete index is rebuilt`() {
        val now = 50_000_000L
        val staleAt = now - RepositoryIndexRefreshPolicy.AUTO_REFRESH_INTERVAL_MS

        assertFalse(RepositoryIndexRefreshPolicy.canReuse(staleAt, 7, 7, true, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now, 6, 7, true, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now, 7, 7, false, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now + 1L, 7, 7, true, now))
    }

    @Test
    fun `an unchanged server timestamp reuses a complete local index`() {
        assertTrue(RepositoryIndexRefreshPolicy.canReuseServerIndex(123L, 123L, true))
        assertFalse(RepositoryIndexRefreshPolicy.canReuseServerIndex(124L, 123L, true))
        assertFalse(RepositoryIndexRefreshPolicy.canReuseServerIndex(123L, 123L, false))
        assertFalse(RepositoryIndexRefreshPolicy.canReuseServerIndex(0L, 0L, true))
    }
}
