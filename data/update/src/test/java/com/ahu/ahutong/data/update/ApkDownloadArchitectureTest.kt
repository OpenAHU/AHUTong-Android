package com.ahu.ahutong.data.update

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 下载器两条「容易被改坏」的约定的源码级断言：
 *
 * 1. 重新下载必须先关掉进行中的 call，再等旧下载结束——只取消协程打断不了阻塞中的读；
 * 2. 正常收尾只对 part 文件算一次摘要（算两次意味着复制路径与重命名路径都被走了）。
 *
 * 它们原先断言 MainViewModel 的形状，随下载器一起搬进模块——被测代码去哪儿，测试就跟到哪儿。
 */
class ApkDownloadArchitectureTest {

    @Test
    fun `restarting closes active calls before joining the old download`() {
        val downloader = source("com/ahu/ahutong/data/update/ApkDownloader.kt")
        val cancelIndex = downloader.indexOf("transport.cancelAll()")
        val joinIndex = downloader.indexOf("previous?.join()")

        assertTrue(cancelIndex >= 0)
        assertTrue(joinIndex > cancelIndex)
        assertTrue(
            source("com/ahu/ahutong/data/update/ApkDownloadApi.kt").contains("dispatcher.cancelAll()")
        )
    }

    @Test
    fun `normal APK finalization hashes the completed part only once`() {
        val downloader = source("com/ahu/ahutong/data/update/ApkDownloader.kt")

        assertEquals(1, Regex("sha256Of\\(partFile\\)").findAll(downloader).count())
    }

    @Test
    fun `the downloader gets its directory through the module seam`() {
        val downloader = source("com/ahu/ahutong/data/update/ApkDownloader.kt")

        assertTrue(downloader.contains("private val directory: ApkDirectory"))
        assertTrue(downloader.contains("private val transport: ApkDownloadTransport"))
        assertFalse(downloader.contains("AppEnvironmentHolder"))
        assertFalse(downloader.contains("getExternalFilesDir"))
    }

    private fun source(relativePath: String): String = File(
        repositoryRoot(),
        "data/update/src/main/java/$relativePath"
    ).readText()

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
