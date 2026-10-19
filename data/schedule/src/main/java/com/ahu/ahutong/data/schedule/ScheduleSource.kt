package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.model.Course

/**
 * 课表数据的唯一入口。
 *
 * 界面只问四件事：有没有缓存、什么时候取的、要不要拉一次、内容变没变；
 * 协议（教务页解析）与缓存分层（内存 / 磁盘 / 快照比较）都留在实现里
 * （:app 的 AHURepository）。
 *
 * 接口里不出现 Context 与协议类型，因此 feature 的 ViewModel 可以在 JVM 上被 fake 驱动。
 */
interface ScheduleSource {

    /** 调试用的假数据开关：为真时不必登录也能看到课表。 */
    fun usesMockData(): Boolean

    /** 缓存里的课表；没有则为 null。 */
    fun cached(): List<Course>?

    /** 缓存取到的时间（epoch millis）；没有则为 null。 */
    fun fetchedAt(): Long?

    /** 拉取课表；[isRefresh] 为真时强制绕过缓存。 */
    suspend fun fetch(isRefresh: Boolean): AhuResult<List<Course>>

    /** 后台刷新：只取一次，结果里带着「内容是否变化」。 */
    suspend fun refreshCache(): AhuResult<ScheduleRefreshResult>

    /** 下一学期的课表。 */
    suspend fun next(isRefresh: Boolean): AhuResult<List<Course>>

    /** 可浏览的真实学期目录；上游未提供完整目录时可能只有部分历史学期。 */
    suspend fun semesters(): AhuResult<List<ScheduleSemester>>

    /** 按真实学期 ID 浏览课表，不改变当前课表、学期及提醒使用的缓存。 */
    suspend fun semester(semesterId: Int): AhuResult<List<Course>>
}

