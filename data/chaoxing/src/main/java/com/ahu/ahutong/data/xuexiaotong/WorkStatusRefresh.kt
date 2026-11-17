package com.ahu.ahutong.data.xuexiaotong

/**
 * 首页 Hero 的「单条校准」：只挑缓存里最紧急的一条未交作业，
 * 用一次课程作业列表请求刷新它的状态，其余数据不动。
 */
object WorkStatusRefresh {

    /** 未交、有截止时间、尚未逾期；取截止时间最早的一条。 */
    fun pickMostUrgent(works: List<Work>, nowMillis: Long): Work? =
        works.asSequence()
            .filter { !it.isDone }
            .filter { it.endTs != null && it.endTs > nowMillis }
            .minByOrNull { it.endTs!! }

    /**
     * 用列表页返回的作业覆盖缓存里的同一条：
     * 状态、标题、链接以新数据为准；起止时间优先保留详情页抓到的精确值，
     * 列表里的相对倒计时只在原值缺失时补位。
     */
    fun merge(existing: List<Work>, refreshed: Work): List<Work> {
        val index = existing.indexOfFirst {
            it.courseId == refreshed.courseId && it.workId == refreshed.workId
        }
        if (index < 0) return existing
        val old = existing[index]
        val endTs = refreshed.endTs ?: old.endTs
        val merged = old.copy(
            answerId = refreshed.answerId.ifBlank { old.answerId },
            title = refreshed.title.ifBlank { old.title },
            status = refreshed.status,
            detailUrl = refreshed.detailUrl.ifBlank { old.detailUrl },
            startTs = old.startTs?.takeIf { endTs == null || it <= endTs } ?: endTs,
            endTs = endTs
        )
        return existing.toMutableList().also { it[index] = merged }
    }
}
