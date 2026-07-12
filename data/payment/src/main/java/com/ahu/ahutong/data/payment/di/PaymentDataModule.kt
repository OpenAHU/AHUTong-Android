package com.ahu.ahutong.data.payment.di

import com.ahu.ahutong.data.payment.AhuCachePaymentLocalStore
import com.ahu.ahutong.data.payment.PaymentCredentialGate
import com.ahu.ahutong.data.payment.PaymentLocalStore
import com.ahu.ahutong.data.payment.PaymentRemoteSource
import com.ahu.ahutong.data.payment.PaymentRepository
import com.ahu.ahutong.data.payment.TokenPaymentCredentialGate
import com.ahu.ahutong.data.payment.YcardPaymentRemoteSource
import com.ahu.ahutong.data.payment.internal.DefaultPaymentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PaymentDataModule {
    @Binds @Singleton
    abstract fun bindPaymentRepository(impl: DefaultPaymentRepository): PaymentRepository

    @Binds @Singleton
    abstract fun bindPaymentRemoteSource(impl: YcardPaymentRemoteSource): PaymentRemoteSource

    @Binds @Singleton
    abstract fun bindPaymentCredentialGate(impl: TokenPaymentCredentialGate): PaymentCredentialGate

    @Binds @Singleton
    abstract fun bindPaymentLocalStore(impl: AhuCachePaymentLocalStore): PaymentLocalStore
}
