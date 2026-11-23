package com.ahu.ahutong.data.status

enum class ServiceHealth(val code: Int) {
    DOWN(0),
    UP(1),
    PENDING(2),
    MAINTENANCE(3);

    companion object {
        fun fromCode(code: Int?): ServiceHealth = when (code) {
            0 -> DOWN
            1 -> UP
            2 -> PENDING
            3 -> MAINTENANCE
            else -> PENDING
        }
    }
}

data class ServiceStatusIncident(
    val id: Int,
    val title: String,
    val content: String,
    val style: String,
    val pin: Boolean,
    val createdDate: String,
    val lastUpdatedDate: String?
)

data class ServiceStatusMonitor(
    val id: Int,
    val name: String,
    val type: String,
    val health: ServiceHealth,
    val uptime24h: Double?,
    val latestPingMs: Int?,
    val errorRate: Double?,
    val lastCheckTime: String?
)

data class ServiceStatusGroup(
    val id: Int,
    val name: String,
    val monitors: List<ServiceStatusMonitor>
)

data class ServiceStatusSnapshot(
    val title: String,
    val autoRefreshSeconds: Int,
    val incidents: List<ServiceStatusIncident>,
    val groups: List<ServiceStatusGroup>
) {
    val allMonitors: List<ServiceStatusMonitor>
        get() = groups.flatMap { it.monitors }

    val upCount: Int
        get() = allMonitors.count { it.health == ServiceHealth.UP }

    val overallHealth: ServiceHealth
        get() {
            val monitors = allMonitors
            if (monitors.isEmpty()) return ServiceHealth.PENDING
            return when {
                monitors.any { it.health == ServiceHealth.DOWN } -> ServiceHealth.DOWN
                monitors.any { it.health == ServiceHealth.MAINTENANCE } -> ServiceHealth.MAINTENANCE
                monitors.any { it.health == ServiceHealth.PENDING } -> ServiceHealth.PENDING
                else -> ServiceHealth.UP
            }
        }
}
