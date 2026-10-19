package com.ahu.ahutong.data.preview

import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator
import com.ahu.ahutong.data.security.SecureBoxStore
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.session.AhuSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

object CurrentEarlyAccessIdentity : EarlyAccessIdentitySource {
    override fun current(): EarlyAccessIdentity {
        // A cold-start persisted user is valid, but an explicitly signed-out user is not.
        repeat(3) {
            val generation = SessionRefreshCoordinator.currentIdentityGeneration()
            val unavailable = isUnavailable()
            val userId = if (unavailable) null
            else SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() }
            if (generation == SessionRefreshCoordinator.currentIdentityGeneration() &&
                unavailable == isUnavailable() &&
                (unavailable || SessionStore.currentUser()?.xh?.takeIf { it.isNotBlank() } == userId)) {
                return EarlyAccessIdentity(userId, generation)
            }
        }
        return EarlyAccessIdentity(null, SessionRefreshCoordinator.currentIdentityGeneration())
    }

    private fun isUnavailable(): Boolean = SessionRefreshCoordinator.isExplicitlySignedOut() ||
        AhuSessionState.status.value == AhuSessionState.Status.Expired
}

object EarlyAccessManager {
    private fun choiceKey(userId: String): String = "early_access_choice_${earlyAccessSubject(userId)}"
    private val repository = EarlyAccessRepository(
        api = object : EarlyAccessApi {
            override suspend fun membership(subject: String) = AhuTong.EARLY_ACCESS_API.getEarlyAccess(subject)
            override suspend fun join(subject: String) =
                AhuTong.EARLY_ACCESS_API.joinEarlyAccess(EarlyAccessRequest(subject))
            override suspend fun leave(subject: String) =
                AhuTong.EARLY_ACCESS_API.leaveEarlyAccess(EarlyAccessRequest(subject))
        },
        identities = CurrentEarlyAccessIdentity,
        choices = object : EarlyAccessChoiceStore {
            override fun read(userId: String) = EarlyAccessChoice.fromStorage(
                SecureBoxStore.get(choiceKey(userId))
            )
            override fun write(userId: String, choice: EarlyAccessChoice) {
                // Account choices must survive logout's clearing of the business user cache box.
                val key = choiceKey(userId)
                choice.storageValue?.let { SecureBoxStore.put(key, it) }
                    ?: SecureBoxStore.remove(key)
            }
        }
    )

    val state: StateFlow<EarlyAccessState> = repository.state

    fun synchronizeIdentity(): EarlyAccessState = repository.synchronizeIdentity()

    suspend fun refresh(): Result<EarlyAccessState> {
        val observed = CurrentEarlyAccessIdentity.current()
        return withContext(Dispatchers.IO) { repository.refresh(observed) }
    }

    suspend fun join(): Result<EarlyAccessState> {
        val observed = CurrentEarlyAccessIdentity.current()
        return withContext(Dispatchers.IO) { repository.join(observed) }
    }

    suspend fun leave(): Result<EarlyAccessState> {
        val observed = CurrentEarlyAccessIdentity.current()
        return withContext(Dispatchers.IO) { repository.leave(observed) }
    }

    fun decline() = repository.decline()

    fun defer() = repository.defer()

    fun eligibleUpdateSubject(): String? = repository.eligibleUpdateSubject()
}
