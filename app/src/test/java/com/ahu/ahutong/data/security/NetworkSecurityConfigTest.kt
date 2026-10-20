package com.ahu.ahutong.data.security

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkSecurityConfigTest {
    @Test
    fun `cleartext is limited to the loopback bridge and the canteen endpoint`() {
        val xml = File(repositoryRoot(), "app/src/main/res/xml/network_security_config.xml")
            .readText()

        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"false\""))
        assertEquals(2, Regex("<domain-config cleartextTrafficPermitted=\"true\"").findAll(xml).count())
        assertTrue(xml.contains(">127.0.0.1</domain>"))
        assertTrue(xml.contains(">localhost</domain>"))
        // 必吃榜服务器在域名备案完成前按 IP 明文直连（HTTPS 上线后删除该块）。
        assertTrue(xml.contains(">121.37.174.199</domain>"))
    }

    @Test
    fun `query credentials never use the HTTP logging client`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/data/adapter/RepositoryNetworkRechargeSource.kt"
        ).readText()

        assertTrue(source.contains("YcardApi.loginRedirectClient.newCall(request)"))
        assertFalse(source.contains("YcardApi.okHttpClient.newBuilder()"))
    }

    @Test
    fun `the internal APK fallback is exposed only from its update directory`() {
        val paths = File(repositoryRoot(), "app/src/main/res/xml/file_paths.xml").readText()
        val directory = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/data/adapter/AppApkDirectory.kt"
        ).readText()

        assertTrue(paths.contains("<files-path name=\"update_files\" path=\"updates/\""))
        assertTrue(directory.contains("File(context.filesDir, \"updates\")"))
        assertFalse(paths.contains("<files-path name=\"internal_files\" path=\".\""))
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/res").isDirectory }
    }
}
