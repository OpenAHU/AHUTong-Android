package com.ahu.ahutong.data.schedule

import com.ahu.ahutong.data.model.ScheduleConfigBean

/** 学期配置从哪来：本地缓存、远端教务，还是兜底默认值。 */
enum class ConfigSource {
    LOCAL,
    REMOTE,
    SERVER,
    DEFAULT
}

/**
 * 一次学期配置解析的结果：界面要用的 [config]，以及它的来源。
 *
 * 来源要暴露出来，是因为调用方会据此决定「还要不要再问一次远端」——那是策略，不是细节。
 */
data class ResolvedConfig(
    val config: ScheduleConfigBean,
    val source: ConfigSource
)

