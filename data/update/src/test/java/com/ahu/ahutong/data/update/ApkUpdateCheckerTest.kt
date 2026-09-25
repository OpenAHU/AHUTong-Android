package com.ahu.ahutong.data.update

import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 更新检查的契约测试：只用假的数据源与临时目录，因此不联网、不碰设备。
 *
 * 这几种情形原先只能靠真机点"检查更新"来看：云端说没有更新、远端版本不比本机新、
 * 元数据无效（摘要不是 64 位 hex）、请求失败。它们的措辞由展示侧决定，这里钉的是**分类**。
 */
class ApkUpdateCheckerTest {

    private val dir: File = Files.createTempDirectory("apk-check").toFile()
    private val skipStore = MemorySkipStore()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun checker(info: ApkUpdateInfo) = DefaultApkUpdateChecker(
        source = { info },
        directory = { dir },
        skipStore = skipStore
    )

    @Test
    fun `the server saying there is no update reports up to date`() {
        val result = kotlinx.coroutines.runBlocking {
            checker(ApkUpdateInfo(update = false))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun `a remote version that is not newer reports up to date`() {
        val result = kotlinx.coroutines.runBlocking {
            checker(validInfo(versionCode = 100))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        assertEquals(UpdateCheck.UpToDate, result)
    }

    @Test
    fun `a newer version is available and carries the normalized digest`() {
        val result = kotlinx.coroutines.runBlocking {
            checker(
                validInfo(versionCode = 101, sha256 = VALID_SHA256.uppercase())
            ).check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        val available = result as UpdateCheck.Available
        assertEquals(101, available.info.versionCode)
        assertEquals(VALID_SHA256, available.sha256)
        assertNull(available.localApk)
    }

    @Test
    fun `a validated package already on disk is reported as ready to install`() {
        val apkFile = ApkIntegrity.apkFile(dir, 101)
        apkFile.writeText("hello")
        val sha = ApkIntegrity.sha256Of(apkFile)

        val result = kotlinx.coroutines.runBlocking {
            checker(validInfo(versionCode = 101, sha256 = sha))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        val available = result as UpdateCheck.Available
        assertEquals(apkFile, available.localApk)
    }

    @Test
    fun `metadata with a bad digest is reported as invalid rather than as a request failure`() {
        val result = kotlinx.coroutines.runBlocking {
            checker(validInfo(versionCode = 101, sha256 = "nope"))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        val failed = result as UpdateCheck.Failed
        assertTrue(failed.invalidMetadata)
    }

    @Test
    fun `a request failure is reported without claiming the metadata was invalid`() {
        val failing = DefaultApkUpdateChecker(
            source = { throw IllegalStateException("host not found") },
            directory = { dir },
            skipStore = skipStore
        )

        val result = kotlinx.coroutines.runBlocking {
            failing.check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        val failed = result as UpdateCheck.Failed
        assertEquals("host not found", failed.reason)
        assertTrue(!failed.invalidMetadata)
    }

    /**
     * 两个入口共用同一套分类：入口只决定日志标签，不决定"这是什么情况"。
     */
    @Test
    fun bothEntriesClassifyIdentically() {
        val startup = kotlinx.coroutines.runBlocking {
            checker(validInfo(versionCode = 101, sha256 = "nope"))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }
        val manual = kotlinx.coroutines.runBlocking {
            checker(validInfo(versionCode = 101, sha256 = "nope"))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.MANUAL)
        }

        assertEquals(
            (startup as UpdateCheck.Failed).invalidMetadata,
            (manual as UpdateCheck.Failed).invalidMetadata
        )
    }

    @Test
    fun `skipped version is silent on startup but remains available manually and a newer code prompts again`() {
        val current = checker(validInfo(versionCode = 101))
        assertTrue(current.skipVersion(101))

        val startup = kotlinx.coroutines.runBlocking {
            current.check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }
        val manual = kotlinx.coroutines.runBlocking {
            current.check(currentVersionCode = 100, entry = UpdateCheckEntry.MANUAL)
        }
        val newer = kotlinx.coroutines.runBlocking {
            checker(validInfo(versionCode = 102))
                .check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        assertEquals(UpdateCheck.UpToDate, startup)
        assertTrue(manual is UpdateCheck.Available)
        assertTrue(newer is UpdateCheck.Available)
    }

    @Test
    fun `forced update still prompts even when its version code was skipped`() {
        val info = validInfo(versionCode = 101).copy(force = true)
        val checker = checker(info)
        assertTrue(checker.skipVersion(101))

        val result = kotlinx.coroutines.runBlocking {
            checker.check(currentVersionCode = 100, entry = UpdateCheckEntry.STARTUP)
        }

        assertTrue(result is UpdateCheck.Available)
    }

    private class MemorySkipStore : ApkUpdateSkipStore {
        var skipped: Int? = null
        override fun skippedVersionCode(): Int? = skipped
        override fun saveSkippedVersionCode(versionCode: Int): Boolean {
            skipped = versionCode
            return true
        }
        override suspend fun clear() {
            skipped = null
        }
    }

    private fun validInfo(versionCode: Int, sha256: String = VALID_SHA256) = ApkUpdateInfo(
        update = true,
        versionCode = versionCode,
        versionName = "1.2.3",
        changelog = "修好了若干问题",
        url = "https://openahu.org/download/app.apk",
        sha256 = sha256
    )

    private companion object {
        const val VALID_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }
}
