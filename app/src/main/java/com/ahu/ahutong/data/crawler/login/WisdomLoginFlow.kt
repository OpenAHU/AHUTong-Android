package com.ahu.ahutong.data.crawler.login

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.model.adwnh.WisdomLoginResponse
import com.ahu.ahutong.data.model.User
import com.google.gson.Gson

/**
 * Authenticate Wisdom AHU before checking which academic portal accepts the account.
 * This stage only handles the captcha and ADWMH response.
 */
internal class WisdomLoginFlow(
    private val fetchCaptcha: suspend () -> ByteArray,
    private val solveCaptcha: suspend (ByteArray) -> String,
    private val submitLogin: suspend (String, String, String) -> String
) {
    suspend fun login(username: String, password: String): AhuResult<User> {
        val account = username.trim()
        if (account.isBlank() || password.isBlank()) {
            return failure(-1, "请将信息填写完整")
        }

        repeat(CAPTCHA_ATTEMPTS) { attempt ->
            val captcha = solveCaptcha(fetchCaptcha())
            val info = Gson().fromJson(
                submitLogin(account, password, captcha),
                WisdomLoginResponse::class.java
            ) ?: return failure(-1, "智慧安大登录响应为空，请稍后重试")

            if (info.code == SUCCESS_CODE) {
                val profile = info.payload?.user
                val name = profile?.userName?.takeIf { it.isNotBlank() }
                val studentId = profile?.idNumber?.takeIf { it.isNotBlank() }
                if (name == null || studentId == null) {
                    return failure(-1, "智慧安大登录响应缺少用户信息，请稍后重试")
                }
                return AhuResult.Success(User(name, studentId))
            }

            val message = info.msg?.takeIf { it.isNotBlank() } ?: "智慧安大登录失败"
            // Retry OCR errors only. Wrong credentials and other business errors
            // must retain the upstream explanation and must not trigger more logins.
            if (info.code == INVALID_CREDENTIALS_CODE ||
                !message.contains("验证码") || attempt == CAPTCHA_ATTEMPTS - 1
            ) {
                return failure(info.code ?: -1, message)
            }
        }
        error("Captcha attempts exhausted without a response")
    }

    private fun failure(upstreamCode: Int, message: String): AhuResult.Failure =
        if (upstreamCode == INVALID_CREDENTIALS_CODE) {
            AhuResult.Failure(AhuError.Unauthorized(message))
        } else {
            AhuResult.Failure(AhuError.Server(upstreamCode, message))
        }

    private companion object {
        const val SUCCESS_CODE = 10000
        const val INVALID_CREDENTIALS_CODE = 10012
        const val CAPTCHA_ATTEMPTS = 5
    }
}
