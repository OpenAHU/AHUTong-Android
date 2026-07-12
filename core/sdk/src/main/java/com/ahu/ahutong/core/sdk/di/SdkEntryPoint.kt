package com.ahu.ahutong.core.sdk.di

import com.ahu.ahutong.core.sdk.CampusNativeGateway
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Entry point for non-Hilt-managed code (Application, object facades).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SdkEntryPoint {
    fun campusNativeGateway(): CampusNativeGateway
}
