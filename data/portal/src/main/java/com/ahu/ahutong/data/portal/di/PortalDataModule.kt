package com.ahu.ahutong.data.portal.di

import com.ahu.ahutong.data.portal.AdwmhLostFoundRemoteSource
import com.ahu.ahutong.data.portal.AhuCacheLostFoundLocalStore
import com.ahu.ahutong.data.portal.LostFoundLocalStore
import com.ahu.ahutong.data.portal.LostFoundRemoteSource
import com.ahu.ahutong.data.portal.LostFoundRepository
import com.ahu.ahutong.data.portal.internal.DefaultLostFoundRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PortalDataModule {
    @Binds @Singleton
    abstract fun bindLostFoundRepository(impl: DefaultLostFoundRepository): LostFoundRepository

    @Binds @Singleton
    abstract fun bindLostFoundLocalStore(impl: AhuCacheLostFoundLocalStore): LostFoundLocalStore

    @Binds @Singleton
    abstract fun bindLostFoundRemoteSource(impl: AdwmhLostFoundRemoteSource): LostFoundRemoteSource
}
