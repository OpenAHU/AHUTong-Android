package com.ahu.ahutong.data.laundry

import android.util.Log
import com.ahu.ahutong.data.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * token 的本地保管处。
 *
 * 存 [SecureStorage]（Android Keystore AES-GCM 加密后落 SharedPreferences），不落明文、
 * 不进日志、不上传服务器：这份 JWT 等于用户微信账号在乐摇摇平台的登录态，
 * 只能本机使用（要全校共享得另做服务端池，属于后续方案）。
 */
@Singleton
class LaundryTokenStore @Inject constructor() {

    fun token(): String? = SecureStorage.getString(KEY)?.takeIf { it.isNotBlank() }

    fun save(token: String) {
        SecureStorage.putString(KEY, token)
    }

    fun clear() {
        runCatching { SecureStorage.remove(KEY) }
            .onFailure { Log.w("LaundryTokenStore", "清除 token 失败", it) }
    }

    private companion object {
        const val KEY = "laundry.leyaoyao.token.v1"
    }
}
