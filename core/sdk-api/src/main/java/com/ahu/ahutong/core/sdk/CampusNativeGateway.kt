package com.ahu.ahutong.core.sdk

import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.User

/**
 * Abstraction over the native (Rust) campus capabilities.
 * Implementations live in a future `:core:sdk` module; feature/data code
 * must depend only on this API.
 *
 * Methods are intentionally minimal in Phase 1 and will expand as call sites migrate.
 */
interface CampusNativeGateway {
    fun isNativeLoaded(): Boolean

    suspend fun login(username: String, password: String): AppResult<User>

    suspend fun getSchedule(): AppResult<List<Course>>

    suspend fun getExamInfo(): AppResult<String>

    suspend fun getGrade(): AppResult<String>

    fun dumpCookies(): String?

    fun init(cookiesJson: String)

    /**
     * Starts the local HTTP service. Returns a JSON payload with port/token
     * or an error description (legacy contract preserved for gradual migration).
     */
    fun startServer(port: Int = 0): String

    fun startServerWithStorage(port: Int, storagePath: String, seedCookies: String): String

    fun stopServer()
}
