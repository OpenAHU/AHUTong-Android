package com.ahu.ahutong.di

import com.ahu.ahutong.data.recharge.CardRechargeSource
import com.ahu.ahutong.data.recharge.BathroomDepositSource
import com.ahu.ahutong.ui.state.RechargeDispatcher
import com.ahu.ahutong.ui.state.RepositoryBathroomDepositSource
import com.ahu.ahutong.ui.state.RepositoryCardRechargeSource
import com.ahu.ahutong.data.recharge.NetworkRechargeSource
import com.ahu.ahutong.data.adapter.RepositoryNetworkRechargeSource
import com.ahu.ahutong.data.adapter.RepositoryElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityDepositSource
import com.ahu.ahutong.data.recharge.ElectricityUsageSource
import com.ahu.ahutong.data.adapter.RepositoryElectricityUsageSource
import com.ahu.ahutong.core.storage.ElectricityAlertSettings
import com.ahu.ahutong.electricity.LocalElectricityAlertStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 充值 feature 的接线：端口在 :data:recharge，实现留在 :app（协议客户端与仓库）。
 *
 * 只有组合根同时认识两边——充值的界面因此看不见 ycard 的请求形状与订单号是怎么来的。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RechargeWiringModule {

    @Binds
    @Singleton
    abstract fun bindElectricityAlertSettings(implementation: LocalElectricityAlertStore): ElectricityAlertSettings

    @Binds
    @Singleton
    abstract fun bindElectricityUsageSource(implementation: RepositoryElectricityUsageSource): ElectricityUsageSource

    @Binds
    @Singleton
    abstract fun bindCardRechargeSource(
        implementation: RepositoryCardRechargeSource
    ): CardRechargeSource

    @Binds
    @Singleton
    abstract fun bindBathroomDepositSource(
        implementation: RepositoryBathroomDepositSource
    ): BathroomDepositSource

    @Binds
    @Singleton
    abstract fun bindNetworkRechargeSource(
        implementation: RepositoryNetworkRechargeSource
    ): NetworkRechargeSource

    @Binds
    @Singleton
    abstract fun bindElectricityDepositSource(
        implementation: RepositoryElectricityDepositSource
    ): ElectricityDepositSource

    companion object {

        /** feature 的 ViewModel 只要求"一个 IO 调度器"，具体是谁由组合根决定。 */
        @Provides
        @RechargeDispatcher
        fun provideRechargeDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
