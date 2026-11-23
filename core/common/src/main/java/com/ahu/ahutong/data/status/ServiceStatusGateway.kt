package com.ahu.ahutong.data.status

import com.ahu.ahutong.core.common.AhuResult

interface ServiceStatusGateway {
    suspend fun fetchStatus(): AhuResult<ServiceStatusSnapshot>
}
