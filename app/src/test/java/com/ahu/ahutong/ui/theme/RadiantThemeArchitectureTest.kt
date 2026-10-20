package com.ahu.ahutong.ui.theme

import com.ahu.ahutong.data.model.AppUiTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadiantThemeArchitectureTest {
    @Test
    fun `themes share the Radiant home structure and retain themed bottom navigation`() {
        assertTrue(AppUiTheme.LIQUID_GLASS.usesLiquidGlass)
        assertTrue(AppUiTheme.RADIANT.usesLiquidGlass)

        val home = source("com/ahu/ahutong/ui/screen/main/Home.kt")
        val bottomBar = source("com/ahu/ahutong/ui/screen/BottomNavBar.kt")
        val main = source("com/ahu/ahutong/ui/screen/Main.kt")

        assertFalse(home.contains("val radiant = isRadiantUi"))
        assertTrue(home.contains("HomeWidgetRegistry.slotCountRadiant"))
        assertTrue(home.contains("HomeDateRow("))
        assertTrue(bottomBar.contains("if (isRadiantUi)"))
        assertTrue(bottomBar.contains("\"xuexiaotong\""))
        assertTrue(main.contains("animatedComposable(\"xuexiaotong\")"))
        assertTrue(main.contains("selectedRoute = currentRoute"))
    }

    @Test
    fun `xuexiaotong login uses controls that dispatch all app themes`() {
        val login = source("com/ahu/ahutong/ui/screen/xuexiaotong/XuexiaotongLoginScreen.kt")

        assertTrue(login.contains("AppCard("))
        assertTrue(login.contains("AppTextField("))
        assertTrue(login.contains("AppButton("))
        assertTrue(login.contains("AppCircularProgressIndicator("))
        assertFalse(login.contains("androidx.compose.material3.OutlinedTextField"))
        assertFalse(login.contains("androidx.compose.material3.Button"))
    }

    /**
     * 界面文件可能还在 :app，也可能已经搬进某个 feature 模块——因此按"每个模块的源集根"逐个查找，
     * 而不是写死 app 的路径：写死路径的断言会在文件搬家时以"文件不存在"的形式失效。
     */
    private fun source(relativePath: String): String {
        val root = repositoryRoot()
        val file = sourceRoots(root)
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?: error("找不到源文件：$relativePath")
        return file.readText()
    }

    private fun sourceRoots(root: File): List<File> =
        listOf(File(root, "app/src/main/java")) +
            listOf("core", "data", "feature").flatMap { parent ->
                File(root, parent).listFiles().orEmpty()
                    .sortedBy { it.name }
                    .map { File(it, "src/main/java") }
                    .filter { it.isDirectory }
            }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
