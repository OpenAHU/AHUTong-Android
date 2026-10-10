package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.model.User
import com.ahu.ahutong.data.security.SecureStorage
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 界面只持有姓名与学号，密码仅在发起登录时读取。 */
data class SavedAccount(val userId: String, val name: String)

/** 独立于 init / 用户缓存分箱，普通登录清缓存不会删除其它账号。 */
internal class SavedAccountStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val remove: () -> Unit
) {
    @Keep
    private class Entry(val userId: String, val name: String, val password: String)
    private val gson = Gson()

    @Synchronized
    fun accounts(): List<SavedAccount> = entries().map { SavedAccount(it.userId, it.name.orEmpty()) }

    @Synchronized
    fun password(userId: String): String? = entries().firstOrNull { it.userId == userId }?.password

    @Synchronized
    fun save(user: User, password: String) {
        val userId = requireNotNull(user.xh).trim()
        require(userId.isNotEmpty() && password.isNotBlank())
        val updated = listOf(Entry(userId, user.name.orEmpty(), password)) +
            entries().filterNot { it.userId == userId }
        write(gson.toJson(updated))
    }

    @Synchronized
    fun forget(userId: String) {
        val remaining = entries().filterNot { it.userId == userId }
        if (remaining.isEmpty()) remove() else write(gson.toJson(remaining))
    }

    @Synchronized
    fun clear() = remove()

    private fun entries(): List<Entry> {
        val json = read() ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson<List<Entry>>(json, type).orEmpty().filter {
                !it.userId.isNullOrBlank() && !it.password.isNullOrBlank()
            }.distinctBy { it.userId }
        }.getOrDefault(emptyList())
    }
}

object SavedAccounts {
    private const val KEY = "accounts.saved"
    private val store = SavedAccountStore(
        read = { SecureStorage.getString(KEY) },
        write = { SecureStorage.putString(KEY, it) },
        remove = { SecureStorage.remove(KEY) }
    )

    fun accounts(): List<SavedAccount> = store.accounts()
    fun password(userId: String): String? = store.password(userId)
    fun save(user: User, password: String) = store.save(user, password)
    fun forget(userId: String) = store.forget(userId)
    fun clear() = store.clear()

    /** 升级后把原有单账号迁入列表，必须在清理旧凭据之前执行。 */
    fun rememberCurrent() {
        val user = SessionStore.currentUser() ?: return
        val password = SecureCredentialVault.wisdomPassword()?.takeIf { it.isNotBlank() } ?: return
        save(user, password)
    }
}
