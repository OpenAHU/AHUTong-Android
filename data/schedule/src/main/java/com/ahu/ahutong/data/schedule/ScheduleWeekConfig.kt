package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.ScheduleConfigBean

/**
 * 学期与周次配置：本地优先、必要时问远端，写入要落盘。
 *
 * 界面需要回答的问题是「现在第几周、这学期什么时候开学、用户手选的周次存到哪」，
 * 而**怎么问**（教务页、校历接口）与**存到哪**（键名、观测日）留在实现里
 * （:app 的适配器，目前委托给 CurrentWeekResolver 与 AHUCache）。
 */
interface ScheduleWeekConfig {

    /** 缓存的学期标识；没有则为 null。 */
    fun cachedSemesterKey(): SemesterKey?

    /** 落盘的学年，用作学期标识不完整时的补全；没有则为 null。 */
    fun storedSchoolYear(): String?

    /** 今天是一周里的第几天（周一 = 1）。 */
    fun currentWeekDay(): Int

    /** 拼出学期标识（学年 + 学期号）。 */
    fun buildSemesterKey(schoolYear: String, schoolTerm: String): String

    /** 是否显示非本周课程——它参与配置，因此跟着一起给。 */
    fun isShowAllCourse(): Boolean

    /** 同步快照：只用本机缓存算出的当前配置，供首帧直接显示，避免先画第 1 周再跳。 */
    fun localSnapshot(): ScheduleConfigBean? = null

    /** 本地优先解析：本地可用且今天已确认过就直接用，否则问一次远端，再不行给默认值。 */
    suspend fun resolveLocalFirst(): ResolvedConfig

    /** 只问远端；失败或时间被调试覆盖时返回 null。 */
    suspend fun syncRemote(): ResolvedConfig?

    /** 保存学年与学期标识（用户在设置里手选学期时）。 */
    suspend fun saveSchoolYearAndTerm(schoolYear: String, semesterKey: String)

    /**
     * 保存开学日与「是否在学期内」。
     *
     * [observedOn] 是这次判断的日期，用于判断缓存是否已经过时——少了它就分不清
     * 「今天确认过」与「上周确认过」。
     */
    suspend fun saveTermPosition(
        schoolYear: String,
        schoolTerm: String,
        startTime: String,
        isInSemester: Boolean,
        observedOn: String
    )
}

