package com.ahu.ahutong.data.xuexiaotong

import kotlinx.coroutines.flow.StateFlow

enum class XuexiaotongSyncMode { LITE, FULL }

/** 应用级同步状态：页面销毁不影响任务，回页时据此恢复进度。 */
data class XuexiaotongSyncState(
    val worksRunning: Boolean = false,
    val worksDone: Int = 0,
    val worksTotal: Int = 0,
    val worksMessage: String = "",
    val worksRevision: Long = 0,
    val worksError: String? = null,
    val progressRunning: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val progressMessage: String = "",
    val progressRevision: Long = 0,
    val progressError: String? = null,
)

/**
 * 与页面生命周期解耦的学习通同步服务。
 *
 * 登录成功后的首次全量、手动刷新都在这里执行；ViewModel 只触发与观察。
 */
interface ChaoxingSyncService {
    val state: StateFlow<XuexiaotongSyncState>
    val fullSyncEnabled: StateFlow<Boolean>

    /** 首次连接时自动触发本学期的全量同步（每个账号一次）。 */
    fun onConnected()

    fun startWorksSync(mode: XuexiaotongSyncMode): Boolean

    fun startCourseProgressSync(): Boolean

    fun setFullSyncEnabled(enabled: Boolean)

    fun cancel()
}
