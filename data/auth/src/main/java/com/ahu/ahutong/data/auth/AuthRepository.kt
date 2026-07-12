package com.ahu.ahutong.data.auth

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.User

/**
 * Authentication / session data API.
 */
interface AuthRepository {
    /**
     * Logs in via native HTTP → JNI → Android crawler fallback.
     */
    suspend fun login(username: String, password: String): AppResult<User>
}
