package com.ahu.ahutong.data.auth.di

import com.ahu.ahutong.data.auth.AuthRepository
import com.ahu.ahutong.data.auth.internal.DefaultAuthRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: DefaultAuthRepository,
    ): AuthRepository
}
