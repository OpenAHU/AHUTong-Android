package com.ahu.ahutong.data.crawler.manager

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CampusCardTokenRecoveryTest {
    @Test
    fun `usable token needs no retry or login`() = runTest {
        var fetches = 0
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = {
                fetches++
                CampusCardTokenAttempt(token = "available")
            },
            refreshSession = { error("must not log in") }
        )

        assertEquals("available", token)
        assertEquals(1, fetches)
    }

    @Test
    fun `failed OAuth exchange automatically obtains a fresh ticket and token`() = runTest {
        var fetches = 0
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = {
                fetches++
                if (fetches == 1) CampusCardTokenAttempt() else CampusCardTokenAttempt(token = "renewed")
            },
            refreshSession = { error("valid CAS cookies need no password login") }
        )

        assertEquals("renewed", token)
        assertEquals(2, fetches)
    }

    @Test
    fun `expired CAS logs in before fetching a new OAuth ticket`() = runTest {
        val calls = mutableListOf<String>()
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = {
                calls += "OAuth"
                if (calls.size == 1) CampusCardTokenAttempt(casLoginUrl = CAS_URL)
                else CampusCardTokenAttempt(token = "renewed")
            },
            refreshSession = { url ->
                assertEquals(CAS_URL, url)
                calls += "CAS"
                true
            }
        )

        assertEquals("renewed", token)
        assertEquals(listOf("OAuth", "CAS", "OAuth"), calls)
    }

    @Test
    fun `OAuth retry discovering expired CAS can still renew the login once`() = runTest {
        var fetches = 0
        var logins = 0
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = {
                when (++fetches) {
                    1 -> CampusCardTokenAttempt()
                    2 -> CampusCardTokenAttempt(casLoginUrl = CAS_URL)
                    3 -> CampusCardTokenAttempt(token = "renewed")
                    else -> error("must stop retrying")
                }
            },
            refreshSession = { logins++; true }
        )

        assertEquals("renewed", token)
        assertEquals(3, fetches)
        assertEquals(1, logins)
    }

    @Test
    fun `unavailable credentials stop after one OAuth retry`() = runTest {
        var fetches = 0
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = { fetches++; CampusCardTokenAttempt(token = " ") },
            refreshSession = { error("no CAS challenge was received") }
        )

        assertNull(token)
        assertEquals(2, fetches)
    }

    @Test
    fun `failed CAS renewal does not loop or fetch another ticket`() = runTest {
        var fetches = 0
        var logins = 0
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = { fetches++; CampusCardTokenAttempt(casLoginUrl = CAS_URL) },
            refreshSession = { logins++; false }
        )

        assertNull(token)
        assertEquals(1, fetches)
        assertEquals(1, logins)
    }

    @Test
    fun `CAS challenge persisting after login does not trigger a second login`() = runTest {
        var fetches = 0
        var logins = 0
        val token = fetchCampusCardTokenWithRecovery(
            fetchToken = { fetches++; CampusCardTokenAttempt(casLoginUrl = CAS_URL) },
            refreshSession = { logins++; true }
        )

        assertNull(token)
        assertEquals(2, fetches)
        assertEquals(1, logins)
    }

    @Test
    fun `cancelled OAuth fetch is propagated without retry`() = runTest {
        var fetches = 0
        assertFailsWith<CancellationException> {
            fetchCampusCardTokenWithRecovery(
                fetchToken = { fetches++; throw CancellationException("cancelled") },
                refreshSession = { error("must not log in") }
            )
        }
        assertEquals(1, fetches)
    }

    @Test
    fun `cancelled CAS renewal does not fetch another ticket`() = runTest {
        var fetches = 0
        assertFailsWith<CancellationException> {
            fetchCampusCardTokenWithRecovery(
                fetchToken = { fetches++; CampusCardTokenAttempt(casLoginUrl = CAS_URL) },
                refreshSession = { throw CancellationException("cancelled") }
            )
        }
        assertEquals(1, fetches)
    }

    private companion object {
        const val CAS_URL = "https://one.ahu.edu.cn/cas/login?service=campus-card"
    }
}
