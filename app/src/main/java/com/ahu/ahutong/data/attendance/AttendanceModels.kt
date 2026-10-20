package com.ahu.ahutong.data.attendance

/** 教务考勤（kqcard）记录。抓拍照片不在模型里，也不会被请求。 */
data class AttendanceRecord(
    val courseName: String,
    val courseCode: String,
    val teacher: String,
    val room: String,
    val building: String,
    val section: String,
    val classDate: String,
    val startTime: String,
    val endTime: String,
    val punchTime: String,
    val status: AttendanceStatus,
    val canAppeal: Boolean
)

enum class AttendanceStatus(val code: Int, val label: String) {
    NORMAL(1, "正常"),
    LATE(2, "迟到"),
    ABSENT(3, "缺勤"),
    LEAVE(5, "请假"),
    OTHER(0, "其他");

    companion object {
        fun of(code: Int): AttendanceStatus = entries.firstOrNull { it.code == code } ?: OTHER
    }
}

data class AttendancePage(
    val totalCount: Int,
    val records: List<AttendanceRecord>
)

class AttendanceAuthException(message: String) : Exception(message)
class AttendanceException(message: String) : Exception(message)
