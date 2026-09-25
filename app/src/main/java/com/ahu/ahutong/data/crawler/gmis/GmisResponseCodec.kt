package com.ahu.ahutong.data.crawler.gmis

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.IOException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class GmisSessionExpiredException : IOException("研究生教务登录已失效，请重新登录")
class GmisProtocolException(message: String) : IOException(message)

/** Mirrors the public GMIS /Scripts/rajax.js codec; these constants are not user credentials. */
internal object GmisResponseCodec {
    private val key by lazy {
        val seed = "sopthsk!#032IJDS".reversed()
        (decrypt("ND7TLBY9Cx/SdS0R/7dqmg==", seed) +
            decrypt("==AVRFTTkU6hlHQpo3Q+3FXZ".reversed(), seed) +
            decrypt("2dLxnqs81pKORjc2MlklrQ==", seed)).toByteArray(Charsets.UTF_8)
    }

    fun decode(body: String): JsonElement {
        val raw = body.trim().removePrefix("\uFEFF")
        if (raw.startsWith("<")) throw GmisSessionExpiredException()
        if (raw.isBlank()) throw GmisProtocolException("研究生教务返回了空响应")
        val json = if (raw.startsWith("{") || raw.startsWith("[")) raw else try {
            val encrypted = if (raw.startsWith('"')) JsonParser.parseString(raw).asString else raw
            decryptBytes(encrypted, key)
        } catch (e: Exception) {
            throw GmisProtocolException("研究生教务响应无法解码，请稍后重试")
        }
        if (json.trimStart().startsWith("<")) throw GmisSessionExpiredException()
        return try {
            JsonParser.parseString(json).also {
                if (!it.isJsonArray && !it.isJsonObject) {
                    throw GmisProtocolException("研究生教务返回的数据格式不正确")
                }
            }
        } catch (e: GmisProtocolException) {
            throw e
        } catch (e: Exception) {
            throw GmisProtocolException("研究生教务返回的数据格式不正确")
        }
    }

    private fun decrypt(data: String, key: String) = decryptBytes(data, key.toByteArray(Charsets.UTF_8))

    private fun decryptBytes(data: String, key: ByteArray): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return String(cipher.doFinal(Base64.getDecoder().decode(data.filterNot(Char::isWhitespace))), Charsets.UTF_8)
    }
}
