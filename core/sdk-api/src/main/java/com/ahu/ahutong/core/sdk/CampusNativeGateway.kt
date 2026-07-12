package com.ahu.ahutong.core.sdk

import android.content.Context
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.User

/**
 * Abstraction over native (Rust) campus capabilities and the local HTTP service.
 *
 * Implementations live in `:core:sdk`. Feature/data code should depend on this
 * API rather than [com.ahu.ahutong.sdk.RustSDK] / LocalServiceClient directly.
 */
interface CampusNativeGateway {
    // --- lifecycle ---

    fun isNativeLoaded(): Boolean

    fun loadLibrary(
        context: Context,
        onHotUpdateFound: (() -> Unit)? = null,
        onHotUpdateSuccess: (() -> Unit)? = null,
        onHotUpdateFinished: (() -> Unit)? = null,
    )

    fun startServer(port: Int = 0): String

    fun startServerWithStorage(port: Int, storagePath: String, seedCookies: String): String

    fun stopServer()

    // --- session / cookies ---

    fun init(cookiesJson: String)

    fun initSafe(cookiesJson: String)

    fun dumpCookies(): String?

    fun getCookiesList(): String

    suspend fun refreshToken(): AppResult<String>

    // --- campus ops (JNI path) ---

    suspend fun login(username: String, password: String): AppResult<User>

    suspend fun getSchedule(): AppResult<List<Course>>

    suspend fun getExamInfo(): AppResult<List<Exam>>

    /** Raw grade JSON; parsing stays in the data layer. */
    suspend fun getGradeRaw(): AppResult<String>

    suspend fun getBalance(): AppResult<Card>

    suspend fun getQrcode(): AppResult<String>

    // --- kv storage (Rust-backed) ---

    fun kvPutString(box: String, key: String, value: String): Boolean

    fun kvGetString(box: String, key: String): String?

    fun kvRemove(box: String, key: String): Boolean

    fun kvClearBox(box: String): Boolean

    // --- local HTTP client lifecycle ---

    fun bindLocalService(port: Int, token: String)

    fun unbindLocalService()

    fun isLocalServiceReady(): Boolean

    // --- local HTTP ops ---

    suspend fun httpLogin(username: String, password: String): AppResult<User>

    suspend fun httpGetSchedule(): AppResult<List<Course>>

    suspend fun httpGetExamInfo(): AppResult<List<Exam>>

    suspend fun httpGetGrade(studentId: String? = null): AppResult<String>

    suspend fun httpGetBalance(): AppResult<Card>

    suspend fun httpGetQrcode(): AppResult<String>

    suspend fun httpInit(cookiesJson: String): AppResult<Unit>

    suspend fun httpDumpCookies(): AppResult<String>

    suspend fun httpGetCookiesList(): AppResult<String>

    suspend fun httpRefreshToken(): AppResult<String>
}
