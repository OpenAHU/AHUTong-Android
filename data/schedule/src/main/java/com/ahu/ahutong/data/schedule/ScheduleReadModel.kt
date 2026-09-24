package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.Course
import com.ahu.ahutong.data.model.ScheduleConfigBean

/**
 * 后台组件（小组件、通知、提醒）用得到的课表只读事实。
 *
 * 它们**只许读缓存**：进程被系统拉起时没有登录态，也不该因为渲染一次就去登录或拉网络
 * （计划 P4 的验收标准）。写操作与网络读取都不在这个接口里——那些属于前台流程。
 */
interface ScheduleReadModel {

    /** 某学期的课表缓存；没有缓存返回 null。 */
    fun cachedSchedule(schoolTerm: String): List<Course>?

    /** 当前学期课表上次成功从教务系统获取的时间；没有记录返回 null。 */
    fun cachedScheduleFetchedAt(): Long?

    /** 学期开始时间的原始字符串（缓存里怎么存就怎么给）；没有则 null。 */
    fun schoolTermStartTime(schoolYear: String, schoolTerm: String): String?

    /** 当前学期文案（小组件标题用）；没有则 null。 */
    fun currentSchoolTerm(): String?

    /**
     * 缓存里的学期三元组：课表按 raw 存，开学时间按 schoolYear/schoolTerm 存，
     * 提醒排期要同时问这两样，因此给的是同一个值对象。
     */
    fun cachedSemesterKey(): SemesterKey?

    /**
     * 本机缓存的周次配置（读不到就给默认）。**非挂起**：后台回调线程不许等远端。
     * 等价于旧代码里的 resolveLocalConfig() ?: defaultConfig()。
     */
    fun cachedConfig(): ScheduleConfigBean
}
