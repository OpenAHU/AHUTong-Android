package com.ahu.ahutong.core.sdk.di

import com.ahu.ahutong.core.sdk.CampusNativeGateway
import com.ahu.ahutong.core.sdk.RustCampusNativeGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SdkModule {
    @Binds
    @Singleton
    abstract fun bindCampusNativeGateway(
        impl: RustCampusNativeGateway,
    ): CampusNativeGateway
}
