package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.model.User
import kotlinx.coroutines.CancellationException

internal enum class PortalLoginStatus { SUCCESS, REJECTED, VERIFICATION_REQUIRED, UNAVAILABLE }
internal data class PortalLoginResult(
    val status: PortalLoginStatus,
    val message: String = "",
    val sessionPageUrl: String? = null
)

internal class AcademicLoginFlow(
    private val wisdom: suspend (String, String) -> AhuResult<User>,
    private val undergraduate: suspend (String, String, User) -> PortalLoginResult,
    private val postgraduate: suspend (String, String, User) -> PortalLoginResult
) {
    suspend fun login(username: String, password: String): AhuResult<LoginOutcome> {
        val account = username.trim()
        val response = wisdom(account, password)
        val user = when (response) {
            is AhuResult.Success -> response.value
            is AhuResult.Failure -> return response
        }
        val bachelor = attempt { undergraduate(account, password, user) }
        if (bachelor.status == PortalLoginStatus.SUCCESS) {
            user.academicAccountType = AcademicAccountType.UNDERGRADUATE
            return AhuResult.Success(LoginOutcome.Success(user))
        }
        val graduate = attempt { postgraduate(account, password, user) }
        if (graduate.status == PortalLoginStatus.SUCCESS) {
            user.academicAccountType = AcademicAccountType.POSTGRADUATE
            return AhuResult.Success(LoginOutcome.Success(user))
        }
        if (bachelor.status == PortalLoginStatus.VERIFICATION_REQUIRED) {
            return AhuResult.Success(LoginOutcome.JwxtWebVerificationRequired(user))
        }
        return failure(
            "智慧安大认证成功，但教务身份尚未确认。研究生教务：" +
                graduate.message.ifBlank { "登录未成功，请稍后重试" }
        )
    }

    private suspend fun attempt(block: suspend () -> PortalLoginResult): PortalLoginResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: java.io.IOException) {
        PortalLoginResult(PortalLoginStatus.UNAVAILABLE, "网络连接失败，请稍后重试")
    }

    private fun failure(message: String): AhuResult.Failure =
        AhuResult.Failure(AhuError.ProtocolChanged(message))
}
