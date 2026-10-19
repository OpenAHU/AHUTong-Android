package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.ahu.ahutong.core.plugin.PLUGIN_API_LEVEL
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.Signature
import java.util.zip.ZipInputStream

/**
 * .ahup v3 包的安装/校验/装载。
 *
 * 包格式（ZIP）：
 *   manifest.json
 *   plugin.apk        —— 插件的 unsigned release APK（自带 dex/resources/assets/native lib）
 *   icon.png
 *
 * 安全模型：
 * - 签名载荷 = SHA-256(plugin.apk 字节 + icon 字节 + "id\nversion\nentryClass")
 *   —— manifest 参与绑定，堵住"改 id 覆盖他人插件、读对方存储"。
 * - 验签需要公钥，因此 manifest 必须携带 `signerCert`（Base64 X.509）；
 *   `signerFingerprint` 是证书的 SHA-256，用于 TOFU 展示与"同一作者"判定。
 * - 同 id 覆盖安装时指纹必须一致，否则拒绝（防冒名）。
 *
 * v1/v2 包（裸 plugin.dex）不再支持：旧契约是 `@Composable Entry`，新加载模型下无法渲染。
 */
object AhupInstaller {

    private const val APK_FILE = "plugin.apk"
    private const val MANIFEST_FILE = "manifest.json"
    private const val SIGNER_FILE = "signer.sha256"
    private const val LIB_PREFIX = "lib/"
    private const val MAX_APK_BYTES = 32 * 1024 * 1024

    private fun pluginDir(context: Context, id: String): File =
        File(context.filesDir, "ahup/$id")

    /** 方案 A 的载荷：插件自带的 unsigned release APK。 */
    fun apkFile(context: Context, id: String): File = File(pluginDir(context, id), APK_FILE)

    fun installedIds(context: Context): List<String> =
        File(context.filesDir, "ahup").listFiles()?.filter {
            File(it, MANIFEST_FILE).exists()
        }?.map { it.name }.orEmpty()

    fun manifestOf(context: Context, id: String): AhupManifest? = runCatching {
        AhupManifest.parse(File(pluginDir(context, id), MANIFEST_FILE).readText())
    }.getOrNull()

    fun iconFile(context: Context, id: String, name: String): File = File(pluginDir(context, id), name)

    /** 已安装插件的签名指纹（TOFU 台账）；没有则 null。 */
    fun installedFingerprint(context: Context, id: String): String? =
        File(pluginDir(context, id), SIGNER_FILE)
            .takeIf { it.isFile }
            ?.readText()?.trim()?.takeIf { it.isNotEmpty() }

    /** 安装目录下本机可用的原生库搜索目录；APK 内的 .so 由 [install] 解出。 */
    fun nativeLibDir(context: Context, id: String, supportedAbis: Array<String>): File? {
        val libRoot = File(pluginDir(context, id), LIB_PREFIX)
        if (!libRoot.isDirectory) return null
        for (abi in supportedAbis) {
            val dir = File(libRoot, abi)
            if (dir.isDirectory) return dir
        }
        return null
    }

    /** 解析候选包（不落盘）：供安装确认弹窗展示元数据与签名状态。 */
    fun inspect(context: Context, uri: Uri): AhupCandidate {
        val entries = readZip(context, uri)
        val manifestRaw = entries[MANIFEST_FILE]
            ?: throw AhupException("E_FORMAT：包内缺少 manifest.json")
        val manifest = AhupManifest.parse(manifestRaw.decodeToString())

        if (manifest.format != AhupManifest.CURRENT_FORMAT) {
            throw AhupException(
                "E_FORMAT：该插件是旧格式（v${manifest.format}），当前版本不再支持，请用新版 SDK 重新打包"
            )
        }
        if (manifest.apiLevel > PLUGIN_API_LEVEL) {
            throw AhupException(
                "E_API_LEVEL：该插件需要更新版本的安大通（需 API ${manifest.apiLevel}，当前 $PLUGIN_API_LEVEL）"
            )
        }

        val apk = entries[APK_FILE] ?: throw AhupException("E_FORMAT：包内缺少 plugin.apk")
        if (apk.size > MAX_APK_BYTES) throw AhupException("E_FORMAT：plugin.apk 过大")
        val icon = entries[manifest.iconFile]
            ?: throw AhupException("E_FORMAT：包内缺少 ${manifest.iconFile}")

        val existing = installedFingerprint(context, manifest.id)
        val incoming = manifest.signerFingerprint.takeIf { it.isNotBlank() }
        val signerChanged = existing != null && incoming != null &&
            !existing.equals(incoming, ignoreCase = true)
        val status = verifySignature(manifest, apk, icon, existing)
        return AhupCandidate(manifest, manifestRaw, apk, icon, status, signerChanged)
    }

    /** 落盘安装（先 inspect 再装）。同 id 旧版本覆盖。 */
    fun install(context: Context, candidate: AhupCandidate) {
        val manifest = candidate.manifest
        val existing = installedFingerprint(context, manifest.id)
        val incoming = manifest.signerFingerprint.takeIf { it.isNotBlank() }
        if (existing != null && incoming != null && !existing.equals(incoming, ignoreCase = true)) {
            throw AhupException("E_SIGNER_CHANGED：该插件签名指纹已变化，请先卸载旧版本")
        }

        val dir = pluginDir(context, manifest.id)
        dir.deleteRecursively()
        dir.mkdirs()
        File(dir, MANIFEST_FILE).writeBytes(candidate.manifestRaw)
        File(dir, manifest.iconFile).writeBytes(candidate.icon)
        val apk = File(dir, APK_FILE).apply { writeBytes(candidate.apk) }

        // DexClassLoader 不会自动解 APK 内的 .so，必须自己落到 lib/<abi>/
        extractNativeLibs(candidate.apk, dir)

        // Android 14+：动态加载的代码必须只读
        apk.setReadOnly()
        incoming?.let { File(dir, SIGNER_FILE).writeText(it) }

        Log.i(
            TAG,
            "插件已安装：${manifest.id} v${manifest.version}（format 3，API ${manifest.apiLevel}）"
        )
    }

    fun uninstall(context: Context, id: String) {
        pluginDir(context, id).deleteRecursively()
    }

    /* ---------------- 签名 ---------------- */

    enum class SignatureStatus { TRUSTED, KNOWN, UNKNOWN, UNSIGNED, INVALID }

    /**
     * @param knownFingerprint 已安装版本的指纹（TOFU 比对用）；用于把"签名合法但不认识"
     *        细分为 KNOWN（同一作者）/ UNKNOWN（新作者）。
     */
    internal fun verifySignature(
        manifest: AhupManifest,
        apk: ByteArray,
        icon: ByteArray,
        knownFingerprint: String? = null
    ): SignatureStatus {
        if (manifest.signature.isBlank() || manifest.signerCert.isBlank()) {
            return SignatureStatus.UNSIGNED
        }
        return try {
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(Base64.decode(manifest.signerCert, Base64.DEFAULT).inputStream())
            val actualFingerprint = sha256Hex(cert.encoded)
            if (!actualFingerprint.equals(manifest.signerFingerprint, ignoreCase = true)) {
                return SignatureStatus.INVALID
            }

            val digest = MessageDigest.getInstance("SHA-256").digest(
                signaturePayloadV3(
                    apk = apk,
                    icon = icon,
                    id = manifest.id,
                    version = manifest.version,
                    entryClass = manifest.entryClass
                )
            )
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(cert.publicKey)
            sig.update(digest)
            if (!sig.verify(Base64.decode(manifest.signature, Base64.DEFAULT))) {
                return SignatureStatus.INVALID
            }

            when {
                actualFingerprint.equals(trustedFingerprint(), ignoreCase = true) -> SignatureStatus.TRUSTED
                knownFingerprint != null && actualFingerprint.equals(knownFingerprint, ignoreCase = true) ->
                    SignatureStatus.KNOWN
                else -> SignatureStatus.UNKNOWN
            }
        } catch (e: Throwable) {
            Log.w(TAG, "验签失败", e)
            SignatureStatus.INVALID
        }
    }

    /** 钉死的团队证书指纹（assets/ahup_trusted_fingerprint.txt，hex）。缺失 = 没有官方信任项。 */
    private fun trustedFingerprint(): String? = runCatching {
        com.ahu.ahutong.core.common.AppEnvironmentHolder.context().assets
            .open("ahup_trusted_fingerprint.txt").bufferedReader().readText().trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /* ---------------- 工具 ---------------- */

    private fun extractNativeLibs(apkBytes: ByteArray, dir: File) {
        ZipInputStream(ByteArrayInputStream(apkBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory &&
                    entry.name.startsWith(LIB_PREFIX) &&
                    entry.name.endsWith(".so")
                ) {
                    val out = File(dir, entry.name)
                    out.parentFile?.mkdirs()
                    out.writeBytes(zip.readBytes())
                    out.setReadOnly()
                }
                zip.closeEntry()
            }
        }
    }

    private fun readZip(context: Context, uri: Uri): Map<String, ByteArray> {
        val out = mutableMapOf<String, ByteArray>()
        val raw = context.contentResolver.openInputStream(uri)
            ?: throw AhupException("无法读取所选文件")
        raw.use { input ->
            // 魔数校验：非 ZIP 直接拒（连解析都不进）
            val magic = ByteArray(4)
            val read = input.read(magic)
            if (read < 4 || magic[0] != 0x50.toByte() || magic[1] != 0x4B.toByte()) {
                throw AhupException("不是有效的插件包（.ahup 是 ZIP 格式）")
            }
            ZipInputStream(java.io.SequenceInputStream(magic.inputStream(), input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) out[entry.name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        } ?: throw AhupException("无法读取所选文件")
        return out
    }

    private const val TAG = "AhupInstaller"
}

/** inspect 的产出：落盘前的一切。manifestRaw 是包内原文（签名状态展示的凭据）。 */
class AhupCandidate(
    val manifest: AhupManifest,
    val manifestRaw: ByteArray,
    val apk: ByteArray,
    val icon: ByteArray,
    val signatureStatus: AhupInstaller.SignatureStatus,
    /** 同 id 已安装版本的指纹与包内不一致 → 安装按钮必须禁用，先卸载。 */
    val signerChanged: Boolean
)

class AhupException(message: String) : Exception(message)

/**
 * .ahup v3 签名载荷：plugin.apk + icon + 规范化绑定串（id / version / entryClass）。
 * 绑定 manifest 身份，防止改 id 覆盖他人插件。
 */
internal fun signaturePayloadV3(
    apk: ByteArray,
    icon: ByteArray,
    id: String,
    version: String,
    entryClass: String
): ByteArray = apk + icon + "$id\n$version\n$entryClass".toByteArray(Charsets.UTF_8)
