package com.ahu.ahutong.di

import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSyncService
import com.ahu.ahutong.data.xuexiaotong.XuexiaotongSemesterRangeProvider
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminderStore
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.schedule.AppXuexiaotongSemesterRangeProvider
import com.ahu.ahutong.ui.state.AppChaoxingReminders
import com.ahu.ahutong.ui.state.AppChaoxingSession
import com.ahu.ahutong.ui.state.AppChaoxingStore
import com.ahu.ahutong.ui.state.ChaoxingSyncServiceImpl
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 学习通 feature 的接线：端口在 :data:chaoxing，实现留在 :app（协议客户端、静态存储、提醒）。
 *
 * 只有组合根同时认识两边——学习通的界面因此看不见 Cookie、存储键，也看不见提醒调度。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChaoxingWiringModule {

    @Binds
    @Singleton
    abstract fun bindChaoxingSession(implementation: AppChaoxingSession): ChaoxingSession

    @Binds
    @Singleton
    abstract fun bindChaoxingStore(implementation: AppChaoxingStore): ChaoxingStore

    @Binds
    @Singleton
    abstract fun bindChaoxingReminders(
        implementation: AppChaoxingReminders
    ): ChaoxingReminders

    @Binds
    @Singleton
    abstract fun bindChaoxingSyncService(
        implementation: ChaoxingSyncServiceImpl
    ): ChaoxingSyncService

    @Binds
    @Singleton
    abstract fun bindSemesterRangeProvider(
        implementation: AppXuexiaotongSemesterRangeProvider
    ): XuexiaotongSemesterRangeProvider

    companion object {

        /**
         * 后台提醒只拿窄视图。Store 是 Kotlin object（没有可注入的构造点），
         * 而这里要交出去的也只是它那六个成员。
         */
        @Provides
        @Singleton
        fun provideChaoxingReminderStore(): ChaoxingReminderStore = Store

        /** 学习通的 ViewModel 只要求"一个 IO 调度器"，具体是谁由组合根决定。 */
        @Provides
        @XuexiaotongDispatcher
        fun provideXuexiaotongDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
