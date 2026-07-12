package com.ahu.ahutong.core.network

/**
 * Shared networking defaults for data modules.
 * Concrete OkHttp/Retrofit factories will land here as crawler code migrates out of `:app`.
 */
object NetworkDefaults {
    const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 15L
    const val DEFAULT_READ_TIMEOUT_SECONDS = 30L
    const val DEFAULT_WRITE_TIMEOUT_SECONDS = 30L
}
