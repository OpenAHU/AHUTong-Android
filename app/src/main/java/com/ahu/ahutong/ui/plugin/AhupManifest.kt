package com.ahu.ahutong.ui.plugin

import org.json.JSONObject

/** .ahup 包内 manifest.json 的解析结果（format 3）。 */
data class AhupManifest(
    val id: String,
    val title: String,
    val summary: String,
    val tint: Long,
    val version: String,
    val author: String,
    val entryClass: String,
    val iconFile: String,
    val capabilities: Set<String>,
    /** Base64 RSA/SHA256 签名（对 v3 载荷摘要签名）。空 = 未签名。 */
    val signature: String,
    /** 包格式版本；3 = plugin.apk 载荷。旧包缺省为 1。 */
    val format: Int,
    /** 契约 API 等级，必须 <= 宿主的 PLUGIN_API_LEVEL。 */
    val apiLevel: Int,
    /** SDK 版本，仅展示与排障。 */
    val sdkVersion: String,
    /** 签名证书公钥的 SHA-256（hex）。用于 TOFU 与展示。 */
    val signerFingerprint: String,
    /** Base64 X.509 证书。没有它无法验证 RSA 签名。 */
    val signerCert: String
) {
    companion object {
        const val CURRENT_FORMAT = 3

        fun parse(raw: String): AhupManifest {
            val obj = JSONObject(raw)
            val caps = buildSet {
                obj.optJSONArray("capabilities")?.let { arr ->
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            }
            return AhupManifest(
                id = obj.getString("id"),
                title = obj.getString("title"),
                summary = obj.optString("summary", ""),
                tint = obj.optString("tint", "0xFF607D8B")
                    .removePrefix("0x").toLong(16),
                version = obj.optString("version", "0.0.0"),
                author = obj.optString("author", "未知作者"),
                entryClass = obj.getString("entryClass"),
                iconFile = obj.optString("icon", "icon.png"),
                capabilities = caps,
                signature = obj.optString("signature", ""),
                format = obj.optInt("format", 1),
                apiLevel = obj.optInt("apiLevel", 0),
                sdkVersion = obj.optString("sdkVersion", ""),
                signerFingerprint = obj.optString("signerFingerprint", ""),
                signerCert = obj.optString("signerCert", "")
            )
        }
    }
}
