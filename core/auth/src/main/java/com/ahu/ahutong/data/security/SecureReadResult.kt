package com.ahu.ahutong.data.security

import java.io.IOException

/** A failed encrypted read is distinct from an absent value and must not trigger migration. */
sealed interface SecureReadResult {
    data class Value(val value: String) : SecureReadResult
    data object Missing : SecureReadResult
    data class Unavailable(val cause: Exception, val retryable: Boolean = true) : SecureReadResult
}

/** Credential consumers may retry this failure without declaring the account rejected. */
class SecureStorageUnavailableException(cause: Exception) :
    IOException("Encrypted storage is temporarily unavailable", cause)

fun SecureReadResult.valueOrNull(): String? = (this as? SecureReadResult.Value)?.value
