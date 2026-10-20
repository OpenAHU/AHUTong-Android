package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.schedule.CustomCourse
import com.ahu.ahutong.data.schedule.CustomCourseStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * [CustomCourseStore] 的生产实现：JSON 存在当前账号的加密分箱里，键按学期隔离。
 *
 * 学期键统一补全成 `2024-2025-1`：教务缓存有时把学期存成裸的 `1`，
 * 补全规则只放在这里，读与写才可能落到同一个键上。
 */
@Singleton
class RepositoryCustomCourseStore @Inject constructor() : CustomCourseStore {

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    override fun currentSemesterKey(): String? = normalizeKey(AHUCache.getSchoolTerm())

    override fun currentCourses(): List<CustomCourse> =
        currentSemesterKey()?.let(::coursesFor).orEmpty()

    override fun coursesFor(semesterKey: String): List<CustomCourse> {
        val key = normalizeKey(semesterKey) ?: return emptyList()
        return runCatching { AHUCache.getCustomCourses(key) }
            .getOrDefault(emptyList())
            .mapNotNull { it.normalized() }
    }

    override suspend fun saveCurrent(courses: List<CustomCourse>) {
        val key = currentSemesterKey() ?: error("尚未确定当前学期，请先刷新一次课表")
        val normalized = courses.mapNotNull { it.normalized() }
        withContext(Dispatchers.IO) {
            AHUCache.saveCustomCourses(key, normalized)
            _revision.value += 1
        }
    }

    private fun normalizeKey(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.count { it == '-' } >= 2) return value
        val schoolYear = AHUCache.getSchoolYear()?.trim().orEmpty()
        return if (schoolYear.isEmpty()) value else "$schoolYear-$value"
    }
}
