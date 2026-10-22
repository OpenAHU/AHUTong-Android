package com.ahu.ahutong.ui.plugin

import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginDataCleanupTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private class CleanupContext(private val appFiles: File) : ContextWrapper(null) {
        val deletedPreferences = mutableListOf<String>()
        override fun getFilesDir(): File = appFiles
        override fun deleteSharedPreferences(name: String): Boolean {
            deletedPreferences += name
            val preferences = File(appFiles.parentFile, "shared_prefs")
            File(preferences, "$name.xml").delete()
            File(preferences, "$name.xml.bak").delete()
            return true
        }
    }

    @Test fun uninstallKeepsOtherPluginsWithTheSameIdPrefix() {
        val files = temporaryFolder.newFolder("files")
        val preferences = temporaryFolder.newFolder("shared_prefs")
        listOf("plugin_foo.xml", "plugin_foo__storage.xml", "plugin_foo__settings.xml",
            "plugin_foobar.xml", "plugin_foobar__storage.xml", "unrelated.xml").forEach {
            File(preferences, it).writeText("<map/>")
        }
        val removedData = File(files, "ahup_plugins/foo/files/data").apply {
            parentFile.mkdirs()
            writeText("foo")
        }
        val preservedData = File(files, "ahup_plugins/foobar/files/data").apply {
            parentFile.mkdirs()
            writeText("foobar")
        }
        val context = CleanupContext(files)

        cleanupPluginData(context, "foo")

        assertEquals(setOf("plugin_foo", "plugin_foo__storage", "plugin_foo__settings"),
            context.deletedPreferences.toSet())
        assertFalse(removedData.exists())
        assertTrue(preservedData.exists())
        assertEquals(setOf("plugin_foobar.xml", "plugin_foobar__storage.xml", "unrelated.xml"),
            preferences.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test fun backupFilesUseThePlatformDeletionApiOnceForEachPreferenceName() {
        val files = temporaryFolder.newFolder("files")
        val preferences = temporaryFolder.newFolder("shared_prefs")
        listOf("plugin_foo.xml.bak", "plugin_foo__storage.xml", "plugin_foo__storage.xml.bak",
            "plugin_foobar.xml.bak", "plugin_foo_notes.xml").forEach {
            File(preferences, it).writeText("<map/>")
        }
        val context = CleanupContext(files)

        cleanupPluginData(context, "foo")

        assertEquals(setOf("plugin_foo", "plugin_foo__storage"), context.deletedPreferences.toSet())
        assertEquals(2, context.deletedPreferences.size)
        assertEquals(setOf("plugin_foobar.xml.bak", "plugin_foo_notes.xml"),
            preferences.listFiles().orEmpty().map { it.name }.toSet())
    }
}
