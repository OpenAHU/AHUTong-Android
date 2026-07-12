package com.ahu.ahutong.data.campuscard.di

import com.ahu.ahutong.data.campuscard.CampusCardRepository
import com.ahu.ahutong.data.campuscard.internal.DefaultCampusCardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CampusCardDataModule {
    @Binds
    @Singleton
    abstract fun bindCampusCardRepository(
        impl: DefaultCampusCardRepository,
    ): CampusCardRepository
}
