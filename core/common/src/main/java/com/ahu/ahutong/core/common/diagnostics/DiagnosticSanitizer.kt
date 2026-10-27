package com.ahu.ahutong.core.common.diagnostics

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Two deliberate policies: ordinary records hide identity; detailed records only hide passwords. */
object DiagnosticSanitizer {
    private const val HIDDEN = "[已隐藏]"
    private val passwordKeys = setOf(
        "password", "passwd", "pwd", "pass", "userpassword", "userpwd", "loginpassword",
        "loginpwd", "oldpassword", "newpassword", "confirmpassword", "repeatpassword",
        "paypassword", "paymentpassword", "tradepassword", "encryptedpassword", "encryptpassword",
        "passwordencrypt", "passwordencrypted", "passwordcipher", "cipherpassword", "despassword",
        // The campus CAS protocol sends username + password + lt encrypted in rsa; pl is its length.
        "rsa", "pl", "密码", "口令"
    )
    private val identityKeys = setOf(
        "authorization", "proxyauthorization", "cookie", "cookies", "setcookie", "synjonesauth",
        "token", "accesstoken", "refreshtoken", "authtoken", "idtoken", "secret", "clientsecret",
        "sid", "session", "sessionid", "jsessionid", "ticket", "account", "username", "userid",
        "loginname", "xh", "studentid", "email", "phone", "mobile", "name", "lt", "execution",
        "学号", "账号", "姓名"
    )
    private val jsonKey = Regex("""(["'])(?:\\.|[^"'\\])*\1\s*:\s*""")
    private val unquotedAssignment = Regex(
        """(?im)(\b[\p{L}\p{N}_%-]+)(\s*[:=]\s*)("(?:\\.|[^"\\])*(?:"|$)|'(?:\\.|[^'\\])*(?:'|$)|[^\s,;&}\]\r\n]+)"""
    )
    private val formAssignment = Regex("""(^|[?#&;\s])([^=&#;?\s"'{}:/]+)=("(?:\\.|[^"\\])*(?:"|$)|'(?:\\.|[^'\\])*(?:'|$)|[^&#;\s]*)""")
    private val unicodeEscape = Regex("""\\u([0-9a-fA-F]{4})""")
    private val url = Regex("""(?i)\b(?:https?|wss?)://[^\s<>"']+""")
    private val urlPassword = Regex("""(?i)(https?://[^/\s:@]+:)[^@\s/]+@""")
    private val basicPassword = Regex("""(?i)\bBasic\s+[A-Za-z0-9+/=_-]+""")
    private val privateHeader = Regex("""(?im)\b(authorization|proxy-authorization|synjones-auth|cookie|set-cookie)\s*[:=]\s*[^\r\n]*""")
    private val privateArray = Regex("""(?i)(["']?(?:cookies?|set-cookie|authorization)["']?\s*[:=]\s*)\[[^\r\n]*?\]""")
    private val bearer = Regex("""(?i)\bBearer\s+[^\s,;"']+""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val studentId = Regex("""\b[A-Za-z]?\d{8,14}\b""")
    private val jwt = Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+)?""")
    private val xmlValue = Regex("""(?is)<([\p{L}\p{N}_-]+)(?:\s[^>]*)?>([^<]*)</\1>""")
    private val htmlInput = Regex("""(?is)<input\b[^>]*>""")
    private val htmlAttribute = Regex("""(?i)([\w:-]+)(\s*=\s*)("[^"]*"|'[^']*'|[^\s>]+)""")
    private val knownPasswords = linkedMapOf<String, Long>()

    /** Secrets are used only for redaction, never exposed in snapshots or persisted. */
    @Synchronized fun rememberPassword(value: String) {
        if (value.isEmpty() || value.length > 1024) return
        prunePasswords()
        knownPasswords.remove(value)
        knownPasswords[value] = System.nanoTime() / 1_000_000
        while (knownPasswords.size > 8) knownPasswords.remove(knownPasswords.keys.first())
    }

    @Synchronized internal fun clearKnownPasswords() = knownPasswords.clear()

    fun passwords(text: String): String = sanitize(text, includeIdentity = false)

    fun safe(text: String): String {
        var result = sanitize(text, includeIdentity = true)
        result = privateHeader.replace(result) { "${it.groupValues[1]}: $HIDDEN" }
        result = privateArray.replace(result) { "${it.groupValues[1]}$HIDDEN" }
        result = url.replace(result) { match ->
            runCatching {
                val uri = URI(match.value)
                val host = uri.host ?: return@runCatching "[地址已隐藏]"
                "${uri.scheme}://$host${if (uri.port >= 0) ":${uri.port}" else ""}${uri.rawPath.orEmpty()}" +
                    if (uri.rawQuery != null || uri.rawFragment != null) "?[已隐藏]" else ""
            }.getOrDefault("[地址已隐藏]")
        }
        result = bearer.replace(result, "Bearer $HIDDEN")
        result = jwt.replace(result, "[凭据已隐藏]")
        result = email.replace(result, "[邮箱已隐藏]")
        return studentId.replace(result, "[账号已隐藏]")
    }

    private fun sanitize(text: String, includeIdentity: Boolean): String {
        fun hide(key: String): Boolean {
            val normalized = normalizeKey(key)
            return normalized in passwordKeys || normalized.contains("password") || normalized.contains("passwd") ||
                normalized.startsWith("pwd") || normalized.endsWith("pwd") || includeIdentity && normalized in identityKeys
        }
        var result = hideJsonValues(text, ::hide)
        result = formAssignment.replace(result) {
            if (hide(it.groupValues[2])) "${it.groupValues[1]}${it.groupValues[2]}=$HIDDEN" else it.value
        }
        result = unquotedAssignment.replace(result) {
            if (hide(it.groupValues[1])) "${it.groupValues[1]}${it.groupValues[2]}$HIDDEN" else it.value
        }
        result = xmlValue.replace(result) {
            if (hide(it.groupValues[1])) "<${it.groupValues[1]}>$HIDDEN</${it.groupValues[1]}>" else it.value
        }
        result = htmlInput.replace(result) { input ->
            val attributes = htmlAttribute.findAll(input.value).toList()
            val isPassword = attributes.any {
                val attribute = it.groupValues[1].lowercase()
                val value = it.groupValues[3].trim('"', '\'')
                attribute == "type" && value.equals("password", true) || attribute == "name" && hide(value)
            }
            if (!isPassword) input.value else htmlAttribute.replace(input.value) {
                if (it.groupValues[1].equals("value", true)) "${it.groupValues[1]}${it.groupValues[2]}\"$HIDDEN\"" else it.value
            }
        }
        result = basicPassword.replace(result, "Basic $HIDDEN")
        result = urlPassword.replace(result) { "${it.groupValues[1]}$HIDDEN@" }
        passwordVariants().sortedByDescending { it.length }.forEach { value -> result = result.replace(value, HIDDEN) }
        return result
    }

    /** Consumes an entire sensitive JSON value, including nested objects/arrays and truncated values. */
    private fun hideJsonValues(text: String, hide: (String) -> Boolean): String {
        val output = StringBuilder()
        var copied = 0
        for (match in jsonKey.findAll(text)) {
            if (match.range.first < copied) continue
            val endQuote = match.value.lastIndexOf(match.value[0])
            if (!hide(match.value.substring(1, endQuote))) continue
            val valueStart = match.range.last + 1
            val valueEnd = jsonValueEnd(text, valueStart)
            output.append(text, copied, valueStart).append('"').append(HIDDEN).append('"')
            copied = valueEnd
        }
        return if (copied == 0) text else output.append(text, copied, text.length).toString()
    }

    private fun jsonValueEnd(text: String, start: Int): Int {
        if (start >= text.length) return text.length
        val first = text[start]
        if (first == '"' || first == '\'') {
            var escaped = false
            for (index in start + 1 until text.length) {
                val char = text[index]
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == first) return index + 1
            }
            return text.length
        }
        if (first == '{' || first == '[') {
            val closing = ArrayDeque<Char>()
            var quoted = false
            var escaped = false
            for (index in start until text.length) {
                val char = text[index]
                if (quoted) {
                    if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{' -> closing.addLast('}')
                    '[' -> closing.addLast(']')
                    '}', ']' -> {
                        if (closing.removeLastOrNull() != char) return text.length
                        if (closing.isEmpty()) return index + 1
                    }
                }
            }
            return text.length
        }
        var end = start
        while (end < text.length && text[end] !in ",}]\r\n") end++
        return end
    }

    @Synchronized private fun passwordVariants(): List<String> {
        prunePasswords()
        return knownPasswords.keys.flatMap { raw ->
            val encoded = URLEncoder.encode(raw, "UTF-8")
            listOf(raw, encoded, encoded.replace("+", "%20"), jsonEscape(raw))
        }.filter { it.isNotEmpty() }.distinct()
    }

    private fun prunePasswords() {
        val now = System.nanoTime() / 1_000_000
        knownPasswords.entries.removeAll { now < it.value || now - it.value >= 10 * 60 * 1000 }
    }

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun normalizeKey(key: String): String {
        var decoded = unicodeEscape.replace(key) { it.groupValues[1].toInt(16).toChar().toString() }
        repeat(2) { decoded = runCatching { URLDecoder.decode(decoded, "UTF-8") }.getOrDefault(decoded) }
        return decoded.lowercase().replace(Regex("[\\s_.-]"), "")
    }
}
