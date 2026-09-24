package com.ahu.ahutong.data.xuexiaotong

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val courseId: String = "",
    val clazzId: String = "",
    val cpi: String = "",
    val name: String = "",
    val href: String = "",
    /** 课程结束日期（毫秒）。null = 未知（按未结课处理，绝不误跳）。 */
    val endTs: Long? = null
) {
    /** 已结课：有明确结课标记，或结束日期早于今天（3 天宽限防时区/填写误差）。 */
    fun isEnded(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val end = endTs ?: return false
        return end < nowMillis - 3L * 24 * 3600 * 1000
    }
}

data class CourseKeys(
    val enc: String = "",
    val workEnc: String = ""
)

@Serializable
data class Work(
    val workId: String = "",
    val answerId: String = "",
    val courseId: String = "",
    val courseName: String = "",
    val title: String = "",
    val status: String = "",
    val detailUrl: String = "",
    val startTs: Long? = null,
    val endTs: Long? = null,
    val rawStart: String = "",
    val rawEnd: String = "",
    val colorBg: String = "#FFE0CC",
    val colorText: String = "#B3451E"
) {
    val isDone: Boolean
        get() = when (status) {
            "已完成", "待批阅", "已批改", "未批改", "待批改", "已提交" -> true
            else -> false
        }
    val remainMs: Long get() = (endTs ?: 0L) - System.currentTimeMillis()
}

@Serializable
data class CourseProgress(
    val courseId: String = "",
    val clazzId: String = "",
    val cpi: String = "",
    val name: String = "",
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val percent: Int = 0,
    val updatedAt: Long = 0L
)

@Serializable
data class CustomEvent(
    val id: String = "",
    val title: String = "",
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val startTs: Long = 0L,
    val endTs: Long = 0L,
    val done: Boolean = false,
    val colorBg: String = "#E8EAF6",
    val colorText: String = "#3F51B5"
)

@Serializable
data class RemindSetting(
    val enabled: Boolean = false,
    val leadMinutes: Int = 60,
    val onlyTodo: Boolean = true
)

data class ColorPair(val bg: String, val text: String)

object CourseColors {
    val PALETTE = listOf(
        ColorPair("#FFE8E0", "#C05621"),
        ColorPair("#FFF3D6", "#B7791F"),
        ColorPair("#FFF8DC", "#9C7B0A"),
        ColorPair("#DDF5E4", "#276749"),
        ColorPair("#E0F4F9", "#1E6FA3"),
        ColorPair("#E9E4FD", "#5B45A8"),
        ColorPair("#FDE4F0", "#8B4B93"),
        ColorPair("#DFF7F2", "#157A6E"),
        ColorPair("#F6EEE2", "#7A5C40"),
        ColorPair("#FFE2E5", "#B23A5E"),
        ColorPair("#E8F1F8", "#35597A"),
        ColorPair("#F2E8DD", "#8A6B4A"),
        ColorPair("#E6F4E6", "#2E6B3A"),
        ColorPair("#FDE8F2", "#A3486B")
    )

    fun byCourseId(courseId: String): ColorPair {
        val hash = courseId.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
        return PALETTE[hash % PALETTE.size]
    }
}