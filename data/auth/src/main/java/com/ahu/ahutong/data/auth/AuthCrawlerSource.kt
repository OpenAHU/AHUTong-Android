package com.ahu.ahutong.data.auth

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.User

/**
 * Legacy Android crawler login (adwmh + jwxt). Bound in `:app`.
 */
interface AuthCrawlerSource {
    suspend fun login(username: String, password: String): AppResult<User>
}
