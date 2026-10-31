package com.ahu.ahutong.data.notes

/** 笔记科目：由课表课程名自动创建，或在相机页手动新建。 */
data class NoteSubject(
    val id: String,
    val name: String,
    val createdAt: Long,
    val order: Int
)

/** 照片归属方式：自动按课表、用户手动指定、未归档。 */
enum class NoteSource { AUTO, MANUAL, UNFILED }

/**
 * 一张笔记照片的元数据。
 *
 * [courseName] / [windowStart] / [windowEnd] 只在自动归档时写入，用于回溯"当时命中了哪节课"。
 */
data class NotePhoto(
    val id: String,
    val subjectId: String,
    val fileName: String,
    val takenAt: Long,
    val sizeBytes: Long,
    val source: NoteSource = NoteSource.UNFILED,
    val courseName: String? = null,
    val windowStart: Int? = null,
    val windowEnd: Int? = null
)

internal const val UNFILED_SUBJECT_ID = "unfiled"
internal const val UNFILED_SUBJECT_NAME = "未归档"
