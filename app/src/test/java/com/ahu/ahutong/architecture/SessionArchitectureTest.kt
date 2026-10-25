package com.ahu.ahutong.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class SessionArchitectureTest {

    @Test
    fun `derived mailbox authentication cannot publish global session status`() {
        val mailbox = source("app/src/main/java/com/ahu/ahutong/data/mail/StudentMailSession.kt")
        assertFalse(mailbox.contains("AhuSessionState.mark"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot(), relativePath).readText()

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
