package com.ahu.ahutong.error

import java.io.PrintWriter
import java.io.Writer
import java.net.URI

internal data class UnexpectedErrorReport(val id: Long, val details: String, val sentryEventId: String? = null)

/** Reports are sanitized before entering the queue, including exception messages and nested traces. */
internal object UnexpectedErrorReportFormatter {
    private const val MAX_TRACE_LENGTH = 48_000
    private val url = Regex("(?i)\\b(?:https?|wss?)://[^\\s<>\"']+")
    private val privateHeader = Regex("(?im)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]*")
    private val privateArray = Regex("(?i)([\"']?(?:cookies?|set-cookie|authorization)[\"']?\\s*[:=]\\s*)\\[[^\\r\\n]*?\\]")
    private val privateValue = Regex(
        "(?i)([\"']?(?:password|passwd|pwd|secret|(?:access[_-]?|refresh[_-]?|auth[_-]?)?token|sid|session(?:[_-]?id)?|jsessionid|ticket|authorization|cookies?|set-cookie|account|user(?:name|id|_id)?|loginname|xh|学号|账号|密码)[\"']?\\s*[:=]\\s*)(?:\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\\s,;&}\\]\\r\\n]+)"
    )
    private val bearer = Regex("(?i)\\bBearer\\s+[^\\s,;\"']+")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val studentId = Regex("\\b[A-Za-z]?\\d{8,14}\\b")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)?")

    fun format(error: Throwable, environment: String): String {
        val trace = BoundedTraceWriter(MAX_TRACE_LENGTH)
        PrintWriter(trace).use { error.printStackTrace(it) }
        return sanitize("$environment\n\n错误信息与追踪：\n${trace.text()}")
    }

    internal fun sanitize(text: String): String {
        var safe = privateHeader.replace(text) { "${it.groupValues[1]}: [已隐藏]" }
        safe = url.replace(safe) { match ->
            runCatching {
                val uri = URI(match.value)
                val host = uri.host ?: return@runCatching "[地址已隐藏]"
                "${uri.scheme}://$host${if (uri.port >= 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}" +
                    if (uri.rawQuery != null || uri.rawFragment != null) "?[已隐藏]" else ""
            }.getOrDefault("[地址已隐藏]")
        }
        safe = privateArray.replace(safe) { "${it.groupValues[1]}[已隐藏]" }
        safe = privateValue.replace(safe) { "${it.groupValues[1]}[已隐藏]" }
        safe = bearer.replace(safe, "Bearer [已隐藏]")
        safe = jwt.replace(safe, "[凭据已隐藏]")
        safe = email.replace(safe, "[邮箱已隐藏]")
        return studentId.replace(safe, "[账号已隐藏]")
    }

    private class BoundedTraceWriter(private val limit: Int) : Writer() {
        private val buffer = StringBuilder()
        private var truncated = false
        override fun write(chars: CharArray, offset: Int, length: Int) {
            val remaining = (limit - buffer.length).coerceAtLeast(0)
            buffer.append(chars, offset, minOf(length, remaining))
            if (length > remaining) truncated = true
        }
        override fun flush() = Unit
        override fun close() = Unit
        fun text(): String = buffer.toString() + if (truncated) "\n[追踪过长，已截断]" else ""
    }
}
