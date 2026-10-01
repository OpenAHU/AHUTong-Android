package com.ahu.ahutong.data.mail

import com.ahu.ahutong.data.security.SecureBoxStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/** No generated toString: all fields are private account credentials. */
internal class StoredMailSession(val account: String, val sid: String, val email: String, val cookies: List<Cookie>)

internal object MailSessionCodec {
    private val mailbox = "https://mail.stu.ahu.edu.cn/".toHttpUrl()

    fun encode(session: StoredMailSession): String = JsonObject().apply {
        addProperty("version", 1)
        addProperty("account", session.account)
        addProperty("sid", session.sid)
        addProperty("email", session.email)
        // Cookie's canonical form preserves hostOnly, path, secure, HttpOnly and server expiry.
        add("cookies", JsonArray().apply {
            session.cookies.forEach { cookie -> add(JsonObject().apply {
                addProperty("domain", cookie.domain)
                addProperty("header", cookie.toString())
            }) }
        })
    }.toString()

    fun decode(value: String, account: String, now: Long = System.currentTimeMillis()): StoredMailSession? = try {
        val root = JsonParser.parseString(value).asJsonObject
        val sid = root["sid"].asString
        val email = root["email"].asString
        if (root["version"].asInt != 1 || account.isBlank() || root["account"].asString != account ||
            sid.isBlank() || !Regex("^[^\\s<>@,;]+@[^\\s<>@,;]+\\.[^\\s<>@,;]+$").matches(email)) null
        else {
            val cookies = root.getAsJsonArray("cookies").mapNotNull {
                val stored = it.asJsonObject
                Cookie.parse(mailbox, stored["header"].asString)?.takeIf { cookie -> cookie.domain == stored["domain"].asString }
            }
                .filter { it.expiresAt > now && (it.domain == mailbox.host ||
                    (!it.hostOnly && mailbox.host.endsWith(".${it.domain}"))) }
            if (cookies.isEmpty()) null else StoredMailSession(account, sid, email, cookies)
        }
    } catch (_: Exception) { null }
}

/** Existing AES-GCM/Android Keystore storage; never falls back to a plaintext mailbox record. */
internal object StudentMailSessionStore {
    private const val KEY = "student_mail_session_v1"
    private var lastSaved: String? = null

    fun read(account: String): StoredMailSession? {
        val encoded = SecureBoxStore.get(KEY) ?: return null
        return MailSessionCodec.decode(encoded, account).also { if (it == null) clear() }
    }

    fun write(session: StoredMailSession) {
        val encoded = MailSessionCodec.encode(session)
        if (encoded != lastSaved) {
            SecureBoxStore.put(KEY, encoded)
            lastSaved = encoded
        }
    }

    fun clear() {
        lastSaved = null
        SecureBoxStore.remove(KEY)
    }
}

/** Retry only read operations, once, and only on an explicit authentication failure. */
internal suspend fun <T> recoverMailRead(renew: suspend () -> Unit, read: suspend () -> T): T =
    try { read() } catch (_: MailSessionExpired) {
        renew()
        read()
    }
