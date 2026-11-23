package com.ahu.ahutong.core.common

/**
 * 统一错误模型（见 AGENTS.md §2 错误边界）。
 *
 * 取代三套并存的表达方式：`AHUResponse.msg` 字符串、Kotlin `Result`、以及各适配器自行约定的失败语义。
 * 调用方可以穷举分支，而不再依赖字符串判断。
 */
sealed interface AhuError {

    /** 网络层失败：连接、DNS、TLS。 */
    data object Network : AhuError

    /** 超时（连接 / 读取 / 整体）。 */
    data object Timeout : AhuError

    /** 会话失效，需要重新登录；message 保留上游原文，便于继续展示同样的提示。 */
    data class Unauthorized(val message: String) : AhuError

    /** 上游格式与预期不符（解析失败、缺字段）——不是网络问题，而是对方改版。 */
    data class ProtocolChanged(val detail: String) : AhuError

    /** 上游明确返回的业务错误。 */
    data class Server(val code: Int, val message: String) : AhuError

    /** 无法归类的失败，保留原文以便排查；正常路径不应产生它。 */
    data class Unknown(val message: String) : AhuError
}
