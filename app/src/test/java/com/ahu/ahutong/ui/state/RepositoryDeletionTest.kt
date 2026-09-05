package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.repository.DownloadedFile
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException

class RepositoryDeletionTest {
    @Test
    fun `single deletion returns the completed snapshot`() {
        val files = mutableListOf(file("delete.pdf"), file("keep.pdf"))
        val operations = mutableListOf<String>()

        val result = deleteDownloadedFiles(
            paths = listOf("delete.pdf"),
            delete = { path ->
                operations += "delete:$path"
                files.removeAll { it.path == path }
            },
            listFiles = {
                operations += "snapshot"
                files.toList()
            }
        )

        assertEquals(listOf("delete:delete.pdf", "snapshot"), operations)
        assertEquals(listOf("keep.pdf"), result.remainingFiles.map { it.path })
        assertTrue(result.failedPaths.isEmpty())
    }

    @Test
    fun `batch deletion attempts every file and retains only failed selections`() {
        val files = mutableListOf(file("first.pdf"), file("denied.pdf"), file("last.pdf"))
        val attempts = mutableListOf<String>()
        val result = deleteDownloadedFiles(
            paths = listOf("first.pdf", "denied.pdf", "last.pdf", "first.pdf"),
            delete = { path ->
                attempts += path
                if (path == "denied.pdf") throw IOException("Permission denied")
                files.removeAll { it.path == path }
            },
            listFiles = { files.toList() }
        )

        assertEquals(listOf("first.pdf", "denied.pdf", "last.pdf"), attempts)
        assertEquals(listOf("denied.pdf"), result.remainingFiles.map { it.path })
        assertEquals(setOf("denied.pdf"), result.failedPaths)
    }

    @Test
    fun `a false delete result retains an existing file but not an already missing file`() {
        val result = deleteDownloadedFiles(
            paths = listOf("denied.pdf", "missing.pdf"),
            delete = { false },
            listFiles = { listOf(file("denied.pdf")) }
        )

        assertEquals(setOf("denied.pdf"), result.failedPaths)
    }

    @Test
    fun `cancellation does not continue deleting the batch`() {
        val attempts = mutableListOf<String>()
        assertFailsWith<CancellationException> {
            deleteDownloadedFiles(
                paths = listOf("first.pdf", "second.pdf"),
                delete = {
                    attempts += it
                    throw CancellationException()
                },
                listFiles = { error("No snapshot after cancellation") }
            )
        }
        assertEquals(listOf("first.pdf"), attempts)
    }

    private fun file(path: String) = DownloadedFile(
        name = path,
        path = path,
        localPath = "/downloads/$path",
        downloadTime = 0L
    )
}
