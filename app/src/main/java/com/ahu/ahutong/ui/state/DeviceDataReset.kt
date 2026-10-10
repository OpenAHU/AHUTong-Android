package com.ahu.ahutong.ui.state

import android.content.Context
import com.ahu.ahutong.core.common.AppEnvironmentHolder
import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.data.crawler.manager.CookieManager
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.notice.CampusNoticeRepository
import com.ahu.ahutong.data.session.AhuSession
import com.ahu.ahutong.data.update.ApkUpdateSkipStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.PersistentCookieJar
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.notification.CourseReminderScheduler
import com.ahu.ahutong.notification.CampusNoticeNotifier
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.sdk.RustSDK
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppDataReset] 的生产实现：把设置页里那串复位原样搬过来。
 *
 * 顺序与迁移前逐字一致，因为次序是有意的：先撤掉提醒排期，再清设置与缓存，
 * 最后把登录态标成过期——反过来的话，后台任务可能在清空之后又把内容写回去。
 * 这里还包含了原先藏在 `MainViewModel.logout()` 里的两步（业务缓存登出 + WebView Cookie），
 * 以及第一方 Cookie jar 的 `clear()` / `clearSession()` 两个不同语义的清理。
 */
@Singleton
class DeviceDataReset @Inject constructor(
    private val behavior: BehaviorPredictionRuntime,
    private val settings: SettingsStore,
    private val session: AhuSession,
    private val updateSkipStore: ApkUpdateSkipStore,
    private val chaoxingSession: ChaoxingSession,
    private val chaoxingReminders: ChaoxingReminders
) : AppDataReset {

    private val context: Context get() = AppEnvironmentHolder.context()

    override suspend fun clearAll() {
        CourseReminderScheduler.cancel(context).join()
        CampusNoticeNotifier.cancelAll(context)
        chaoxingReminders.cancelAll()
        CampusNoticeRepository.clearAll()
        settings.clearAll()
        CourseReminderScheduler.clearDeliveryHistory(context)
        updateSkipStore.clear()
        behavior.logoutAndClear()
        com.ahu.ahutong.data.session.SavedAccounts.clear()
        session.signOut()
        AHUCache.logout()
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        // 学习通：独立 SharedPreferences（登录 Cookie、加密凭据、课程/作业/进度缓存），
        // AHUCache 的 MMKV 清理覆盖不到——隐私协议要求「清除数据」时一并清空。
        // clearSession 撤内存中的 CookieJar 与在途请求；后两行清磁盘上的两个 prefs 文件。
        chaoxingSession.clearSession()
        Store.clearAll(context)
        PersistentCookieJar.clearPersisted(context)
        AHUCache.clearAll()
        RustSDK.initSafe("")
        CookieManager.cookieJar.clear()
        CookieManager.cookieJar.clearSession()
    }
}
