package com.ahu.ahutong.data.server

import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import java.net.URI

internal object ApkUpdatePolicy {
    const val MAX_APK_BYTES: Long = 200L * 1024L * 1024L
    const val MAX_DOWNLOAD_REDIRECTS: Int = 5
    const val MIRROR_DOWNLOAD_HOST: String = "openahu.muxyang.com"
    private const val ERROR_NO_UPDATE = "No APK update is available"
    private const val ERROR_NOT_NEWER = "Remote APK version is not newer"

    private val sha256Regex = Regex("^[0-9a-fA-F]{64}$")
    private val baseDownloadUri = URI("https://openahu.org/")
    private val primaryDownloadHosts = setOf("openahu.org", "www.openahu.org")

    data class ValidatedUpdate(
        val info: ApkUpdateInfo,
        val downloadUrl: String,
        val sha256: String
    )

    fun validate(info: ApkUpdateInfo, currentVersionCode: Int): Result<ValidatedUpdate> {
        if (info.update == false) {
            return Result.failure(IllegalArgumentException(ERROR_NO_UPDATE))
        }

        if (info.versionCode <= 0) {
            return Result.failure(IllegalArgumentException("Remote APK version is invalid"))
        }

        if (info.versionCode <= currentVersionCode) {
            return Result.failure(IllegalArgumentException(ERROR_NOT_NEWER))
        }

        val versionName = info.versionName?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: info.versionCode.toString()
        val changelog = info.changelog?.trim().orEmpty()

        val sha256 = normalizeSha256(info.sha256).getOrElse {
            return Result.failure(it)
        }

        val downloadUrl = mirrorDownloadUrl(info.url).getOrElse {
            return Result.failure(it)
        }

        return Result.success(
            ValidatedUpdate(
                info = info.copy(
                    versionName = versionName,
                    changelog = changelog,
                    url = downloadUrl,
                    sha256 = sha256
                ),
                downloadUrl = downloadUrl,
                sha256 = sha256
            )
        )
    }

    fun validateDownloadUrl(
        rawUrl: String?,
        baseUrl: String? = null,
        mirrorOnly: Boolean = false
    ): Result<String> {
        val url = rawUrl?.trim()
        if (url.isNullOrEmpty()) {
            return Result.failure(IllegalArgumentException("APK download URL is empty"))
        }

        val baseUri = if (baseUrl.isNullOrBlank()) {
            baseDownloadUri
        } else {
            try {
                URI(baseUrl)
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("APK download base URL is invalid", e))
            }
        }

        val parsedUri = try {
            URI(url)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("APK download URL is invalid", e))
        }
        val uri = if (parsedUri.isAbsolute) parsedUri else baseUri.resolve(parsedUri)

        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("APK download URL must use HTTPS"))
        }

        if (uri.userInfo != null) {
            return Result.failure(IllegalArgumentException("APK download URL must not contain user info"))
        }

        if (uri.fragment != null) {
            return Result.failure(IllegalArgumentException("APK download URL must not contain fragments"))
        }

        val trustedDownloadHosts = if (mirrorOnly) {
            setOf(MIRROR_DOWNLOAD_HOST)
        } else {
            primaryDownloadHosts + MIRROR_DOWNLOAD_HOST
        }
        val host = uri.host?.lowercase()
        if (host == null || host !in trustedDownloadHosts) {
            return Result.failure(IllegalArgumentException("APK download host is not trusted"))
        }

        val path = uri.rawPath.orEmpty()
        if (path.isBlank() || path == "/") {
            return Result.failure(IllegalArgumentException("APK download URL path is empty"))
        }

        return Result.success(uri.toASCIIString())
    }

    fun mirrorDownloadUrl(downloadUrl: String?): Result<String> {
        val trustedUrl = validateDownloadUrl(downloadUrl).getOrElse {
            return Result.failure(it)
        }
        val uri = URI(trustedUrl)
        val rawPath = uri.rawPath.orEmpty()
        val rawQuery = uri.rawQuery?.let { "?$it" }.orEmpty()
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val mirrorUrl = "https://$MIRROR_DOWNLOAD_HOST$port$rawPath$rawQuery"
        return validateDownloadUrl(mirrorUrl, mirrorOnly = true)
    }

    fun normalizeSha256(rawSha256: String?): Result<String> {
        val sha256 = rawSha256?.trim()
        if (sha256.isNullOrEmpty()) {
            return Result.failure(IllegalArgumentException("APK SHA-256 is required"))
        }

        if (!sha256Regex.matches(sha256)) {
            return Result.failure(IllegalArgumentException("APK SHA-256 must be 64 hex characters"))
        }

        return Result.success(sha256.lowercase())
    }

    fun isNoUpdateFailure(error: Throwable): Boolean {
        if (error !is IllegalArgumentException) return false
        return error.message == ERROR_NO_UPDATE || error.message == ERROR_NOT_NEWER
    }
}
