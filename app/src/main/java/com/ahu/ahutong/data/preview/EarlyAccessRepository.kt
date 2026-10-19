package com.ahu.ahutong.data.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Server membership is authoritative; persisted choices only control the invitation UI. */
class EarlyAccessRepository(
    private val api: EarlyAccessApi,
    private val identities: EarlyAccessIdentitySource,
    private val choices: EarlyAccessChoiceStore
) {
    private val stateLock = Any()
    private val requestMutex = Mutex()
    private var activeIdentity: EarlyAccessIdentity? = null
    private val deferredSubjects = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(EarlyAccessState())
    val state: StateFlow<EarlyAccessState> = mutableState.asStateFlow()

    /** Call when login/logout changes, so an old account's membership disappears immediately. */
    fun synchronizeIdentity(): EarlyAccessState = synchronized(stateLock) {
        synchronizeIdentityLocked()
    }

    suspend fun refresh(observed: EarlyAccessIdentity = identities.current()): Result<EarlyAccessState> {
        val localResult = synchronized(stateLock) {
            val current = synchronizeIdentityLocked()
            when {
                current.subject == null -> Result.success(current)
                activeIdentity != observed -> Result.failure(
                    IllegalStateException("Account changed before early access refresh")
                )
                current.choice != EarlyAccessChoice.Yes -> {
                    // Consent comes before any account hash leaves the device, including GET.
                    val next = if (current.isLoading) current else current.copy(
                        joined = false,
                        shouldPrompt = canPrompt(current.subject, current.choice)
                    )
                    mutableState.value = next
                    Result.success(next)
                }
                else -> null
            }
        }
        return localResult ?: request(observed, expectedJoined = null, remote = api::membership)
    }

    suspend fun join(observed: EarlyAccessIdentity = identities.current()): Result<EarlyAccessState> =
        request(observed, expectedJoined = true, remote = api::join)

    suspend fun leave(observed: EarlyAccessIdentity = identities.current()): Result<EarlyAccessState> =
        request(observed, expectedJoined = false, remote = api::leave)

    fun decline() = rememberChoice(EarlyAccessChoice.No)

    fun defer() = rememberChoice(EarlyAccessChoice.Deferred)

    /** A cached yes alone never unlocks a preview update before server confirmation. */
    fun eligibleUpdateSubject(): String? = synchronized(stateLock) {
        val current = synchronizeIdentityLocked()
        current.subject?.takeIf {
            current.choice == EarlyAccessChoice.Yes && current.joined == true
        }
    }

    private fun rememberChoice(choice: EarlyAccessChoice): Unit = synchronized(stateLock) {
        synchronizeIdentityLocked()
        val observed = activeIdentity ?: return@synchronized
        val userId = observed.userId ?: return@synchronized
        if (identities.current() != observed) {
            synchronizeIdentityLocked()
            return@synchronized
        }
        // The explicit captured account is used, even if login changes during a storage write.
        choices.write(userId, choice)
        observed.subject?.let { deferredSubjects.add(it) }
        if (identities.current() == observed) {
            mutableState.value = mutableState.value.copy(choice = choice, shouldPrompt = false)
        } else {
            synchronizeIdentityLocked()
            Unit
        }
    }

    private suspend fun request(
        observed: EarlyAccessIdentity,
        expectedJoined: Boolean?,
        remote: suspend (String) -> EarlyAccessMembership
    ): Result<EarlyAccessState> {
        synchronizeIdentity()
        val subject = observed.subject
            ?: return Result.failure(IllegalStateException("Early access requires a signed-in account"))
        return requestMutex.withLock {
            try {
                synchronized(stateLock) {
                    synchronizeIdentityLocked()
                    checkCurrentIdentity(observed)
                    if (expectedJoined == null && mutableState.value.choice != EarlyAccessChoice.Yes) {
                        // A queued refresh may have outlived a successful leave or local decline.
                        val next = mutableState.value.copy(
                            joined = false,
                            isLoading = false,
                            shouldPrompt = canPrompt(subject, mutableState.value.choice)
                        )
                        mutableState.value = next
                        return@withLock Result.success(next)
                    }
                    mutableState.value = mutableState.value.copy(isLoading = true)
                }
                val response = remote(subject)
                currentCoroutineContext().ensureActive()
                synchronized(stateLock) {
                    synchronizeIdentityLocked()
                    checkCurrentIdentity(observed)
                    check(response.subject == null || response.subject == subject) { "Early access response has a different subject" }
                    val joined = checkNotNull(response.joined) { "Early access response is missing joined" }
                    check(expectedJoined == null || joined == expectedJoined) {
                        "Early access membership change was not confirmed"
                    }
                    val choice = when {
                        joined -> EarlyAccessChoice.Yes
                        expectedJoined == false -> EarlyAccessChoice.No
                        else -> mutableState.value.choice
                    }
                    if (choice != mutableState.value.choice) {
                        choices.write(checkNotNull(observed.userId), choice)
                    }
                    // Recheck after synchronous persistence before publishing an account's state.
                    checkCurrentIdentity(observed)
                    if (joined || expectedJoined != null) deferredSubjects.add(subject)
                    val next = mutableState.value.copy(
                        choice = choice,
                        joined = joined,
                        isLoading = false,
                        shouldPrompt = !joined && expectedJoined == null && canPrompt(subject, choice)
                    )
                    mutableState.value = next
                    Result.success(next)
                }
            } catch (error: CancellationException) {
                finishRequest(observed)
                throw error
            } catch (error: Exception) {
                finishRequest(observed)
                Result.failure(error)
            }
        }
    }

    private fun finishRequest(observed: EarlyAccessIdentity) = synchronized(stateLock) {
        synchronizeIdentityLocked()
        if (activeIdentity == observed) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                shouldPrompt = mutableState.value.joined != true &&
                    observed.subject?.let { canPrompt(it, mutableState.value.choice) } == true
            )
        }
    }

    private fun checkCurrentIdentity(observed: EarlyAccessIdentity) {
        check(activeIdentity == observed && identities.current() == observed) {
            "Account changed during early access request"
        }
    }

    private fun canPrompt(subject: String, choice: EarlyAccessChoice): Boolean =
        (choice == EarlyAccessChoice.Unknown || choice == EarlyAccessChoice.Deferred) &&
            subject !in deferredSubjects

    private fun synchronizeIdentityLocked(): EarlyAccessState {
        val current = identities.current()
        if (activeIdentity != current) {
            activeIdentity = current
            val subject = current.subject
            mutableState.value = if (subject == null) {
                EarlyAccessState()
            } else {
                val choice = choices.read(checkNotNull(current.userId))
                EarlyAccessState(
                    subject = subject,
                    choice = choice,
                    joined = if (choice == EarlyAccessChoice.Yes) null else false,
                    shouldPrompt = canPrompt(subject, choice)
                )
            }
        }
        return mutableState.value
    }
}
