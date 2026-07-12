package com.ahu.ahutong.data.campuscard.di

import com.ahu.ahutong.data.campuscard.AhuCacheCampusCardLocalStore
import com.ahu.ahutong.data.campuscard.CampusCardCrawlerSource
import com.ahu.ahutong.data.campuscard.CampusCardLocalStore
import com.ahu.ahutong.data.campuscard.CampusCardRepository
import com.ahu.ahutong.data.campuscard.CrawlerCampusCardSource
import com.ahu.ahutong.data.campuscard.internal.DefaultCampusCardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CampusCardDataModule {
    @Binds @Singleton
    abstract fun bindCampusCardRepository(impl: DefaultCampusCardRepository): CampusCardRepository

    @Binds @Singleton
    abstract fun bindCampusCardLocalStore(impl: AhuCacheCampusCardLocalStore): CampusCardLocalStore

    @Binds @Singleton
    abstract fun bindCampusCardCrawlerSource(impl: CrawlerCampusCardSource): CampusCardCrawlerSource
}
