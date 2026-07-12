package com.ahu.ahutong.data.auth.di

import com.ahu.ahutong.data.auth.AhuCacheAuthSessionStore
import com.ahu.ahutong.data.auth.AuthCookieSyncer
import com.ahu.ahutong.data.auth.AuthCrawlerSource
import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.auth.AuthRuntimeReset
import com.ahu.ahutong.data.auth.AuthSessionStore
import com.ahu.ahutong.data.auth.CrawlerAuthRuntimeReset
import com.ahu.ahutong.data.auth.CrawlerAuthSource
import com.ahu.ahutong.data.auth.NativeCookieSyncer
import com.ahu.ahutong.data.auth.internal.DefaultAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {
    @Binds @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds @Singleton
    abstract fun bindAuthSessionStore(impl: AhuCacheAuthSessionStore): AuthSessionStore

    @Binds @Singleton
    abstract fun bindAuthCrawlerSource(impl: CrawlerAuthSource): AuthCrawlerSource

    @Binds @Singleton
    abstract fun bindAuthCookieSyncer(impl: NativeCookieSyncer): AuthCookieSyncer

    @Binds @Singleton
    abstract fun bindAuthRuntimeReset(impl: CrawlerAuthRuntimeReset): AuthRuntimeReset
}
