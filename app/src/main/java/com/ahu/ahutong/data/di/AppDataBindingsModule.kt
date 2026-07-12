package com.ahu.ahutong.data.di

import com.ahu.ahutong.data.auth.AuthCookieSyncer
import com.ahu.ahutong.data.auth.AuthCrawlerSource
import com.ahu.ahutong.data.auth.AuthRuntimeReset
import com.ahu.ahutong.data.auth.AuthSessionStore
import com.ahu.ahutong.data.auth.AhuCacheAuthSessionStore
import com.ahu.ahutong.data.auth.CrawlerAuthRuntimeReset
import com.ahu.ahutong.data.auth.CrawlerAuthSource
import com.ahu.ahutong.data.auth.NativeCookieSyncer
import com.ahu.ahutong.data.calendar.AhuTongSchoolCalendarRemoteSource
import com.ahu.ahutong.data.calendar.SchoolCalendarRemoteSource
import com.ahu.ahutong.data.campuscard.AhuCacheCampusCardLocalStore
import com.ahu.ahutong.data.campuscard.CampusCardCrawlerSource
import com.ahu.ahutong.data.campuscard.CampusCardLocalStore
import com.ahu.ahutong.data.campuscard.CrawlerCampusCardSource
import com.ahu.ahutong.data.home.AhuCacheHomePreferences
import com.ahu.ahutong.data.home.HomePreferences
import com.ahu.ahutong.data.exam.AhuCacheExamLocalStore
import com.ahu.ahutong.data.exam.CrawlerExamSource
import com.ahu.ahutong.data.exam.ExamCrawlerSource
import com.ahu.ahutong.data.exam.ExamLocalStore
import com.ahu.ahutong.data.grade.AhuCacheGradeLocalStore
import com.ahu.ahutong.data.grade.CrawlerGradeSource
import com.ahu.ahutong.data.grade.GradeCrawlerSource
import com.ahu.ahutong.data.grade.GradeLocalStore
import com.ahu.ahutong.data.payment.PaymentCredentialGate
import com.ahu.ahutong.data.payment.PaymentRemoteSource
import com.ahu.ahutong.data.payment.TokenPaymentCredentialGate
import com.ahu.ahutong.data.payment.YcardPaymentRemoteSource
import com.ahu.ahutong.data.portal.AdwmhLostFoundRemoteSource
import com.ahu.ahutong.data.portal.AhuCacheLostFoundLocalStore
import com.ahu.ahutong.data.portal.LostFoundLocalStore
import com.ahu.ahutong.data.portal.LostFoundRemoteSource
import com.ahu.ahutong.data.schedule.AhuCacheScheduleLocalStore
import com.ahu.ahutong.data.schedule.AppScheduleReminderCoordinator
import com.ahu.ahutong.data.schedule.AppScheduleWeekResolver
import com.ahu.ahutong.data.schedule.CrawlerScheduleSource
import com.ahu.ahutong.data.schedule.ScheduleCrawlerSource
import com.ahu.ahutong.data.schedule.ScheduleLocalStore
import com.ahu.ahutong.data.schedule.ScheduleReminderCoordinator
import com.ahu.ahutong.data.schedule.ScheduleWeekResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-layer adapters that wire domain repositories to AHUCache / crawlers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppDataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindScheduleLocalStore(impl: AhuCacheScheduleLocalStore): ScheduleLocalStore

    @Binds
    @Singleton
    abstract fun bindScheduleCrawlerSource(impl: CrawlerScheduleSource): ScheduleCrawlerSource

    @Binds
    @Singleton
    abstract fun bindScheduleWeekResolver(impl: AppScheduleWeekResolver): ScheduleWeekResolver

    @Binds
    @Singleton
    abstract fun bindScheduleReminderCoordinator(
        impl: AppScheduleReminderCoordinator,
    ): ScheduleReminderCoordinator

    @Binds
    @Singleton
    abstract fun bindAuthSessionStore(impl: AhuCacheAuthSessionStore): AuthSessionStore

    @Binds
    @Singleton
    abstract fun bindAuthCrawlerSource(impl: CrawlerAuthSource): AuthCrawlerSource

    @Binds
    @Singleton
    abstract fun bindAuthCookieSyncer(impl: NativeCookieSyncer): AuthCookieSyncer

    @Binds
    @Singleton
    abstract fun bindAuthRuntimeReset(impl: CrawlerAuthRuntimeReset): AuthRuntimeReset

    @Binds
    @Singleton
    abstract fun bindGradeLocalStore(impl: AhuCacheGradeLocalStore): GradeLocalStore

    @Binds
    @Singleton
    abstract fun bindGradeCrawlerSource(impl: CrawlerGradeSource): GradeCrawlerSource

    @Binds
    @Singleton
    abstract fun bindExamLocalStore(impl: AhuCacheExamLocalStore): ExamLocalStore

    @Binds
    @Singleton
    abstract fun bindExamCrawlerSource(impl: CrawlerExamSource): ExamCrawlerSource

    @Binds
    @Singleton
    abstract fun bindCampusCardLocalStore(impl: AhuCacheCampusCardLocalStore): CampusCardLocalStore

    @Binds
    @Singleton
    abstract fun bindCampusCardCrawlerSource(impl: CrawlerCampusCardSource): CampusCardCrawlerSource

    @Binds
    @Singleton
    abstract fun bindHomePreferences(impl: AhuCacheHomePreferences): HomePreferences

    @Binds
    @Singleton
    abstract fun bindLostFoundLocalStore(impl: AhuCacheLostFoundLocalStore): LostFoundLocalStore

    @Binds
    @Singleton
    abstract fun bindLostFoundRemoteSource(impl: AdwmhLostFoundRemoteSource): LostFoundRemoteSource

    @Binds
    @Singleton
    abstract fun bindPaymentRemoteSource(impl: YcardPaymentRemoteSource): PaymentRemoteSource

    @Binds
    @Singleton
    abstract fun bindPaymentCredentialGate(impl: TokenPaymentCredentialGate): PaymentCredentialGate

    @Binds
    @Singleton
    abstract fun bindSchoolCalendarRemoteSource(
        impl: AhuTongSchoolCalendarRemoteSource,
    ): SchoolCalendarRemoteSource
}
