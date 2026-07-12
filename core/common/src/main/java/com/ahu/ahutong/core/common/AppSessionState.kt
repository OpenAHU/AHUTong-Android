package com.ahu.ahutong.core.common

/**
 * Process-wide session flags used by crawler HTTP auth and app UI.
 * Lives in core so network modules do not depend on [Application].
 */
object AppSessionState {
    @JvmField
    @Volatile
    var sessionExpired: Boolean = true

    @JvmField
    val reLoginMutex: Any = Any()
}
