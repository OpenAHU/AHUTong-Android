package com.ahu.ahutong.di

import com.ahu.ahutong.core.storage.SettingsStore
import com.ahu.ahutong.core.storage.PaymentKeyboardSetting
import com.ahu.ahutong.core.storage.CourseReminderSettings
import com.ahu.ahutong.data.commits.AhuCommitStatsSource
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.ui.state.CommitStatsSource
import com.ahu.ahutong.personalization.settings.BehaviorPredictionSettings
import com.ahu.ahutong.personalization.settings.PersonalizationSettings
import com.ahu.ahutong.ui.state.AndroidAppUpdateGateway
import com.ahu.ahutong.ui.state.AndroidCourseReminderControl
import com.ahu.ahutong.core.common.CourseReminderControl
import com.ahu.ahutong.ui.state.AppDataReset
import com.ahu.ahutong.ui.state.AppUpdateGateway
import com.ahu.ahutong.ui.state.DeviceDataReset
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 设置 feature 的接线：接口在 :core:storage 与 :data:personalization，实现留在 :app。
 *
 * 只有组合根同时认识两边——设置 feature 因此看不见 DataStore、也看不见端侧运行时。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsWiringModule {

    @Binds
    @Singleton
    abstract fun bindSettingsStore(implementation: PreferencesManager): SettingsStore

    /** 贡献名单只读公开的提交统计；HTTP 细节留在 :app。 */
    @Binds
    @Singleton
    abstract fun bindCommitStatsSource(
        implementation: AhuCommitStatsSource
    ): CommitStatsSource

    /** 付费页只问"用内置键盘吗"：同一条设置，两种问法。 */
    @Binds
    @Singleton
    abstract fun bindPaymentKeyboardSetting(
        implementation: PreferencesManager
    ): PaymentKeyboardSetting

    /** 后台只问两条提醒设置：同一条设置，窄的一种问法。 */
    @Binds
    @Singleton
    abstract fun bindCourseReminderSettings(
        implementation: PreferencesManager
    ): CourseReminderSettings

    @Binds
    @Singleton
    abstract fun bindPersonalizationSettings(
        implementation: BehaviorPredictionSettings
    ): PersonalizationSettings

    /** 提醒的排期属于后台职责，设置 feature 只见命令。 */
    @Binds
    @Singleton
    abstract fun bindCourseReminderControl(
        implementation: AndroidCourseReminderControl
    ): CourseReminderControl

    /** 「清除所有数据」：一串有顺序的本机复位，定义在 feature，动作在 :app。 */
    @Binds
    @Singleton
    abstract fun bindAppDataReset(implementation: DeviceDataReset): AppDataReset

    /** 版本名与更新说明：界面只读结果。 */
    @Binds
    @Singleton
    abstract fun bindAppUpdateGateway(
        implementation: AndroidAppUpdateGateway
    ): AppUpdateGateway
}
