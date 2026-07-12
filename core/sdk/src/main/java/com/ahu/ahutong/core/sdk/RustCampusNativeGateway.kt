package com.ahu.ahutong.core.sdk

import android.content.Context
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.model.Card
import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.Exam
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.sdk.LocalServiceClient
import com.ahu.ahutong.sdk.RustSDK
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default [CampusNativeGateway] backed by [RustSDK] + [LocalServiceClient].
 */
@Singleton
class RustCampusNativeGateway @Inject constructor() : CampusNativeGateway {

    private val gson = Gson()

    override fun isNativeLoaded(): Boolean = RustSDK.isNativeLoaded()

    override fun loadLibrary(
        context: Context,
        onHotUpdateFound: (() -> Unit)?,
        onHotUpdateSuccess: (() -> Unit)?,
        onHotUpdateFinished: (() -> Unit)?,
    ) {
        RustSDK.loadLibrary(
            context = context,
            onHotUpdateFound = onHotUpdateFound,
            onHotUpdateSuccess = onHotUpdateSuccess,
            onHotUpdateFinished = onHotUpdateFinished,
        )
    }

    override fun startServer(port: Int): String = RustSDK.startServer(port)

    override fun startServerWithStorage(
        port: Int,
        storagePath: String,
        seedCookies: String,
    ): String = RustSDK.startServerWithStorage(port, storagePath, seedCookies)

    override fun stopServer() = RustSDK.stopServer()

    override fun init(cookiesJson: String) = RustSDK.init(cookiesJson)

    override fun initSafe(cookiesJson: String) = RustSDK.initSafe(cookiesJson)

    override fun dumpCookies(): String? = RustSDK.dumpCookies()

    override fun getCookiesList(): String = RustSDK.getCookiesListSafe()

    override suspend fun refreshToken(): AppResult<String> = withContext(Dispatchers.IO) {
        if (!RustSDK.isNativeLoaded()) {
            return@withContext AppResult.error("Native library not loaded")
        }
        runCatching { RustSDK.refreshToken() }
            .fold(
                onSuccess = { token ->
                    if (token.startsWith("ERROR")) {
                        AppResult.error(token)
                    } else {
                        AppResult.success(token)
                    }
                },
                onFailure = { AppResult.error(it.message ?: "refreshToken failed", it) },
            )
    }

    override suspend fun login(username: String, password: String): AppResult<User> =
        withContext(Dispatchers.IO) {
            AppResult.fromKotlin(RustSDK.loginSafe(username, password))
        }

    override suspend fun getSchedule(): AppResult<List<Course>> = withContext(Dispatchers.IO) {
        AppResult.fromKotlin(RustSDK.getScheduleSafe())
    }

    override suspend fun getExamInfo(): AppResult<List<Exam>> = withContext(Dispatchers.IO) {
        AppResult.fromKotlin(RustSDK.getExamInfoSafe())
    }

    override suspend fun getGradeRaw(): AppResult<String> = withContext(Dispatchers.IO) {
        if (!RustSDK.isNativeLoaded()) {
            return@withContext AppResult.error("Native library not loaded")
        }
        runCatching { RustSDK.getGrade() }
            .fold(
                onSuccess = { json ->
                    if (json.contains("\"error\"")) {
                        AppResult.error(json)
                    } else {
                        AppResult.success(json)
                    }
                },
                onFailure = { AppResult.error(it.message ?: "getGrade failed", it) },
            )
    }

    override suspend fun getBalance(): AppResult<Card> = withContext(Dispatchers.IO) {
        AppResult.fromKotlin(RustSDK.getBalanceSafe())
    }

    override suspend fun getQrcode(): AppResult<String> = withContext(Dispatchers.IO) {
        AppResult.fromKotlin(RustSDK.getQrcodeSafe())
    }

    override fun downloadSchoolCalendar(savePath: String): Boolean =
        RustSDK.downloadSchoolCalendar(savePath)

    override fun kvPutString(box: String, key: String, value: String): Boolean =
        RustSDK.kvPutStringSafe(box, key, value)

    override fun kvGetString(box: String, key: String): String? =
        RustSDK.kvGetStringSafe(box, key)

    override fun kvRemove(box: String, key: String): Boolean =
        RustSDK.kvRemoveSafe(box, key)

    override fun kvClearBox(box: String): Boolean =
        RustSDK.kvClearBoxSafe(box)

    override fun bindLocalService(port: Int, token: String) {
        LocalServiceClient.initialize(port, token)
    }

    override fun unbindLocalService() {
        LocalServiceClient.destroy()
    }

    override fun isLocalServiceReady(): Boolean =
        LocalServiceClient.getInstance() != null

    private fun clientOrError(): LocalServiceClient? = LocalServiceClient.getInstance()

    override suspend fun httpLogin(username: String, password: String): AppResult<User> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.login(username, password))
    }

    override suspend fun httpGetSchedule(): AppResult<List<Course>> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.getSchedule())
    }

    override suspend fun httpGetExamInfo(): AppResult<List<Exam>> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.getExamInfo())
    }

    override suspend fun httpGetGrade(studentId: String?): AppResult<String> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return client.getGrade(studentId).fold(
            onSuccess = { AppResult.success(gson.toJson(it)) },
            onFailure = { AppResult.error(it.message ?: "httpGetGrade failed", it) },
        )
    }

    override suspend fun httpGetBalance(): AppResult<Card> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.getBalance())
    }

    override suspend fun httpGetQrcode(): AppResult<String> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.getQrcode())
    }

    override suspend fun httpInit(cookiesJson: String): AppResult<Unit> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.init(cookiesJson))
    }

    override suspend fun httpDumpCookies(): AppResult<String> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.dumpCookies())
    }

    override suspend fun httpGetCookiesList(): AppResult<String> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.getCookiesList())
    }

    override suspend fun httpRefreshToken(): AppResult<String> {
        val client = clientOrError()
            ?: return AppResult.error("Local service not ready")
        return AppResult.fromKotlin(client.refreshToken())
    }
}
