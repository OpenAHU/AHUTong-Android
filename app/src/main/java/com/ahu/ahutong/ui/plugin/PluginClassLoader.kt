package com.ahu.ahutong.ui.plugin

import dalvik.system.DexClassLoader

/**
 * 插件类加载器（方案 A：插件自带全部依赖）。
 *
 * 三类解析规则：
 * 1. 契约 `com.ahu.ahutong.core.plugin.*` **必须来自宿主** —— 否则 `instance is AhuPlugin` 恒为 false；
 * 2. JDK / Android framework / org.json 走引导类加载器（插件 APK 里本来也没有）；
 * 3. 其余一律 **child-first** —— 插件自带的 Compose / Kotlin / okhttp 优先，避免与宿主那份混用。
 *
 * 找不到时回退父加载器，作为"插件漏打某个类"的容错。
 */
internal class PluginClassLoader(
    apkPath: String,
    nativeLibDir: String?,
    parent: ClassLoader
) : DexClassLoader(apkPath, null, nativeLibDir, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (isContractClass(name) || isBootClass(name)) {
            return super.loadClass(name, resolve)
        }

        findLoadedClass(name)?.let { loaded ->
            if (resolve) resolveClass(loaded)
            return loaded
        }

        return try {
            val c = findClass(name)
            if (resolve) resolveClass(c)
            c
        } catch (_: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
    }

    private fun isContractClass(name: String): Boolean = name.startsWith(CONTRACT_PREFIX)

    private fun isBootClass(name: String): Boolean = BOOT_PREFIXES.any(name::startsWith)

    companion object {
        const val CONTRACT_PREFIX = "com.ahu.ahutong.core.plugin."

        private val BOOT_PREFIXES = listOf(
            "java.", "javax.", "android.", "dalvik.",
            "org.json.", "org.xmlpull.", "libcore.", "sun."
        )
    }
}
