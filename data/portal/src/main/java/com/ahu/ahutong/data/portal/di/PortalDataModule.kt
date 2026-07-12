package com.ahu.ahutong.data.portal.di

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
    @Binds
    @Singleton
    abstract fun bindLostFoundRepository(
        impl: DefaultLostFoundRepository,
    ): LostFoundRepository
}
