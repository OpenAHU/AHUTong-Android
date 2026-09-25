package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.model.AcademicAccountType
import com.ahu.ahutong.data.model.User
import kotlinx.coroutines.CancellationException

internal enum class PortalLoginStatus { SUCCESS, REJECTED, VERIFICATION_REQUIRED, UNAVAILABLE }
internal data class PortalLoginResult(val status: PortalLoginStatus, val message: String = "")

internal class AcademicLoginFlow(
    private val wisdom: suspend (String, String) -> AHUResponse<User>,
    private val undergraduate: suspend (String, String, User) -> PortalLoginResult,
    private val postgraduate: suspend (String, String, User) -> PortalLoginResult
) {
    suspend fun login(username: String, password: String): AHUResponse<User> {
        val account = username.trim()
        val response = wisdom(account, password)
        if (!response.isSuccessful) return response
        val user = response.data ?: return failure("智慧安大未返回用户信息")
        val bachelor = attempt { undergraduate(account, password, user) }
        if (bachelor.status == PortalLoginStatus.SUCCESS) {
            user.academicAccountType = AcademicAccountType.UNDERGRADUATE
            return response
        }
        val graduate = attempt { postgraduate(account, password, user) }
        if (graduate.status == PortalLoginStatus.SUCCESS) {
            user.academicAccountType = AcademicAccountType.POSTGRADUATE
            return response
        }
        if (bachelor.status == PortalLoginStatus.VERIFICATION_REQUIRED) {
            return AHUResponse<User>().apply {
                code = 412
                data = user
                msg = "需要完成本科教务安全验证"
            }
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

    private fun failure(message: String) = AHUResponse<User>().apply {
        code = -1
        msg = message
    }
}
