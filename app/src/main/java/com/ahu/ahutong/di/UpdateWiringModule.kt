package com.ahu.ahutong.di

import com.ahu.ahutong.data.update.ApkDownloader
import com.ahu.ahutong.data.adapter.AhuTongApkUpdateInfoSource
import com.ahu.ahutong.data.adapter.AppApkDirectory
import com.ahu.ahutong.data.adapter.AppApkUpdateSkipStore
import com.ahu.ahutong.data.update.ApkDirectory
import com.ahu.ahutong.data.update.ApkUpdateChecker
import com.ahu.ahutong.data.update.ApkUpdateInfoSource
import com.ahu.ahutong.data.update.ApkUpdateSkipStore
import com.ahu.ahutong.data.update.DefaultApkUpdateChecker
import com.ahu.ahutong.data.update.DefaultApkDownloader
import com.ahu.ahutong.data.update.ApkDownloadTransport
import com.ahu.ahutong.data.update.RetrofitApkDownloadTransport
import com.ahu.ahutong.data.update.UpdateDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 更新的接线：端口在 :data:update，实现也在那里（下载只依赖 :core:network 的工厂，
 * 不需要认识 :app 的任何东西）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateWiringModule {

    @Binds
    @Singleton
    abstract fun bindApkDownloader(implementation: DefaultApkDownloader): ApkDownloader

    @Binds
    @Singleton
    abstract fun bindApkDownloadTransport(
        implementation: RetrofitApkDownloadTransport
    ): ApkDownloadTransport

    @Binds
    @Singleton
    abstract fun bindApkUpdateInfoSource(
        implementation: AhuTongApkUpdateInfoSource
    ): ApkUpdateInfoSource

    @Binds
    @Singleton
    abstract fun bindApkDirectory(implementation: AppApkDirectory): ApkDirectory

    @Binds
    @Singleton
    abstract fun bindApkUpdateSkipStore(implementation: AppApkUpdateSkipStore): ApkUpdateSkipStore

    @Binds
    @Singleton
    abstract fun bindApkUpdateChecker(
        implementation: DefaultApkUpdateChecker
    ): ApkUpdateChecker

    companion object {
        @Provides
        @UpdateDispatcher
        fun provideUpdateDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
