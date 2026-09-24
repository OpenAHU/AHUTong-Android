package com.ahu.ahutong.data.crawler.net

/**
 * 网络层唯一允许调用的"会话"接缝。
 *
 * 有了它，data/crawler/net 不再 import 任何业务类型（AHURepository / AHUCache / AhuSessionState）；
 * 业务实现见 data/session/RepositorySessionExpiryHook，由 API 客户端构造时注入。
 * 约束见 docs/architecture/CONTEXT.md 第 2 节 R4。
 */
interface SessionExpiryHook {

    /**
     * @param observedGeneration 请求真正发出时的会话代号，用于避免旧请求触发重复登录。
     * @return true 表示已成功续期、调用方可重试原请求；false 表示续期失败。
     */
    suspend fun refresh(observedGeneration: Long): Boolean

    /** 自动续期失败或重试仍被拒绝时，只有当前会话代号可以提交过期状态。 */
    suspend fun onExpired(observedGeneration: Long)
}
