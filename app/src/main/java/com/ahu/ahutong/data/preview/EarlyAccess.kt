package com.ahu.ahutong.data.preview

import androidx.annotation.Keep
import java.security.MessageDigest

/** Only an account can join early access; a device or guest is never a subject. */
fun earlyAccessSubject(userId: String?): String? = userId?.takeIf { it.isNotBlank() }?.let {
    MessageDigest.getInstance("SHA-256")
        .digest("user:$it".toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

enum class EarlyAccessChoice(val storageValue: String?) {
    Unknown(null),
    Yes("yes"),
    No("no"),
    Deferred("deferred");

    companion object {
        fun fromStorage(value: String?): EarlyAccessChoice =
            values().firstOrNull { it.storageValue == value } ?: Unknown
    }
}

data class EarlyAccessState(
    val subject: String? = null,
    val choice: EarlyAccessChoice = EarlyAccessChoice.Unknown,
    val joined: Boolean? = null,
    val isLoading: Boolean = false,
    val shouldPrompt: Boolean = false
)

data class EarlyAccessIdentity(val userId: String?, val generation: Long) {
    val subject: String? = earlyAccessSubject(userId)
}

fun interface EarlyAccessIdentitySource {
    fun current(): EarlyAccessIdentity
}

interface EarlyAccessChoiceStore {
    fun read(userId: String): EarlyAccessChoice
    fun write(userId: String, choice: EarlyAccessChoice)
}

@Keep
data class EarlyAccessRequest(val subject: String)

@Keep
data class EarlyAccessMembership(
    val subject: String? = null,
    val joined: Boolean? = null
)

interface EarlyAccessApi {
    suspend fun membership(subject: String): EarlyAccessMembership
    suspend fun join(subject: String): EarlyAccessMembership
    suspend fun leave(subject: String): EarlyAccessMembership
}
