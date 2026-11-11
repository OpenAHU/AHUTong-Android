package com.ahu.ahutong.assistant.safety.content

import java.text.Normalizer
import java.util.Base64

/**
 * 检测前统一文本形态：全角、零宽、HTML 实体、百分号编码、汉字间分隔符、base64 片段。
 * 只做确定性变换，不做任何语义推断。
 */
internal object ContentNormalizer {

    private val zeroWidth = Regex("[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]")
    private val hanSeparators = Regex("(?<=\\p{IsHan})[\\s*·•._|/\\\\-]+(?=\\p{IsHan})")
    private val whitespace = Regex("\\s+")
    private val base64Token = Regex("[A-Za-z0-9+/]{8,}={0,2}")
    private val percentTriplet = Regex("%[0-9A-Fa-f]{2}")
    private val htmlEntity = Regex("&(amp|lt|gt|quot|#39|nbsp);")

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        var text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        text = zeroWidth.replace(text, "")
        text = htmlEntity.replace(text) { match ->
            when (match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "#39" -> "'"
                else -> " "
            }
        }
        text = decodePercent(text)
        // base64 区分大小写，必须在转小写之前解码。
        val decodedBase64 = extractDecodedBase64(text)
        text = text.lowercase()
        text = hanSeparators.replace(text, "")
        text = whitespace.replace(text, " ").trim()
        return if (decodedBase64.isBlank()) text else "$text ${decodedBase64.lowercase()}"
    }

    private fun decodePercent(text: String): String {
        if (!percentTriplet.containsMatchIn(text)) return text
        val out = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            if (text[index] == '%' && index + 3 <= text.length) {
                val bytes = mutableListOf<Byte>()
                var cursor = index
                while (cursor + 3 <= text.length && text[cursor] == '%') {
                    val value = text.substring(cursor + 1, cursor + 3).toIntOrNull(16) ?: break
                    bytes += value.toByte()
                    cursor += 3
                }
                val decoded = bytes.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { String(it.toByteArray(), Charsets.UTF_8) }.getOrNull() }
                if (!decoded.isNullOrEmpty()) {
                    out.append(decoded)
                    index = cursor
                    continue
                }
            }
            out.append(text[index])
            index++
        }
        return out.toString()
    }

    private fun extractDecodedBase64(text: String): String {
        if (text.length < 8) return ""
        return buildString {
            base64Token.findAll(text).forEach { match ->
                val token = match.value
                val bytes = runCatching { Base64.getDecoder().decode(token) }.getOrNull()
                    ?: return@forEach
                val value = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                    ?: return@forEach
                if (value.length >= 2 && cjkRatio(value) >= 0.5f) {
                    append(' ')
                    append(value)
                }
            }
        }
    }

    private fun cjkRatio(value: String): Float {
        if (value.isEmpty()) return 0f
        val cjk = value.count { it.code in 0x4E00..0x9FFF }
        return cjk.toFloat() / value.length
    }
}
