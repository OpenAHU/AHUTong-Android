package com.ahu.ahutong.assistant.model

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.network.retrofit
import com.ahu.ahutong.data.preview.CurrentEarlyAccessIdentity
import com.ahu.ahutong.data.preview.EarlyAccessIdentity
import com.ahu.ahutong.data.security.SecureBoxStore
import com.google.gson.annotations.SerializedName
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.io.IOException
import java.math.BigDecimal
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

internal object OfficialTrial {
    const val ORIGIN = "https://ahutong-aigateway.muxyang.com"
    const val PROFILE_ID = "official-student-trial"
    private const val KEY = "official_ai_trial_key_v1"
    private const val DERIVED_ID_PREFIX = "student-trial-v1-"
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val secretPattern = Regex("[A-Za-z0-9_-]{43}")

    val profile = AssistantModelProfile(
        id = PROFILE_ID,
        name = "官方试用模型",
        baseUrl = "$ORIGIN/v1",
        model = "ahutong-ai",
        providerLabel = "安大通试用",
        supportsThinking = false,
    )

    private val api by lazy {
        val client = AhuHttp.plain(
            callTimeoutSeconds = 25,
            readTimeoutSeconds = 20,
            retryOnConnectionFailure = false,
            followRedirects = false,
            captureDiagnostics = false,
        ).build()
        retrofit(ORIGIN, client).create(TrialApi::class.java)
    }

    fun currentIdentity(): EarlyAccessIdentity? =
        if (AHUCache.getMockData()) null else CurrentEarlyAccessIdentity.current().takeIf { it.userId != null }

    fun apiKey(): String? {
        val owner = runCatching { currentIdentity() }.getOrNull() ?: return null
        return runCatching { SecureBoxStore.get(SecureBoxStore.userBox(owner.userId), KEY) }
            .getOrNull()?.takeIf { it.startsWith("sk-ahu-") && owner == currentIdentity() }
    }

    suspend fun fetchPoints(owner: EarlyAccessIdentity): BigDecimal? = credentialBalance(owner)?.points

    suspend fun existingCredential(owner: EarlyAccessIdentity): CredentialResult? =
        credentialBalance(owner, allowInvalidKey = true)?.let { CredentialResult(it.points) }

    private suspend fun credentialBalance(owner: EarlyAccessIdentity, allowInvalidKey: Boolean = false): TrialBalance? {
        val key = withContext(Dispatchers.IO) {
            if (owner == currentIdentity()) apiKey() else null
        } ?: return null
        val reply = api.pointsBalance("Bearer $key")
        if (allowInvalidKey && reply.code() == 401) {
            reply.errorBody()?.close()
            return null
        }
        val balance = reply.checked()
        return balance.takeIf { owner == currentIdentity() }
    }

    fun redactStudentNumber(value: JsonElement, student: String): JsonElement = when {
        value.isJsonObject -> JsonObject().apply {
            value.asJsonObject.entrySet().forEach { (key, entry) ->
                add(key, redactStudentNumber(entry, student))
            }
        }
        value.isJsonArray -> JsonArray().apply {
            value.asJsonArray.forEach { add(redactStudentNumber(it, student)) }
        }
        value.isJsonPrimitive && value.asString.contains(student, ignoreCase = true) ->
            JsonPrimitive(value.asString.replace(student, "[学号已隐藏]", ignoreCase = true))
        else -> value
    }

    suspend fun resolveEntry(owner: EarlyAccessIdentity): TrialEntry {
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新开始验证。")
        val challenge = api.startEntry(ByteArray(0).toRequestBody()).checked()
        val student = owner.userId ?: throw TrialException("请先登录安大通。")
        if (!secretPattern.matches(challenge.browserTicket) || !secretPattern.matches(challenge.pollKey) ||
            challenge.expiresIn !in 1..300
        ) {
            throw TrialException("试用网关返回了无效的验证会话。")
        }
        val encrypted = withContext(Dispatchers.Default) {
            encryptStudentNumber(student, challenge.challenge, challenge.publicKey)
        }
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新开始验证。")
        val body = JSONObject().put("encrypted_student", encrypted).requestBody()
        val entry = api.resolveEntry("Bearer ${challenge.pollKey}", body).checked()
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新开始验证。")
        return when (entry.next) {
            "register" -> TrialEntry.Register(RegistrationSession(challenge.browserTicket, challenge.pollKey, challenge.expiresIn))
            "recover" -> TrialEntry.Recover
            "frozen" -> throw TrialException("该试用账号已受限，请联系安大通团队。")
            else -> throw TrialException("试用网关返回了无效的账号状态。")
        }
    }

    suspend fun pollRegistration(owner: EarlyAccessIdentity, session: RegistrationSession): CredentialResult? {
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新开始验证。")
        val reply = api.browserRegistrationResult("Bearer ${session.pollKey}")
        if (reply.code() == 204) return null
        return saveCredential(owner, reply.checked())
    }

    suspend fun sendRecoveryCode(owner: EarlyAccessIdentity): RecoverySession {
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新打开此页面。")
        val student = owner.userId ?: throw TrialException("请先登录安大通。")
        val reply = api.startRecovery(ByteArray(0).toRequestBody())
        if (reply.code() == 404) {
            reply.errorBody()?.close()
            throw TrialException("试用网关尚未开放邮箱恢复。")
        }
        val challenge = reply.checked()
        if (!secretPattern.matches(challenge.challenge) || challenge.challengeId.isBlank()) {
            throw TrialException("试用网关返回了无效的恢复挑战。")
        }
        val encrypted = withContext(Dispatchers.Default) {
            encryptStudentNumber(student, challenge.challenge, challenge.publicKey)
        }
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新打开此页面。")
        val session = api.sendRecoveryCode(
            JSONObject()
                .put("challenge_id", challenge.challengeId)
                .put("challenge", challenge.challenge)
                .put("encrypted_student", encrypted)
                .requestBody(),
        ).checked()
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新打开此页面。")
        if (!secretPattern.matches(session.token) || session.expiresIn !in 1..600 ||
            session.retryAfter !in 1..60
        ) {
            throw TrialException("试用网关返回了无效的恢复会话。")
        }
        return session
    }

    suspend fun recover(owner: EarlyAccessIdentity, session: RecoverySession, code: String): CredentialResult {
        require(code.length == 8 && code.all { it in '0'..'9' })
        if (owner != currentIdentity()) throw TrialException("账号已切换，请重新打开此页面。")
        val result = api.verifyRecoveryCode("Bearer ${session.token}", JSONObject().put("code", code).requestBody())
        return saveCredential(owner, result.checked())
    }

    private fun JSONObject.requestBody(): RequestBody = toString().toRequestBody("application/json".toMediaType())

    private suspend fun saveCredential(owner: EarlyAccessIdentity, result: RegistrationResult): CredentialResult {
        val userId = result.userId.orEmpty()
        val apiKey = result.apiKey.orEmpty()
        if (!userId.startsWith(DERIVED_ID_PREFIX) || !apiKey.startsWith("sk-ahu-")) {
            throw TrialException("试用网关返回了无效的凭据。")
        }
        val student = owner.userId ?: throw TrialException("请先登录安大通。")
        withContext(Dispatchers.IO + NonCancellable) {
            val box = SecureBoxStore.userBox(student)
            SecureBoxStore.put(box, KEY, apiKey)
            if (SecureBoxStore.get(box, KEY) != apiKey) {
                throw TrialException("无法保存试用凭据，请联系安大通团队。")
            }
        }
        if (owner != currentIdentity()) throw TrialException("账号已切换，请返回 AI 设置。")
        return CredentialResult(result.points)
    }

    internal fun encryptStudentNumber(student: String, challenge: String, publicKey: String): String {
        val challengeBytes = decoder.decode(challenge)
        val key = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(decoder.decode(publicKey)),
        ) as RSAPublicKey
        require(key.modulus.bitLength() >= 3072 && challengeBytes.size == 32)
        require(student.length in 1..64 && student.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' })
        val payload = ByteArray(48 + student.length)
        try {
            val nonce = ByteArray(16).also(random::nextBytes)
            nonce.copyInto(payload)
            challengeBytes.copyInto(payload, 16)
            student.forEachIndexed { index, char -> payload[48 + index] = char.code.toByte() }
            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                    PSource.PSpecified("AHUTong AI trial v1".toByteArray(Charsets.US_ASCII))),
                random,
            )
            return encoder.encodeToString(cipher.doFinal(payload))
        } finally {
            payload.fill(0)
        }
    }

    private fun <T : Any> Response<T>.checked(): T {
        if (isSuccessful) return body() ?: throw TrialException("试用网关响应无效。")
        val rawBody = errorBody()?.use { body ->
            runCatching {
                val source = body.source()
                source.request(2048L)
                source.readUtf8(minOf(2048L, source.buffer.size))
            }.getOrNull()
        }
        throw gatewayError(code(), headers()["X-Captcha-Verify-Code"], rawBody.orEmpty())
    }

    private fun gatewayError(status: Int, edgeStatus: String?, body: String): TrialException {
        val edgeCode = edgeStatus?.takeIf { it.matches(Regex("[FT][0-9]{3}")) }
        val code = runCatching { JSONObject(body).optJSONObject("error")?.optString("code") }.getOrNull()
        return TrialException(when (code) {
            "CAPTCHA_FAILED" -> when (edgeCode) {
                "T001" -> "验证码已通过，试用服务器未接受验签结果。"
                null -> "试用服务器未收到有效验签结果，请重新验证。"
                else -> "验证码未通过（$edgeCode），请重新验证。"
            }
            "TRIAL_CHALLENGE_INVALID" -> "注册挑战已过期或无效，请重新验证。"
            "TRIAL_BROWSER_EXPIRED" -> "验证码会话已过期，请重新验证。"
            "TRIAL_CAPTCHA_EDGE_MISSING" -> when {
                edgeCode == "T001" -> "ESA 已返回 T001，但网关未收到回源验签结果。"
                edgeCode != null -> "验证码未通过（$edgeCode），请重新验证。"
                else -> "ESA 未返回验签状态，请检查验证码规则是否已生效。"
            }
            "TRIAL_CAPTCHA_EDGE_REJECTED" -> "ESA 拒绝了验签${edgeCode?.let { "（$it）" }.orEmpty()}，请重新验证。"
            "TRIAL_CAPTCHA_ORIGIN_UNVERIFIED" -> "网关收到验签通过状态，但无法确认回源身份。"
            "TRIAL_ALREADY_REGISTERED" -> "该试用账号已开通，请使用邮箱恢复。"
            "TRIAL_RECOVERY_UNAVAILABLE" -> "邮箱恢复暂不可用，请稍后再试。"
            "TRIAL_RECOVERY_CHALLENGE_INVALID" -> "恢复请求已过期，请重新发送验证码。"
            "TRIAL_RECOVERY_MAIL_UNAVAILABLE" -> "暂时无法发送恢复邮件，请稍后再试。"
            "TRIAL_RECOVERY_RATE_LIMIT" -> "请求过于频繁，请稍后再试。每个账号每天最多发送 5 次。"
            "TRIAL_RECOVERY_CODE_INVALID" -> "验证码错误或已失效，请检查邮件或重新发送。"
            "USER_FROZEN", "account_frozen" -> "该试用账号已受限，请联系安大通团队。"
            "IP_NOT_IN_HEFEI", "ip_not_in_hefei" -> "请使用合肥本地网络或安徽大学校园网。"
            "TRIAL_UNAVAILABLE" -> "官方试用暂未开放，请稍后再试。"
            else -> when (status) {
                401 -> "试用凭据已失效，请联系安大通团队。"
                403 -> if (edgeCode != null) "验证码未通过（$edgeCode），请重新验证。"
                    else "注册请求未通过验证，请重新验证后重试。"
                429 -> "验证请求过于频繁，请稍后重试。"
                else -> "试用网关请求失败（HTTP $status），请稍后重试。"
            }
        }, code)
    }

    private interface TrialApi {
        @GET("/v1/credits/balance")
        suspend fun pointsBalance(@Header("Authorization") authorization: String): Response<TrialBalance>

        @POST("/v1/client/trial/entry/start")
        suspend fun startEntry(@Body body: RequestBody): Response<RegistrationChallenge>

        @POST("/v1/client/trial/entry/resolve")
        suspend fun resolveEntry(
            @Header("Authorization") authorization: String,
            @Body body: RequestBody,
        ): Response<EntryResponse>

        @GET("/v1/client/trial/browser/status")
        suspend fun browserRegistrationResult(
            @Header("Authorization") authorization: String,
        ): Response<RegistrationResult>

        @POST("/v1/client/trial/recovery/challenge")
        suspend fun startRecovery(@Body body: RequestBody): Response<RecoveryChallenge>

        @POST("/v1/client/trial/recovery/send")
        suspend fun sendRecoveryCode(@Body body: RequestBody): Response<RecoverySession>

        @POST("/v1/client/trial/recovery/verify")
        suspend fun verifyRecoveryCode(
            @Header("Authorization") authorization: String,
            @Body body: RequestBody,
        ): Response<RegistrationResult>
    }

    private class RegistrationChallenge(
        @SerializedName("browser_ticket") val browserTicket: String,
        @SerializedName("poll_key") val pollKey: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("challenge") val challenge: String,
        @SerializedName("public_key") val publicKey: String,
    )
    private class RegistrationResult(
        @SerializedName("user_id") val userId: String?,
        @SerializedName("api_key") val apiKey: String?,
        @SerializedName("points_balance_display") val points: BigDecimal?,
    )

    private class EntryResponse(@SerializedName("next") val next: String)

    private class RecoveryChallenge(
        @SerializedName("challenge_id") val challengeId: String,
        @SerializedName("challenge") val challenge: String,
        @SerializedName("public_key") val publicKey: String,
    )

    class RecoverySession(
        @SerializedName("recovery_token") val token: String,
        @SerializedName("expires_in") val expiresIn: Int,
        @SerializedName("retry_after") val retryAfter: Int,
    )

    private class TrialBalance(@SerializedName("points_balance_display") val points: BigDecimal?)

    sealed interface TrialEntry {
        class Register(val session: RegistrationSession) : TrialEntry
        data object Recover : TrialEntry
    }

    class RegistrationSession(val ticket: String, val pollKey: String, val expiresIn: Int)

    class CredentialResult(val points: BigDecimal?)
}

internal class TrialException(message: String, val gatewayCode: String? = null) : IOException(message)
