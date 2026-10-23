package com.ahu.ahutong.data.repository

enum class RepositorySourceHealth { UNTESTED, TESTING, AVAILABLE, UNAVAILABLE }

const val REPOSITORY_FALLBACK_SOURCE_ID = "muxyang-fallback"

fun areRepositoryPrimarySourcesUnavailable(
    sources: List<RepositoryAccelerationSource>,
    health: Map<String, RepositorySourceHealth>
): Boolean {
    val primary = sources.filterNot { it.isFallback }
    return primary.isNotEmpty() && primary.all { health[it.id] == RepositorySourceHealth.UNAVAILABLE }
}

/** 自动切换只影响本次有效源，不能覆盖用户保存的常规源偏好。 */
fun resolveRepositoryAccelerationSourceId(
    preferredId: String?,
    sources: List<RepositoryAccelerationSource>,
    health: Map<String, RepositorySourceHealth>
): String {
    if (areRepositoryPrimarySourcesUnavailable(sources, health)) {
        sources.firstOrNull { it.isFallback }?.let { return it.id }
    }
    return sources.firstOrNull { it.id == preferredId && !it.isFallback }?.id
        ?: sources.firstOrNull { !it.isFallback }?.id.orEmpty()
}
