package com.ahu.ahutong.data.network

import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.gmis.GmisSessionExpiredException
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.LoginOutcome
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.SessionStore
import com.ahu.ahutong.data.toAhuError
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Pure telemetry gating decisions so campus session-expiry or bad-password errors are never
 * misreported as upstream server outages.
 */
internal object StatusReportingPolicy {

    fun isSessionAuthError(error: AhuError, cause: Throwable? = null): Boolean {
        if (error is AhuError.Unauthorized) return true
        if (error is AhuError.Server && error.code == 401) return true
        if (cause is GmisSessionExpiredException) return true
        if (cause is HttpException && cause.code() == 401) return true
        return false
    }

    fun shouldReportCampusFailure(
        isMockMode: Boolean,
        isSessionAuthenticated: Boolean,
        error: AhuError,
        cause: Throwable? = null
    ): Boolean {
        if (isMockMode || !isSessionAuthenticated) return false
        return !isSessionAuthError(error, cause)
    }

    fun shouldReportLoginFailure(
        isMockMode: Boolean,
        error: AhuError
    ): Boolean {
        if (isMockMode) return false
        if (error is AhuError.Unauthorized) return false
        if (error is AhuError.Server && error.code == -1 && error.message == "请将信息填写完整") {
            return false
        }
        return true
    }
}

/**
 * Application-level bridge to [StatusReporter] with session-state & mock-mode gating.
 */
object AppStatusReporting {

    private fun isMock(): Boolean = runCatching { AHUCache.getMockData() }.getOrDefault(false)

    private fun isSessionValid(): Boolean =
        runCatching {
            AhuSessionState.status.value == AhuSessionState.Status.Authenticated &&
                SessionStore.isLoggedIn()
        }.getOrDefault(false)

    fun <T> reportCampusResult(
        endpoint: StatusEndpoint,
        action: String,
        result: AhuResult<T>,
        latencyMs: Long? = null
    ): AhuResult<T> {
        if (isMock()) return result
        when (result) {
            is AhuResult.Success -> StatusReporter.reportOk(endpoint, action, latencyMs)
            is AhuResult.Failure -> {
                if (StatusReportingPolicy.shouldReportCampusFailure(
                        isMockMode = false,
                        isSessionAuthenticated = isSessionValid(),
                        error = result.error
                    )
                ) {
                    StatusReporter.reportError(endpoint, action, result.error, latencyMs)
                }
            }
        }
        return result
    }

    fun reportCampusSuccess(
        endpoint: StatusEndpoint,
        action: String,
        latencyMs: Long? = null
    ) {
        if (!isMock()) {
            StatusReporter.reportOk(endpoint, action, latencyMs)
        }
    }

    fun reportCampusThrowable(
        endpoint: StatusEndpoint,
        action: String,
        throwable: Throwable,
        latencyMs: Long? = null
    ) {
        if (throwable is CancellationException || isMock()) return
        val error = throwable.toAhuError()
        if (StatusReportingPolicy.shouldReportCampusFailure(
                isMockMode = false,
                isSessionAuthenticated = isSessionValid(),
                error = error,
                cause = throwable
            )
        ) {
            StatusReporter.reportError(endpoint, action, error, latencyMs)
        }
    }

    fun reportLoginResult(
        action: String,
        result: AhuResult<LoginOutcome>,
        latencyMs: Long? = null
    ): AhuResult<LoginOutcome> {
        if (isMock()) return result
        when (result) {
            is AhuResult.Success -> {
                if (result.value is LoginOutcome.Success) {
                    StatusReporter.reportOk(StatusEndpoint.AHUTONG_LOGIN, action, latencyMs)
                }
            }
            is AhuResult.Failure -> {
                if (StatusReportingPolicy.shouldReportLoginFailure(isMockMode = false, error = result.error)) {
                    StatusReporter.reportError(StatusEndpoint.AHUTONG_LOGIN, action, result.error, latencyMs)
                }
            }
        }
        return result
    }

    fun reportCommonSuccess(action: String, latencyMs: Long? = null) {
        if (!isMock()) {
            StatusReporter.reportOk(StatusEndpoint.AHUTONG_COMMON, action, latencyMs)
        }
    }

    fun reportCommonThrowable(action: String, throwable: Throwable, latencyMs: Long? = null) {
        if (throwable is CancellationException || isMock()) return
        StatusReporter.reportError(StatusEndpoint.AHUTONG_COMMON, action, throwable.toAhuError(), latencyMs)
    }

    fun reportCommonError(action: String, error: AhuError, latencyMs: Long? = null) {
        if (!isMock()) {
            StatusReporter.reportError(StatusEndpoint.AHUTONG_COMMON, action, error, latencyMs)
        }
    }
}
