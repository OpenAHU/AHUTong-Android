package com.ahu.ahutong.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 模块边界的自动门禁（零豁免），对应 AGENTS.md §2 的硬约束。
 *
 * 规则定义在 [RULES]，严禁通过豁免清单或放宽 exceptPrefixes 绕过。
 */
class ModuleBoundaryTest {

    @Test
    fun `no boundary violation across all governed modules`() {
        val offenders = RULES.flatMap { rule ->
            violations(rule).map { file -> rule.id + "  " + file }
        }

        assertTrue(
            offenders.isEmpty(),
            "检测到模块边界违规，请修正依赖方向：\n" +
                offenders.joinToString("\n") +
                "\n规则说明见 AGENTS.md §2。"
        )
    }

    @Test
    fun `all settings modules are governed and features never depend on sibling features`() {
        val root = repositoryRoot()
        val settingsModules = Regex("""include\s*\(([^)]+)\)""")
            .findAll(File(root, "settings.gradle.kts").readText())
            .flatMap { call ->
                Regex(""""(:[^"]+)"""").findAll(call.groupValues[1]).map { it.groupValues[1] }
            }
            .map { it.removePrefix(":").replace(':', '/') }
            .filterNot { it == "core/plugin-platform" }
            .toSet()

        val uncovered = (settingsModules - SOURCE_MODULES.toSet()).sorted()
        assertTrue(
            uncovered.isEmpty(),
            "以下 settings.gradle.kts 模块未纳入 ModuleBoundaryTest.SOURCE_MODULES：\n" +
                uncovered.joinToString("\n")
        )

        val crossFeatureDeps = settingsModules
            .filter { it.startsWith("feature/") }
            .flatMap { module ->
                val buildFile = File(root, "$module/build.gradle.kts")
                if (!buildFile.isFile) emptyList()
                else {
                    val text = buildFile.readText()
                    Regex("""project\(":feature:([A-Za-z0-9_.-]+)"\)""")
                        .findAll(text)
                        .map { "$module -> :feature:${it.groupValues[1]}" }
                        .toList()
                }
            }
            .sorted()

        assertTrue(
            crossFeatureDeps.isEmpty(),
            "检测到 feature 模块间横向依赖（AGENTS.md §2 禁止）：\n" +
                crossFeatureDeps.joinToString("\n")
        )
    }

    private data class BoundaryRule(
        val id: String,
        val packagePrefix: String,
        val forbiddenImportPrefixes: List<String>,
        val forbiddenContentRegex: Regex? = null,
        val exceptPrefixes: List<String> = emptyList(),
        /** 限定只在这些模块内生效（相对仓库根，例如 core/common）；null 表示所有被扫描的模块。 */
        val modules: List<String>? = null
    )

    private data class SourceFile(
        val relativePath: String,
        val packagePath: String,
        val file: File
    )

    private fun violations(rule: BoundaryRule): List<String> =
        sourceFiles()
            .filter { it.packagePath.startsWith(rule.packagePrefix) }
            .filter { source ->
                rule.modules == null || rule.modules.any { source.relativePath.startsWith(it + "/") }
            }
            .filterNot { source -> rule.exceptPrefixes.any { source.packagePath.startsWith(it) } }
            .filter { source -> rule.matches(source.file) }
            .map { it.relativePath }
            .sorted()

    /**
     * 两条判定：import 前缀（常规分层规则）与整文件内容正则（用于"某种构造只能出现在一处"这类约束）。
     * 内容正则允许跨行匹配，便于识别 OkHttpClient\n.Builder() 这种换行写法。
     */
    private fun BoundaryRule.matches(file: File): Boolean {
        val text = file.readText()
        val importHit = text.lineSequence().any { line ->
            val trimmed = line.trim()
            forbiddenImportPrefixes.any { trimmed.startsWith(it) }
        }
        return importHit || forbiddenContentRegex?.containsMatchIn(text) == true
    }

    private fun sourceFiles(): List<SourceFile> {
        val repositoryRoot = repositoryRoot()
        return SOURCE_MODULES.flatMap { module ->
            SOURCE_SUBPATHS.flatMap { subpath ->
                val moduleSourceRoot = File(repositoryRoot, "$module/$subpath")
                if (!moduleSourceRoot.isDirectory) emptyList()
                else {
                    moduleSourceRoot.walkTopDown()
                        .filter { it.isFile && it.extension in SOURCE_EXTENSIONS }
                        .map { file ->
                            val packagePath = file.relativeTo(moduleSourceRoot).invariantSeparatorsPath
                            SourceFile(
                                relativePath = "$module/$subpath/$packagePath",
                                packagePath = packagePath,
                                file = file
                            )
                        }
                        .toList()
                }
            }
        }
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    private companion object {
        val SOURCE_SUBPATHS = listOf("src/main/java", "src/main/kotlin")
        val SOURCE_EXTENSIONS = setOf("kt", "java")
        val SOURCE_MODULES = listOf(
            "app",
            "core/model",
            "core/common",
            "core/designsystem",
            "core/network",
            "core/storage",
            "core/auth",
            "core/plugin-api",
            "data/personalization",
            "data/repository-index",
            "data/schedule",
            "data/grade",
            "data/chaoxing",
            "data/recharge",
            "data/update",
            "background",
            "feature/repository-index",
            "feature/settings",
            "feature/schedule",
            "feature/grade",
            "feature/xuexiaotong",
            "feature/recharge",
            "feature/circle"
        )

        val RULES = listOf(
            BoundaryRule(
                id = "R1-data-no-ui",
                packagePrefix = "com/ahu/ahutong/data/",
                forbiddenImportPrefixes = listOf("import com.ahu.ahutong.ui.")
            ),
            BoundaryRule(
                id = "R2-ui-no-crawler-api",
                packagePrefix = "com/ahu/ahutong/ui/",
                forbiddenImportPrefixes = listOf("import com.ahu.ahutong.data.crawler.api.")
            ),
            BoundaryRule(
                id = "R3-utils-leaf",
                packagePrefix = "com/ahu/ahutong/utils/",
                // 只禁"实现住在哪里"，不按 `data.` 整包禁止：模型与传输层已经搬进 :core:model /
                // :core:network，整包禁止会把合法依赖一起拦下（Navigation.kt 就是这么被误报的）。
                forbiddenImportPrefixes = listOf(
                    "import com.ahu.ahutong.data.AHURepository",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui."
                )
            ),
            BoundaryRule(
                id = "R4-net-no-business",
                packagePrefix = "com/ahu/ahutong/data/crawler/net/",
                forbiddenImportPrefixes = listOf(
                    // 末尾不带点：带点只能匹配到嵌套类，类本身反而漏网（这一条曾经永远匹配不上）。
                    "import com.ahu.ahutong.data.AHURepository",
                    "import com.ahu.ahutong.data.dao."
                )
            ),
            BoundaryRule(
                id = "R5-personalization-no-ui",
                packagePrefix = "com/ahu/ahutong/personalization/",
                // 界面本体（屏与 ViewModel）住在 :app；设计系统（ui.components / ui.utils / ui.shape…）
                // 已经搬进 :core:designsystem，个性化层的 Compose 宿主依赖它是合法的。
                forbiddenImportPrefixes = listOf(
                    "import com.ahu.ahutong.ui.screen.",
                    "import com.ahu.ahutong.ui.state."
                )
            ),
            BoundaryRule(
                id = "R6-logging-single-site",
                packagePrefix = "com/ahu/ahutong/",
                forbiddenImportPrefixes = listOf("import okhttp3.logging."),
                exceptPrefixes = listOf("com/ahu/ahutong/data/network/")
            ),
            BoundaryRule(
                id = "R7-campus-http-assembly",
                packagePrefix = "com/ahu/ahutong/",
                forbiddenImportPrefixes = listOf(
                    "import com.ahu.ahutong.data.crawler.net.AutoLoginInterceptor",
                    "import com.ahu.ahutong.data.crawler.net.TokenAuthenticator"
                ),
                exceptPrefixes = listOf(
                    "com/ahu/ahutong/data/network/",
                    "com/ahu/ahutong/data/crawler/net/"
                )
            ),
            BoundaryRule(
                id = "R8-single-client-factory",
                packagePrefix = "com/ahu/ahutong/",
                forbiddenImportPrefixes = emptyList(),
                forbiddenContentRegex = Regex("OkHttpClient\\s*\\.\\s*Builder\\s*\\("),
                exceptPrefixes = listOf("com/ahu/ahutong/data/network/")
            ),
            BoundaryRule(
                id = "R9-single-retrofit-factory",
                packagePrefix = "com/ahu/ahutong/",
                forbiddenImportPrefixes = emptyList(),
                forbiddenContentRegex = Regex("Retrofit\\s*\\.\\s*Builder\\s*\\("),
                exceptPrefixes = listOf("com/ahu/ahutong/data/network/")
            ),
            BoundaryRule(
                id = "R10-core-common-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/common"),
                forbiddenImportPrefixes = listOf(
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R11-designsystem-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/designsystem"),
                forbiddenImportPrefixes = listOf(
                    // 设计系统只认识主题模型与 Compose：界面之外的东西一律不许伸手。
                    "import com.ahu.ahutong.ui.screen.",
                    "import com.ahu.ahutong.ui.state.",
                    "import com.ahu.ahutong.ui.component.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R12-personalization-api-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/personalization"),
                forbiddenImportPrefixes = listOf(
                    // 这里放的是个性化层的对外词汇与接口：实现（运行时/训练/推理/存储/评估）与界面都不许被它依赖。
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R13-repository-index-api-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/repository-index"),
                forbiddenImportPrefixes = listOf(
                    // 对外接口与数据结构：实现（管理器/下载/缓存）与界面都不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R14-feature-no-app-internals",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/repository-index"),
                forbiddenImportPrefixes = listOf(
                    // feature 只认识 core / data 的接口与设计系统；app 内部的实现一概不许伸手。
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R15-network-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/network"),
                forbiddenImportPrefixes = listOf(
                    // 传输层只认识 HTTP 与会话接缝：业务实现（仓储 / 缓存 / 会话 / 凭据 / 界面）一概不许伸手。
                    "import com.ahu.ahutong.data.AHURepository",
                    "import com.ahu.ahutong.data.dao.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.crawler.api.",
                    "import com.ahu.ahutong.data.crawler.manager.",
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R16-storage-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/storage"),
                forbiddenImportPrefixes = listOf(
                    // 设置档位只认识设置的值类型（:core:model）与协程：界面、个性化与设备侧实现都不许伸手。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R17-settings-feature-no-app-internals",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/settings"),
                forbiddenImportPrefixes = listOf(
                    // feature 只认识 core / data 的接口与设计系统；app 内部的实现一概不许伸手。
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R18-auth-api-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/auth"),
                forbiddenImportPrefixes = listOf(
                    // 会话与凭据的接口只认识 core 层的类型：实现（设备侧存储、仓库登录）不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 安全存储的原语（SecureStorage）现在住在本模块里，因此不能按 data.security 整包禁止；
                    // 仍留在 :app 的设备侧装配按实现类点名。
                    "import com.ahu.ahutong.data.security.SecureBoxStore",
                    "import com.ahu.ahutong.data.security.SecureBoxStoreCore",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R19-schedule-api-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/schedule"),
                forbiddenImportPrefixes = listOf(
                    // 课表的数据与策略只认识 core 层的类型：协议、缓存、界面都不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R20-schedule-feature-no-app-internals",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/schedule"),
                forbiddenImportPrefixes = listOf(
                    // feature 只认识 core / data 的接口；app 内部的实现一概不许伸手。
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.schedule.CurrentWeekResolver",
                    // 会话与凭据的**接口**现在住在 :core:auth（包名仍是 data.session），
                    // 所以这里按实现类点名，而不是按包名——按包名会把 feature 合法依赖的接口一起禁掉。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R21-grade-api-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/grade"),
                forbiddenImportPrefixes = listOf(
                    // 成绩的数据端口只认识 core 层的类型：协议、缓存、界面都不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R22-grade-feature-no-app-internals",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/grade"),
                forbiddenImportPrefixes = listOf(
                    // feature 只认识 core / data 的接口；app 内部的实现一概不许伸手。
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.data.schedule.CurrentWeekResolver",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R23-chaoxing-data-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/chaoxing"),
                forbiddenImportPrefixes = listOf(
                    // 学习通的数据层只认识 core 层的类型：界面、UI 状态、提醒与缓存都不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R24-xuexiaotong-feature-no-app-internals",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/xuexiaotong"),
                forbiddenImportPrefixes = listOf(
                    // feature 只认识 core / data 的接口；app 内部的实现一概不许伸手。
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    // 提醒必须走 ChaoxingReminders 端口——VM 原先直接拿 ReminderScheduler 与 Context。
                    "import com.ahu.ahutong.reminder.",
                    // 三个端口的 :app 适配器：feature 只该看见 :data:chaoxing 里的接口。
                    "import com.ahu.ahutong.ui.state.AppChaoxingSession",
                    "import com.ahu.ahutong.ui.state.AppChaoxingStore",
                    "import com.ahu.ahutong.ui.state.AppChaoxingReminders",
                    // 具体存储也不许进 feature：凭据的保存属于登录（端口实现里做），
                    // 界面只调 ViewModel。
                    "import com.ahu.ahutong.data.xuexiaotong.Store",
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    // 会话与凭据的接口住在 :core:auth（包名仍是 data.session），所以这里按实现类点名，
                    // 而不是按包名——按包名会把接口一起禁掉，那不是这些规则想表达的约束。
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R25-recharge-data-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/recharge"),
                forbiddenImportPrefixes = listOf(
                    // 充值域的端口与协议词汇只认识 core 层：仓库、缓存、会话、界面都不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    // 协议客户端与抓取层仍在 :app；本模块只搬来了 ycard 的 DTO 与签名辅助，
                    // 它们的包名（data.crawler.model.ycard / data.crawler.utils）因此不能整包禁止。
                    "import com.ahu.ahutong.data.crawler.api.",
                    "import com.ahu.ahutong.data.crawler.configs.",
                    "import com.ahu.ahutong.data.crawler.login.",
                    "import com.ahu.ahutong.data.crawler.manager.",
                    "import com.ahu.ahutong.data.crawler.net.",
                    "import com.ahu.ahutong.data.crawler.model.jwxt.",
                    "import com.ahu.ahutong.data.crawler.model.adwnh.",
                    "import com.ahu.ahutong.data.crawler.utils.GpaRankHtmlParser",
                    "import com.ahu.ahutong.data.crawler.CrawlerDataSource",
                    "import com.ahu.ahutong.data.crawler.SdkDataSource",
                    "import com.ahu.ahutong.data.crawler.GradeMapper",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R26-recharge-feature-no-app-internals",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/recharge"),
                forbiddenImportPrefixes = listOf(
                    // feature 只认识 core / data 的接口；支付 SDK、协议客户端与 app 内部实现一概不许伸手。
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.personalization.runtime.",
                    "import com.ahu.ahutong.personalization.training.",
                    "import com.ahu.ahutong.personalization.inference.",
                    "import com.ahu.ahutong.personalization.storage.",
                    "import com.ahu.ahutong.personalization.evaluation.",
                    "import com.ahu.ahutong.personalization.ui.",
                    "import com.ahu.ahutong.data.AHURepository",
                    "import com.ahu.ahutong.data.dao.",
                    // 协议客户端与抓取层仍在 :app；ycard 的 DTO 与签名辅助已随 :data:recharge 出去，
                    // 所以按 app 侧的包与类点名，而不是把 data.crawler 整包禁掉。
                    "import com.ahu.ahutong.data.crawler.api.",
                    "import com.ahu.ahutong.data.crawler.configs.",
                    "import com.ahu.ahutong.data.crawler.login.",
                    "import com.ahu.ahutong.data.crawler.manager.",
                    "import com.ahu.ahutong.data.crawler.net.",
                    "import com.ahu.ahutong.data.crawler.model.jwxt.",
                    "import com.ahu.ahutong.data.crawler.model.adwnh.",
                    "import com.ahu.ahutong.data.crawler.utils.GpaRankHtmlParser",
                    "import com.ahu.ahutong.data.crawler.CrawlerDataSource",
                    "import com.ahu.ahutong.data.crawler.SdkDataSource",
                    "import com.ahu.ahutong.data.crawler.GradeMapper",
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R27-update-data-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("data/update"),
                forbiddenImportPrefixes = listOf(
                    // 更新域只认识 core 层与自己的元数据：界面、仓库、会话、缓存都不许被它依赖。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R28-background-read-only",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("background"),
                forbiddenImportPrefixes = listOf(
                    // 后台组件只许读：界面、仓库、协议客户端、会话实现、原生 SDK 一概不许伸手。
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.server.",
                    "import com.ahu.ahutong.data.session.SessionStore",
                    "import com.ahu.ahutong.data.session.RepositoryAhuSession",
                    "import com.ahu.ahutong.data.session.DefaultAhuSession",
                    "import com.ahu.ahutong.data.session.SecureCredentialVault",
                    "import com.ahu.ahutong.data.session.RepositorySessionExpiryHook",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication",
                    // 写入口与"会问远端"的接口同样不许伸手：后台要么读缓存，要么读窄开关。
                    "import com.ahu.ahutong.core.storage.SettingsStore",
                    "import com.ahu.ahutong.data.schedule.ScheduleWeekConfig",
                    // 学习通客户端同样不许：后台要的是"提醒快照"，不是"再登一次"。
                    "import com.ahu.ahutong.data.xuexiaotong.ChaoxingApi",
                    // 宽存储对象也不许：后台只拿 ChaoxingReminderStore 那个窄视图。
                    "import com.ahu.ahutong.data.xuexiaotong.Store"
                ),
                // 本地优先解析在缓存未经今天确认时会问一次远端并写缓存。
                // 小组件冷启动正是从这条路上滑出去过一次（P4 审查发现的 P1），所以按名字盯住它。
                // 后六个是凭据与登录动作：后台的提醒记账（saveRemindedMap）是它自己的事，
                // 但保存 Cookie / 账号密码、静默重登永远不是。
                forbiddenContentRegex = Regex(
                    "resolveLocalFirst|syncRemoteConfig|saveCookie|saveCredential|clearCredential|" +
                        "saveKeepLogin|loginByPassword|silentRelogin"
                )
            ),
            BoundaryRule(
                id = "R29-plugin-api-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/plugin-api"),
                forbiddenImportPrefixes = listOf(
                    // 插件契约是叶子：不认识任何业务层（界面/数据/会话/个性化/原生 SDK）
                    "import com.ahu.ahutong.ui.screen.",
                    "import com.ahu.ahutong.ui.state.",
                    "import com.ahu.ahutong.data.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R30-circle-plugin-contract-only",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("feature/circle"),
                forbiddenImportPrefixes = listOf(
                    // 插件只许认契约与设计系统：app 内部实现、各 data 域、会话凭据一概不许伸手
                    "import com.ahu.ahutong.ui.screen.",
                    "import com.ahu.ahutong.ui.state.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.session.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.data.schedule.",
                    "import com.ahu.ahutong.data.grade.",
                    "import com.ahu.ahutong.data.xuexiaotong.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            ),
            BoundaryRule(
                id = "R31-model-leaf",
                packagePrefix = "com/ahu/ahutong/",
                modules = listOf("core/model"),
                forbiddenImportPrefixes = listOf(
                    "import android.",
                    "import androidx.compose.",
                    "import androidx.lifecycle.",
                    "import androidx.work.",
                    "import androidx.room.",
                    "import okhttp3.",
                    "import retrofit2.",
                    "import com.ahu.ahutong.ui.",
                    "import com.ahu.ahutong.personalization.",
                    "import com.ahu.ahutong.sdk.",
                    "import com.ahu.ahutong.data.dao.",
                    "import com.ahu.ahutong.data.repository.",
                    "import com.ahu.ahutong.data.crawler.",
                    "import com.ahu.ahutong.data.session.",
                    "import com.ahu.ahutong.data.security.",
                    "import com.ahu.ahutong.appwidget.",
                    "import com.ahu.ahutong.notification.",
                    "import com.ahu.ahutong.reminder.",
                    "import com.ahu.ahutong.AHUApplication"
                )
            )
        )
    }
}
