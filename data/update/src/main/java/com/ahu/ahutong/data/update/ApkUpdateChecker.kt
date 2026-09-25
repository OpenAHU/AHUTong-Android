package com.ahu.ahutong.data.update

import android.util.Log
import com.ahu.ahutong.data.server.ApkUpdatePolicy
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 更新包的存放目录：生产是应用的外部私有目录，测试给一个临时目录。 */
fun interface ApkDirectory {
    fun dir(): File
}

/** 云端更新元数据的来源：实现转给服务端接口，测试可以给一个固定答案。 */
fun interface ApkUpdateInfoSource {
    suspend fun latest(): ApkUpdateInfo
}

/** Device-wide preference: the user declined automatic prompts for this exact versionCode. */
interface ApkUpdateSkipStore {
    fun skippedVersionCode(): Int?
    fun saveSkippedVersionCode(versionCode: Int): Boolean
    suspend fun clear()
}

/** 一次更新检查的结果（域名层；"要不要弹窗、怎么措辞"留给展示侧）。 */
sealed interface UpdateCheck {

    /** 有新版本：[localApk] 非空表示本地已有校验通过的包，可以提示"直接安装"。 */
    data class Available(
        val info: ApkUpdateInfo,
        val downloadUrl: String,
        val sha256: String,
        val localApk: File?
    ) : UpdateCheck

    /** 没有自动提醒：云端无更新、版本不高于本机，或用户跳过了该版本。 */
    data object UpToDate : UpdateCheck

    /**
     * 请求失败或元数据无效；[reason] 供展示侧拼"检查更新失败：…"。
     * [invalidMetadata] 区分两者：无效元数据的兜底文案是"更新信息无效"，请求失败是"请稍后重试"。
     */
    data class Failed(val reason: String?, val invalidMetadata: Boolean) : UpdateCheck
}

/**
 * 更新检查的两个入口。
 *
 * 迁移前两个入口的日志标签不同（启动检查是 "startup update check request failed" / "local APK"，
 * 手动检查是 "manual update check failed" / "manual check local APK"），现场靠它们区分是谁在问；
 * 因此标签作为参数传进来，而检查规则仍然只有一份。
 */
enum class UpdateCheckEntry(
    val requestFailureLabel: String,
    val localApkLabel: String
) {
    STARTUP(
        requestFailureLabel = "startup update check request failed",
        localApkLabel = "local APK"
    ),
    MANUAL(
        requestFailureLabel = "manual update check failed",
        localApkLabel = "manual check local APK"
    )
}

/**
 * 更新检查：清理残留、取元数据、按策略校验、看本地有没有可用的包。
 *
 * 原先这段写在 MainViewModel 里，且**启动检查与手动检查各写一遍**（措辞与静默策略略有不同）。
 * 收进模块后只有一份规则；两种入口的差异由调用方对 [UpdateCheck] 的处理体现。
 */
interface ApkUpdateChecker {
    suspend fun check(currentVersionCode: Int, entry: UpdateCheckEntry): UpdateCheck

    /** Persist a user's explicit decision before dismissing the update dialog. */
    fun skipVersion(versionCode: Int): Boolean
}

class DefaultApkUpdateChecker @Inject constructor(
    private val source: ApkUpdateInfoSource,
    private val directory: ApkDirectory,
    private val skipStore: ApkUpdateSkipStore
) : ApkUpdateChecker {

    override fun skipVersion(versionCode: Int): Boolean {
        if (versionCode <= 0) return false
        return runCatching { skipStore.saveSkippedVersionCode(versionCode) }
            .onFailure { Log.w(TAG, "Unable to save skipped APK version", it) }
            .getOrDefault(false)
    }

    override suspend fun check(
        currentVersionCode: Int,
        entry: UpdateCheckEntry
    ): UpdateCheck = withContext(Dispatchers.IO) {
        val dir = directory.dir()
        ApkIntegrity.cleanStaleApks(dir, currentVersionCode)

        val info = runCatching { source.latest() }
            .onFailure { Log.w(TAG, entry.requestFailureLabel, it) }
            .getOrElse { error -> return@withContext UpdateCheck.Failed(error.message, invalidMetadata = false) }

        val validated = ApkUpdatePolicy.validate(info, currentVersionCode).getOrElse { error ->
            return@withContext if (ApkUpdatePolicy.isNoUpdateFailure(error)) {
                UpdateCheck.UpToDate
            } else {
                Log.w(TAG, "ignore invalid APK update metadata: ${error.message}")
                UpdateCheck.Failed(error.message, invalidMetadata = true)
            }
        }

        if (entry == UpdateCheckEntry.STARTUP && !validated.info.force) {
            val skippedVersion = runCatching { skipStore.skippedVersionCode() }
                .onFailure { Log.w(TAG, "Unable to read skipped APK version", it) }
                .getOrNull()
            if (skippedVersion == validated.info.versionCode) return@withContext UpdateCheck.UpToDate
        }

        val localApk = ApkIntegrity.apkFile(dir, validated.info.versionCode)
        val localReady = if (localApk.exists() && localApk.length() > 0) {
            ApkIntegrity.verifySha256(localApk, validated.sha256, entry.localApkLabel)
        } else {
            false
        }

        UpdateCheck.Available(
            info = validated.info,
            downloadUrl = validated.downloadUrl,
            sha256 = validated.sha256,
            localApk = localApk.takeIf { localReady }
        )
    }


    private companion object {
        const val TAG = "ApkUpdate"
    }
}
