package com.ahu.ahutong.ui.plugin

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.security.Signature
import java.util.zip.ZipInputStream

/**
 * .ahup 包的安装/校验/装载。
 *
 * 包格式（ZIP）：
 *   v1：manifest.json + plugin.dex + icon.png
 *   v2：额外可选原生库，位于 lib 下各 abi 目录的 .so 文件（如 OpenCV；由 zip 内容判定，manifest 不参与）
 *
 * 安全模型：签名是唯一闸门，签的是可执行体。
 *   v1 载荷 = sha256(dex + icon)
 *   v2 载荷 = sha256(dex + icon + lib 内全部 .so 按相对路径排序后逐一拼接)
 *   （.so 是可执行代码，不纳入信任链 = 给改包注入留门）
 * manifest.json 只是展示元数据（不参与签名，篡改了也无害——能力门控读的是已签名的
 * 插件代码里的 capabilities 声明，弹窗展示的以代码声明为准）。
 * 未签名/签名不符的包给强警告，由用户自担风险确认。
 */
object AhupInstaller {

    private const val DEX_FILE = "plugin.dex"
    private const val APK_FILE = "plugin.apk"
    private const val MANIFEST_FILE = "manifest.json"
    private const val LIB_PREFIX = "lib/"

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

    fun dexFile(context: Context, id: String): File = File(pluginDir(context, id), DEX_FILE)
    fun iconFile(context: Context, id: String, name: String): File = File(pluginDir(context, id), name)

    /** 安装目录下本机可用的原生库搜索目录（v2 包装载用）；v1 包返回 null。 */
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
            ?: throw AhupException("包内缺少 manifest.json")
        val manifest = AhupManifest.parse(manifestRaw.decodeToString())
        val dex = entries[DEX_FILE] ?: throw AhupException("包内缺少 plugin.dex")
        val icon = entries[manifest.iconFile] ?: throw AhupException("包内缺少 ${manifest.iconFile}")
        val libs = entries.filterKeys { it.startsWith(LIB_PREFIX) && it.endsWith(".so") }
        val signatureStatus = verifySignature(manifest, dex, icon, libs)
        return AhupCandidate(manifest, manifestRaw, dex, icon, libs, signatureStatus)
    }

    /** 落盘安装（先 inspect 再装）。覆盖同 id 旧版本。 */
    fun install(context: Context, candidate: AhupCandidate) {
        val dir = pluginDir(context, candidate.manifest.id)
        dir.deleteRecursively()
        dir.mkdirs()
        File(dir, MANIFEST_FILE).writeBytes(candidate.manifestRaw)
        File(dir, DEX_FILE).writeBytes(candidate.dex)
        File(dir, candidate.manifest.iconFile).writeBytes(candidate.icon)
        // Android 14+：动态加载的 dex 必须只读，否则 ClassLoader 拒载
        File(dir, DEX_FILE).setReadOnly()
        // v2：原生库按相对路径落盘（lib 下各 abi 目录的 .so）
        candidate.libs.forEach { (path, bytes) ->
            val out = File(dir, path)
            out.parentFile?.mkdirs()
            out.writeBytes(bytes)
            out.setReadOnly()
        }
        Log.i(TAG, "插件已安装：${candidate.manifest.id} v${candidate.manifest.version}" +
            (if (candidate.libs.isEmpty()) "" else "（含 ${candidate.libs.size} 个原生库）"))
    }

    fun uninstall(context: Context, id: String) {
        pluginDir(context, id).deleteRecursively()
    }

    /* ---------------- 签名 ---------------- */

    enum class SignatureStatus { TRUSTED, UNSIGNED, INVALID }

    internal fun verifySignature(
        manifest: AhupManifest,
        dex: ByteArray,
        icon: ByteArray,
        libs: Map<String, ByteArray> = emptyMap()
    ): SignatureStatus {
        if (manifest.signature.isBlank()) return SignatureStatus.UNSIGNED
        val pubkey = trustedPublicKey() ?: return SignatureStatus.UNSIGNED
        return runCatching {
            // 载荷由 zip 内容确定性判定：有 lib 即 v2，无则 v1（构造与测试共用 signaturePayload）
            val digest = MessageDigest.getInstance("SHA-256").digest(signaturePayload(dex, icon, libs))
            val sig = Signature.getInstance("SHA256withRSA")
            // assets 里钉的是 X.509 证书（keytool -exportcert 产物），先解证书再取公钥
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(Base64.decode(pubkey, Base64.DEFAULT).inputStream())
            sig.initVerify(cert.publicKey)
            sig.update(digest)
            if (sig.verify(Base64.decode(manifest.signature, Base64.DEFAULT))) {
                SignatureStatus.TRUSTED
            } else {
                SignatureStatus.INVALID
            }
        }.getOrDefault(SignatureStatus.INVALID)
    }

    /** 钉死的团队公钥（assets/ahup_trusted_pubkey.txt，Base64 X509）。缺失 = 全部按未签名处理。 */
    private fun trustedPublicKey(): String? = runCatching {
        com.ahu.ahutong.core.common.AppEnvironmentHolder.context().assets
            .open("ahup_trusted_pubkey.txt").bufferedReader().readText().trim()
    }.getOrNull()

    /* ---------------- 工具 ---------------- */

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

/** inspect 的产出：落盘前的一切。manifestRaw 是包内原文（签名状态展示的凭据）；
 *  libs 是 lib 下各 abi 目录 .so 的相对路径到字节映射（v2 包才有，参与签名与落盘）。 */
class AhupCandidate(
    val manifest: AhupManifest,
    val manifestRaw: ByteArray,
    val dex: ByteArray,
    val icon: ByteArray,
    val libs: Map<String, ByteArray> = emptyMap(),
    val signatureStatus: AhupInstaller.SignatureStatus
)

class AhupException(message: String) : Exception(message)

/**
 * .ahup 签名载荷构造（v1/v2 由 zip 内容确定性判定，两端共用同一语义）：
 * v1（无 so）= dex + icon；v2（有 so）= dex + icon + 全部 .so 按相对路径排序后逐一拼接。
 * internal 化以便 JVM 单测直接验证拼接顺序。
 */
internal fun signaturePayload(dex: ByteArray, icon: ByteArray, libs: Map<String, ByteArray>): ByteArray {
    if (libs.isEmpty()) return dex + icon
    var acc = dex + icon
    libs.keys.sorted().forEach { rel -> acc += libs.getValue(rel) }
    return acc
}
