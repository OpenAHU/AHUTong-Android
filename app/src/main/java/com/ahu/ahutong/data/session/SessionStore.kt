package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureBoxStore
import com.ahu.ahutong.data.security.SecureStorageUnavailableException
import com.ahu.ahutong.ext.fromJson
import com.google.gson.Gson

/**
 * 会话域数据（身份 + 原生服务会话 Cookie）的唯一读写点。
 *
 * 为什么要单列一层：这些数据的丢失后果与"缓存"完全不同 —— 缓存丢了可以重新拉取，
 * 身份丢失等于登出、会话 Cookie 丢失等于原生服务立刻失效。因此它们不该混在业务缓存里。
 *
 * 键名与 JSON 格式沿用先前实现（`current_user` / `rust_cookies_json`），因此**无需数据迁移**。
 * 三档存储至此互不重叠：设置 → `PreferencesManager`（DataStore）、
 * 凭据 → `CredentialVault`（Keystore）、会话与缓存 → 本对象 / `AHUCache`。
 */
object SessionStore : SessionIdentity {

    private const val KEY_CURRENT_USER = "current_user"
    private const val KEY_RUST_COOKIES = "rust_cookies_json"
    private const val KEY_EVAL_TOKEN = "eval_token"

    private val userRestore = CurrentUserRestore(
        read = {
            SecureBoxStore.readOrMigrate(KEY_CURRENT_USER) {
                SecureBoxStore.legacyString(KEY_CURRENT_USER)
            }
        },
        decode = { it.fromJson(User::class.java) }
    )

    /** 只做读写与缓存；"用户变化后要刷新小组件槽位"这类副作用仍由缓存层负责。 */
    fun persistCurrentUser(user: User) {
        SecureBoxStore.put(KEY_CURRENT_USER, Gson().toJson(user))
        userRestore.update(user)
    }

    fun clearPersistedCurrentUser() {
        SecureBoxStore.remove(KEY_CURRENT_USER)
        userRestore.update(null)
    }

    override fun currentUser(): User? =
        (restoreCurrentUser() as? CurrentUserRestoreResult.Ready)?.user

    fun restoreCurrentUser(): CurrentUserRestoreResult = userRestore.restore()

    fun isUserRestoreUnavailable(): Boolean = userRestore.unavailable

    fun currentUserForRefresh(): User? = when (val restored = restoreCurrentUser()) {
        is CurrentUserRestoreResult.Ready -> restored.user
        is CurrentUserRestoreResult.Unavailable -> {
            if (restored.retryable) throw SecureStorageUnavailableException(restored.cause)
            null
        }
    }

    override fun isLoggedIn(): Boolean = currentUser() != null

    fun saveRustCookies(cookiesJson: String) {
        if (cookiesJson.isEmpty()) SecureBoxStore.remove(KEY_RUST_COOKIES)
        else SecureBoxStore.put(KEY_RUST_COOKIES, cookiesJson)
    }

    fun rustCookies(): String =
        SecureBoxStore.getOrMigrate(KEY_RUST_COOKIES) {
            SecureBoxStore.legacyString(KEY_RUST_COOKIES)
        }.orEmpty()

    /**
     * 评教服务令牌：属于"丢了就要重新登录"的一类数据，因此与身份同层，
     * 但按用户分箱存放（键名与箱名沿用先前实现，无需迁移）。
     */
    fun saveEvalToken(token: String) {
        val box = SecureBoxStore.userBox(currentUser()?.xh)
        if (token.isEmpty()) SecureBoxStore.remove(box, KEY_EVAL_TOKEN)
        else SecureBoxStore.put(box, KEY_EVAL_TOKEN, token)
    }

    fun evalToken(): String? {
        val box = SecureBoxStore.userBox(currentUser()?.xh)
        return SecureBoxStore.getOrMigrate(box, KEY_EVAL_TOKEN) {
            SecureBoxStore.legacyString(box, KEY_EVAL_TOKEN)
        }
    }
}
