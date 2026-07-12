package com.ahu.ahutong.data.auth

import android.util.Log
import com.ahu.ahutong.core.common.AppResult
import com.ahu.ahutong.data.crawler.api.adwmh.AdwmhApi
import com.ahu.ahutong.data.crawler.api.jwxt.JwxtApi
import com.ahu.ahutong.data.crawler.configs.Constants
import com.ahu.ahutong.data.crawler.model.adwnh.Info
import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.utils.DES
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

/**
 * Legacy adwmh + jwxt crawler login path extracted from AHURepository.
 */
@Singleton
class CrawlerAuthSource @Inject constructor() : AuthCrawlerSource {

    override suspend fun login(username: String, password: String): AppResult<User> = coroutineScope {
        val adwmhLogin = async(Dispatchers.IO) {
            var failedTimes = 0
            var info: Info? = null
            while (failedTimes < 5) {
                Log.e(TAG, "crawler login attempt ${failedTimes + 1}")
                val captchaBytes = AdwmhApi.API.getAuthCode().bytes()
                val captchaPart = MultipartBody.Part.createFormData(
                    "captcha",
                    "img.jpg",
                    captchaBytes.toRequestBody("image/jpg".toMediaType()),
                )
                val captcha = AhuTong.API.getCaptchaResult(captchaPart).result
                info = AdwmhApi.API.loginWithCaptcha(username, password, 0, captcha)
                if (info.code == 10000) {
                    return@async info
                }
                failedTimes++
            }
            info
        }

        val jwxtLogin = async(Dispatchers.IO) {
            val loginPage = JwxtApi.API.fetchLoginInfo()
            val document = Jsoup.parse(loginPage.body()!!.string())
            val lt = document.selectFirst("input[name=lt]")?.attr("value")

            lt?.let {
                val cipher = DES().strEnc(username + password + lt, "1", "2", "3")
                JwxtApi.API.device(
                    "https://one.ahu.edu.cn/cas/device",
                    username.length,
                    password.length,
                    cipher,
                )
                val jwxtLoginUrl = "https://one.ahu.edu.cn/cas/login" +
                    "?service=https%3A%2F%2Fjw.ahu.edu.cn%2Fstudent%2Fsso%2Flogin"
                val jwxtResponse = JwxtApi.API.login(
                    jwxtLoginUrl,
                    cipher,
                    username.length,
                    password.length,
                    lt,
                )
                if (jwxtResponse.raw().request.url.toString().endsWith(Constants.JWXT_HOME)) {
                    return@async true
                }
            } ?: run {
                if (loginPage.raw().request.url.toString().endsWith(Constants.JWXT_HOME)) {
                    return@async true
                }
                return@async false
            }
            false
        }

        val crawlerResult = adwmhLogin.await()
        val jwxtLoginSuccess = jwxtLogin.await()

        crawlerResult?.let {
            if (it.code == 10000 && jwxtLoginSuccess) {
                val user = User(it.`object`.user.userName, it.`object`.user.idNumber)
                return@coroutineScope AppResult.success(user)
            }
        }
        AppResult.error("登录失败")
    }

    private companion object {
        const val TAG = "CrawlerAuthSource"
    }
}
