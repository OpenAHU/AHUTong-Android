package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureReadResult

sealed interface CurrentUserRestoreResult {
    data class Ready(val user: User?) : CurrentUserRestoreResult
    data class Unavailable(val cause: Exception, val retryable: Boolean) : CurrentUserRestoreResult
}

/** Only a successful read, including a genuinely absent user, becomes the process cache. */
internal class CurrentUserRestore(
    private val read: () -> SecureReadResult,
    private val decode: (String) -> User?
) {
    private var initialized = false
    private var cachedUser: User? = null

    @Volatile
    var unavailable: Boolean = false
        private set

    @Synchronized
    fun restore(): CurrentUserRestoreResult {
        if (initialized) return CurrentUserRestoreResult.Ready(cachedUser)
        val stored = try {
            read()
        } catch (error: Exception) {
            SecureReadResult.Unavailable(error)
        }
        val restored = when (stored) {
            is SecureReadResult.Value -> {
                if (stored.value.isBlank()) {
                    CurrentUserRestoreResult.Ready(null)
                } else {
                    val user = runCatching { decode(stored.value) }.getOrNull()
                    if (user?.xh.isNullOrBlank()) {
                        CurrentUserRestoreResult.Unavailable(
                            IllegalArgumentException("Stored account data is invalid"), retryable = false
                        )
                    } else {
                        CurrentUserRestoreResult.Ready(user)
                    }
                }
            }
            SecureReadResult.Missing -> CurrentUserRestoreResult.Ready(null)
            is SecureReadResult.Unavailable -> CurrentUserRestoreResult.Unavailable(stored.cause, stored.retryable)
        }
        unavailable = restored is CurrentUserRestoreResult.Unavailable
        if (restored is CurrentUserRestoreResult.Ready) {
            cachedUser = restored.user
            initialized = true
        }
        return restored
    }

    @Synchronized
    fun update(user: User?) {
        cachedUser = user
        initialized = true
        unavailable = false
    }
}
