package com.ahu.ahutong.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryIndexRefreshPolicyTest {
    @Test
    fun `fresh compatible index is reused even when UI observes progress`() {
        val now = 50_000_000L

        assertTrue(
            RepositoryIndexRefreshPolicy.canReuse(
                cachedAtMillis = now - RepositoryIndexRefreshPolicy.AUTO_REFRESH_INTERVAL_MS + 1L,
                cachedVersion = 7,
                expectedVersion = 7,
                hasRootContents = true,
                nowMillis = now
            )
        )
    }

    @Test
    fun `stale incompatible or incomplete index is rebuilt`() {
        val now = 50_000_000L
        val staleAt = now - RepositoryIndexRefreshPolicy.AUTO_REFRESH_INTERVAL_MS

        assertFalse(RepositoryIndexRefreshPolicy.canReuse(staleAt, 7, 7, true, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now, 6, 7, true, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now, 7, 7, false, now))
        assertFalse(RepositoryIndexRefreshPolicy.canReuse(now + 1L, 7, 7, true, now))
    }

    @Test
    fun `an unchanged server timestamp reuses a complete local index`() {
        assertTrue(RepositoryIndexRefreshPolicy.canReuseServerIndex(123L, 123L, true))
        assertFalse(RepositoryIndexRefreshPolicy.canReuseServerIndex(124L, 123L, true))
        assertFalse(RepositoryIndexRefreshPolicy.canReuseServerIndex(123L, 123L, false))
        assertFalse(RepositoryIndexRefreshPolicy.canReuseServerIndex(0L, 0L, true))
    }

    @Test
    fun `index construction does not eagerly fetch every LFS candidate`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/data/repository/RepositoryManager.kt"
        ).readText()

        assertFalse(source.contains("resolveGitLfsDisplaySizes"))
        assertTrue(source.contains("size = entry.size"))
    }

    @Test
    fun `cold root renders before the full repository index finishes`() {
        val source = File(
            repositoryRoot(),
            "app/src/main/java/com/ahu/ahutong/data/repository/RepositoryManager.kt"
        ).readText()
        val getContents = source.substring(
            source.indexOf("suspend fun getContents"),
            source.indexOf("suspend fun warmUpAllContentCaches")
        )

        val immediateRootReturn = getContents.indexOf("fallbackRootItems?.let { return@withContext it }")
        val indexWarmUp = getContents.indexOf("warmUpAllContentCaches(")
        assertTrue(immediateRootReturn in 0 until indexWarmUp)
    }

    @Test
    fun `repository cache parsing stays off the composition thread`() {
        val source = moduleSource("com/ahu/ahutong/ui/state/RepositoryViewModel.kt")
        val stateGetter = source.substring(
            source.indexOf("fun getInitialDirectoryState"),
            source.indexOf("fun getSharedState")
        )

        assertFalse(stateGetter.contains("RepositoryManager.getCachedContents"))
        // 调度器现在由构造参数注入（测试可以换成测试调度器），但"离开组合线程"这条不变。
        assertTrue(source.contains("withContext(ioDispatcher) { refreshDownloadedSet() }"))
        assertTrue(source.contains("val resolvedState = withContext(ioDispatcher)"))
    }

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    /** ViewModel 已随 feature 抽到 :feature:repository-index，按源集根逐个查找。 */
    private fun moduleSource(relativePath: String): String {
        val root = repositoryRoot()
        val file = SOURCE_ROOTS.map { File(root, it + relativePath) }.firstOrNull { it.isFile }
            ?: error("找不到源文件：$relativePath")
        return file.readText()
    }

    private companion object {
        val SOURCE_ROOTS = listOf(
            "app/src/main/java/",
            "feature/repository-index/src/main/java/"
        )
    }
}
