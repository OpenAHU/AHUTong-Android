package com.ahu.ahutong.data.home.di

import com.ahu.ahutong.data.home.AhuCacheHomePreferences
import com.ahu.ahutong.data.home.HomePreferences
import com.ahu.ahutong.data.weather.WeatherPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeDataBindingsModule {
    @Binds @Singleton
    abstract fun bindHomePreferences(impl: AhuCacheHomePreferences): HomePreferences

    @Binds @Singleton
    abstract fun bindWeatherPreferences(impl: AhuCacheHomePreferences): WeatherPreferences
}
