package com.ahu.ahutong.data.preview

import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.data.session.AhuSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EarlyAccessRepositoryTest {
    @Test
    fun `only account ids have a subject with the shared server hash`() {
        assertNull(earlyAccessSubject(null))
        assertNull(earlyAccessSubject(""))
        assertNull(earlyAccessSubject("  "))
        assertEquals(
            "486bab3d69f62dd80ef68040035eec6184c3245c4025e038fa70fedd32fc6e4a",
            earlyAccessSubject("1001")
        )
    }

    @Test
    fun `unknown prompts locally while a saved no stays quiet without sending a hash`() = runTest {
        val fixture = Fixture()
        fixture.repository.synchronizeIdentity()
        assertTrue(fixture.repository.state.value.shouldPrompt)
        fixture.repository.refresh().getOrThrow()
        assertTrue(fixture.repository.state.value.shouldPrompt)
        fixture.repository.decline()
        fixture.repository.refresh().getOrThrow()
        assertEquals(EarlyAccessChoice.No, fixture.repository.state.value.choice)
        assertFalse(fixture.repository.state.value.shouldPrompt)
        assertEquals(EarlyAccessChoice.No, fixture.choices.read("alice"))
        fixture.identities.value = EarlyAccessIdentity(null, 2)
        fixture.repository.synchronizeIdentity()
        fixture.identities.value = EarlyAccessIdentity("alice", 3)
        val restarted = EarlyAccessRepository(fixture.api, fixture.identities, fixture.choices)
        restarted.refresh().getOrThrow()
        assertEquals(EarlyAccessChoice.No, restarted.state.value.choice)
        assertFalse(restarted.state.value.shouldPrompt)
        assertTrue(fixture.api.calls.isEmpty())
    }

    @Test
    fun `deferred stays quiet across refresh and identity recreation until next process`() = runTest {
        val fixture = Fixture()
        fixture.repository.refresh().getOrThrow()
        fixture.repository.defer()
        fixture.repository.refresh().getOrThrow()
        fixture.identities.value = EarlyAccessIdentity("bob", 2)
        fixture.repository.synchronizeIdentity()
        fixture.identities.value = EarlyAccessIdentity("alice", 3)
        fixture.repository.refresh().getOrThrow()
        assertEquals(EarlyAccessChoice.Deferred, fixture.repository.state.value.choice)
        assertFalse(fixture.repository.state.value.shouldPrompt)

        val restarted = EarlyAccessRepository(fixture.api, fixture.identities, fixture.choices)
        restarted.refresh().getOrThrow()
        assertTrue(restarted.state.value.shouldPrompt)
        assertTrue(fixture.api.calls.isEmpty())
    }

    @Test
    fun `saved consent fetches server membership and suppresses the invitation`() = runTest {
        val fixture = Fixture()
        fixture.choices.write("alice", EarlyAccessChoice.Yes)
        fixture.api.onMembership = { EarlyAccessMembership(it, true) }
        fixture.repository.refresh().getOrThrow()
        assertEquals(EarlyAccessChoice.Yes, fixture.choices.read("alice"))
        assertEquals(true, fixture.repository.state.value.joined)
        assertFalse(fixture.repository.state.value.shouldPrompt)
        assertEquals(earlyAccessSubject("alice"), fixture.repository.eligibleUpdateSubject())
        assertEquals(listOf("membership"), fixture.api.calls)
    }

    @Test
    fun `join and leave only save choices after server confirmation and failures retain state`() = runTest {
        val fixture = Fixture()
        fixture.repository.refresh().getOrThrow()
        fixture.repository.defer()
        fixture.api.onJoin = { throw IllegalStateException("offline") }
        assertTrue(fixture.repository.join().isFailure)
        assertEquals(EarlyAccessChoice.Deferred, fixture.choices.read("alice"))
        assertEquals(false, fixture.repository.state.value.joined)
        assertFalse(fixture.repository.state.value.shouldPrompt)

        fixture.api.onJoin = { EarlyAccessMembership(it, true) }
        fixture.repository.join().getOrThrow()
        assertEquals(EarlyAccessChoice.Yes, fixture.choices.read("alice"))
        assertEquals(true, fixture.repository.state.value.joined)
        fixture.api.onLeave = { throw IllegalStateException("offline") }
        assertTrue(fixture.repository.leave().isFailure)
        assertEquals(EarlyAccessChoice.Yes, fixture.choices.read("alice"))
        assertEquals(true, fixture.repository.state.value.joined)

        fixture.api.onLeave = { EarlyAccessMembership(it, false) }
        fixture.repository.leave().getOrThrow()
        assertEquals(EarlyAccessChoice.No, fixture.choices.read("alice"))
        assertEquals(false, fixture.repository.state.value.joined)
        assertNull(fixture.repository.eligibleUpdateSubject())
        assertFalse(fixture.repository.state.value.shouldPrompt)
    }

    @Test
    fun `late membership response cannot write or display another accounts eligibility`() = runTest {
        val fixture = Fixture()
        val response = CompletableDeferred<EarlyAccessMembership>()
        fixture.api.onJoin = { response.await() }
        val pending = async { fixture.repository.join() }
        runCurrent()
        fixture.identities.value = EarlyAccessIdentity("bob", 2)
        fixture.repository.synchronizeIdentity()
        assertEquals(earlyAccessSubject("bob"), fixture.repository.state.value.subject)
        assertEquals(false, fixture.repository.state.value.joined)
        response.complete(EarlyAccessMembership(earlyAccessSubject("alice"), true))
        assertTrue(pending.await().isFailure)
        assertEquals(EarlyAccessChoice.Unknown, fixture.choices.read("alice"))
        assertEquals(EarlyAccessChoice.Unknown, fixture.choices.read("bob"))
        assertNull(fixture.repository.eligibleUpdateSubject())
    }

    @Test
    fun `same account with a new identity generation rejects a late join`() = runTest {
        val fixture = Fixture()
        val response = CompletableDeferred<EarlyAccessMembership>()
        fixture.api.onJoin = { response.await() }
        val pending = async { fixture.repository.join() }
        runCurrent()
        fixture.identities.value = EarlyAccessIdentity("alice", 2)
        response.complete(EarlyAccessMembership(earlyAccessSubject("alice"), true))
        assertTrue(pending.await().isFailure)
        assertEquals(EarlyAccessChoice.Unknown, fixture.choices.read("alice"))
        assertEquals(false, fixture.repository.state.value.joined)
    }

    @Test
    fun `sign out clears cached membership and anonymous requests never use guest identity`() = runTest {
        val fixture = Fixture()
        fixture.repository.join().getOrThrow()
        fixture.identities.value = EarlyAccessIdentity(null, 2)
        fixture.repository.synchronizeIdentity()
        assertEquals(EarlyAccessState(), fixture.repository.state.value)
        assertNull(fixture.repository.eligibleUpdateSubject())
        fixture.repository.refresh().getOrThrow()
        assertTrue(fixture.repository.join().isFailure)
        assertEquals(listOf("join"), fixture.api.calls)
    }

    @Test
    fun `a cancelled join propagates cancellation without saving a choice`() = runTest {
        val fixture = Fixture()
        fixture.repository.refresh().getOrThrow()
        fixture.repository.defer()
        val response = CompletableDeferred<EarlyAccessMembership>()
        fixture.api.onJoin = { response.await() }
        val pending = async { fixture.repository.join() }
        runCurrent()
        assertTrue(fixture.repository.state.value.isLoading)
        pending.cancel()
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(EarlyAccessChoice.Deferred, fixture.choices.read("alice"))
        assertEquals(false, fixture.repository.state.value.joined)
        assertFalse(fixture.repository.state.value.isLoading)
        assertFalse(fixture.repository.state.value.shouldPrompt)
    }

    @Test
    fun `wrong subject missing joined and unconfirmed writes preserve membership`() = runTest {
        val fixture = Fixture()
        fixture.repository.join().getOrThrow()
        fixture.api.onMembership = { EarlyAccessMembership(earlyAccessSubject("bob"), false) }
        assertTrue(fixture.repository.refresh().isFailure)
        fixture.api.onMembership = { EarlyAccessMembership(it, null) }
        assertTrue(fixture.repository.refresh().isFailure)
        fixture.api.onLeave = { EarlyAccessMembership(it, true) }
        assertTrue(fixture.repository.leave().isFailure)
        assertEquals(true, fixture.repository.state.value.joined)
        assertEquals(EarlyAccessChoice.Yes, fixture.choices.read("alice"))
    }

    @Test
    fun `a saved yes cannot select preview until server confirms current membership`() = runTest {
        val fixture = Fixture()
        fixture.choices.write("alice", EarlyAccessChoice.Yes)
        fixture.repository.synchronizeIdentity()
        assertNull(fixture.repository.eligibleUpdateSubject())
        fixture.repository.refresh().getOrThrow()
        assertNull(fixture.repository.eligibleUpdateSubject())
        assertEquals(EarlyAccessChoice.Yes, fixture.repository.state.value.choice)
        assertEquals(false, fixture.repository.state.value.joined)
        assertFalse(fixture.repository.state.value.shouldPrompt)
    }

    @Test
    fun `stable update requests omit subject and joined account requests carry its hash`() = runTest {
        val fixture = Fixture()
        val subjects = mutableListOf<String?>()
        val check = EarlyAccessUpdateCheck(fixture.identities, fixture.repository::eligibleUpdateSubject) {
            subjects.add(it)
            ApkUpdateInfo(channel = if (it == null) "stable" else "preview")
        }
        assertEquals("stable", check.latest().channel)
        fixture.repository.join().getOrThrow()
        assertEquals("preview", check.latest().channel)
        fixture.repository.leave().getOrThrow()
        assertEquals("stable", check.latest().channel)
        assertEquals(listOf(null, earlyAccessSubject("alice"), null), subjects)
    }

    @Test
    fun `an update from a previous account generation is cancelled`() = runTest {
        val fixture = Fixture()
        fixture.repository.join().getOrThrow()
        val response = CompletableDeferred<ApkUpdateInfo>()
        val check = EarlyAccessUpdateCheck(fixture.identities, fixture.repository::eligibleUpdateSubject) {
            response.await()
        }
        val pending = async { runCatching { check.latest() } }
        runCurrent()
        fixture.identities.value = EarlyAccessIdentity("alice", 2)
        response.complete(ApkUpdateInfo(channel = "preview", versionCode = 999))
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun `leaving early access also cancels a late preview response for the same account`() = runTest {
        val fixture = Fixture()
        fixture.repository.join().getOrThrow()
        val response = CompletableDeferred<ApkUpdateInfo>()
        val check = EarlyAccessUpdateCheck(fixture.identities, fixture.repository::eligibleUpdateSubject) {
            response.await()
        }
        val pending = async { runCatching { check.latest() } }
        runCurrent()
        fixture.repository.leave().getOrThrow()
        response.complete(ApkUpdateInfo(channel = "preview", versionCode = 999))
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
    }

    @Test
    fun `membership responses without an optional subject still confirm the captured account`() = runTest {
        val fixture = Fixture()
        fixture.api.onMembership = { EarlyAccessMembership(joined = true) }
        fixture.api.onJoin = { EarlyAccessMembership(joined = true) }
        fixture.api.onLeave = { EarlyAccessMembership(joined = false) }
        fixture.repository.refresh().getOrThrow()
        assertEquals(false, fixture.repository.state.value.joined)
        fixture.repository.join().getOrThrow()
        assertEquals(EarlyAccessChoice.Yes, fixture.choices.read("alice"))
        fixture.repository.refresh().getOrThrow()
        assertEquals(true, fixture.repository.state.value.joined)
        fixture.repository.leave().getOrThrow()
        assertEquals(EarlyAccessChoice.No, fixture.choices.read("alice"))
        assertEquals(listOf("join", "membership", "leave"), fixture.api.calls)
    }

    @Test
    fun `an explicitly expired session cannot become an early access identity`() {
        val previous = AhuSessionState.status.value
        try {
            AhuSessionState.markExpired()
            assertNull(CurrentEarlyAccessIdentity.current().subject)
        } finally {
            when (previous) {
                AhuSessionState.Status.Anonymous -> AhuSessionState.markAnonymous()
                AhuSessionState.Status.Authenticated -> AhuSessionState.markAuthenticated()
                AhuSessionState.Status.Expired -> AhuSessionState.markExpired()
            }
        }
    }

    @Test
    fun `an action captured before dispatch cannot join the replacement account`() = runTest {
        val fixture = Fixture()
        val observed = fixture.identities.current()
        fixture.identities.value = EarlyAccessIdentity("bob", 2)
        assertTrue(fixture.repository.join(observed).isFailure)
        assertTrue(fixture.api.calls.isEmpty())
        assertEquals(earlyAccessSubject("bob"), fixture.repository.state.value.subject)
        assertEquals(EarlyAccessChoice.Unknown, fixture.choices.read("bob"))
    }

    @Test
    fun `unconsented choices never fetch membership even if another device joined`() = runTest {
        for (choice in listOf(EarlyAccessChoice.Unknown, EarlyAccessChoice.No, EarlyAccessChoice.Deferred)) {
            val fixture = Fixture()
            fixture.choices.write("alice", choice)
            fixture.api.onMembership = { throw AssertionError("Hash sent without local consent") }
            fixture.repository.refresh().getOrThrow()
            assertEquals(false, fixture.repository.state.value.joined)
            assertEquals(choice, fixture.repository.state.value.choice)
            assertTrue(fixture.api.calls.isEmpty())
            assertEquals(choice != EarlyAccessChoice.No, fixture.repository.state.value.shouldPrompt)
            assertNull(fixture.repository.eligibleUpdateSubject())
        }
    }

    @Test
    fun `a refresh queued behind successful leave cannot send a hash after consent is removed`() = runTest {
        val fixture = Fixture()
        fixture.repository.join().getOrThrow()
        val response = CompletableDeferred<EarlyAccessMembership>()
        fixture.api.onLeave = { response.await() }
        val leaving = async { fixture.repository.leave() }
        runCurrent()
        val refreshing = async { fixture.repository.refresh() }
        runCurrent()
        response.complete(EarlyAccessMembership(joined = false))
        leaving.await().getOrThrow()
        refreshing.await().getOrThrow()
        assertEquals(listOf("join", "leave"), fixture.api.calls)
        assertEquals(EarlyAccessChoice.No, fixture.repository.state.value.choice)
        assertEquals(false, fixture.repository.state.value.joined)
    }

    private class Fixture {
        val api = FakeApi()
        val identities = FakeIdentities()
        val choices = MemoryChoices()
        val repository = EarlyAccessRepository(api, identities, choices)
    }

    private class FakeIdentities : EarlyAccessIdentitySource {
        var value = EarlyAccessIdentity("alice", 1)
        override fun current() = value
    }

    private class MemoryChoices : EarlyAccessChoiceStore {
        private val values = mutableMapOf<String, EarlyAccessChoice>()
        override fun read(userId: String) = values[userId] ?: EarlyAccessChoice.Unknown
        override fun write(userId: String, choice: EarlyAccessChoice) {
            values[userId] = choice
        }
    }

    private class FakeApi : EarlyAccessApi {
        val calls = mutableListOf<String>()
        var onMembership: suspend (String) -> EarlyAccessMembership = { EarlyAccessMembership(it, false) }
        var onJoin: suspend (String) -> EarlyAccessMembership = { EarlyAccessMembership(it, true) }
        var onLeave: suspend (String) -> EarlyAccessMembership = { EarlyAccessMembership(it, false) }
        override suspend fun membership(subject: String): EarlyAccessMembership {
            calls.add("membership")
            return onMembership(subject)
        }
        override suspend fun join(subject: String): EarlyAccessMembership {
            calls.add("join")
            return onJoin(subject)
        }
        override suspend fun leave(subject: String): EarlyAccessMembership {
            calls.add("leave")
            return onLeave(subject)
        }
    }
}
