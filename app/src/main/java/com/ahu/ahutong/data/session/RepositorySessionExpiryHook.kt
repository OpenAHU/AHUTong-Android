package com.ahu.ahutong.data.session

import com.ahu.ahutong.data.crawler.net.SessionExpiryHook
import com.ahu.ahutong.data.crawler.net.SessionRefreshCoordinator

/**
 * 把网络层的 [SessionExpiryHook] 接到 [AhuSession]：
 * 网络层只认识接缝，具体续期策略由会话层负责（ADR 0002）。
 *
 * 会话实例可注入，因此"网络层通知 → 会话层动作"这条映射本身也能被测试钉住。
 */
class RepositorySessionExpiryHook(
    private val session: AhuSession = DefaultAhuSession
) : SessionExpiryHook {

    override suspend fun refresh(observedGeneration: Long): Boolean =
        session.ensureFresh(observedGeneration)

    override suspend fun onExpired(observedGeneration: Long) {
        SessionRefreshCoordinator.commitIfCurrent(observedGeneration) {
            if (AhuSessionState.status.value != AhuSessionState.Status.Anonymous) {
                AhuSessionState.markExpired()
            }
        }
    }
}

