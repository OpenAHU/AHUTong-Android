package com.ahu.ahutong.data.adapter

import com.ahu.ahutong.data.network.AppStatusReporting
import com.ahu.ahutong.data.preview.CurrentEarlyAccessIdentity
import com.ahu.ahutong.data.preview.EarlyAccessManager
import com.ahu.ahutong.data.preview.EarlyAccessUpdateCheck
import com.ahu.ahutong.data.server.AhuTong
import com.ahu.ahutong.data.server.model.ApkUpdateInfo
import com.ahu.ahutong.data.update.ApkUpdateInfoSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** [ApkUpdateInfoSource] 的生产实现：当前账号经服务端确认加入后才请求尝鲜渠道。 */
@Singleton
class AhuTongApkUpdateInfoSource @Inject constructor() : ApkUpdateInfoSource {
    private val updateCheck = EarlyAccessUpdateCheck(
        identities = CurrentEarlyAccessIdentity,
        eligibleSubject = EarlyAccessManager::eligibleUpdateSubject,
        fetch = { subject -> AhuTong.API.getApkUpdateInfo(subject) }
    )

    override suspend fun latest(): ApkUpdateInfo {
        val startedAt = System.currentTimeMillis()
        return try {
            updateCheck.latest().also {
                AppStatusReporting.reportCommonSuccess(
                    "apkUpdateCheck",
                    (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            AppStatusReporting.reportCommonThrowable(
                "apkUpdateCheck",
                t,
                (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            )
            throw t
        }
    }
}
