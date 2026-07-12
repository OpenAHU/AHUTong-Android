package com.ahu.ahutong.data.payment.di

import com.ahu.ahutong.data.payment.PaymentRepository
import com.ahu.ahutong.data.payment.internal.DefaultPaymentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentDataModule {
    @Binds
    @Singleton
    abstract fun bindPaymentRepository(
        impl: DefaultPaymentRepository,
    ): PaymentRepository
}
