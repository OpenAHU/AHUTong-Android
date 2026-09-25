package com.ahu.ahutong.data.xuexiaotong

/**
 * 学习通的会话与同步：登录态、登录、静默重登、拉作业与课程进度。
 *
 * 界面只问「登录了吗」「用账号密码登一次」「同步一次作业」，
 * 协议与 Cookie 持久化都留在实现里（:app 的适配器，委托给 ChaoxingApi）。
 */
interface ChaoxingSession {

    /** 本机是否还留着可用的学习通会话。 */
    fun hasSession(): Boolean

    /** 只读提供给作业题目 WebView 的现有会话 Cookie。 */
    fun cookieHeader(): String

    /** 用账号密码登录；失败时抛异常（与迁移前一致）。 */
    suspend fun loginByPassword(account: String, password: String)

    /** 清掉本机会话（登出）。 */
    fun clearSession()

    /** 同步前先静默重登一次，避免会话过期导致整批失败。 */
    suspend fun silentRelogin()

    /** 拉取全部作业；[onProgress] 报告进度（已完成 / 总数 / 说明）。 */
    suspend fun syncWorks(onProgress: (Int, Int, String) -> Unit): List<Work>

    /** 拉取课程进度。 */
    suspend fun syncCourseProgress(onProgress: (Int, Int, String) -> Unit): List<CourseProgress>
}

