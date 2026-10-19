package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.payment.PaymentQrRepository
import com.ahu.ahutong.data.payment.PaymentQrSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class PaymentQrUiState(
    val snapshot: PaymentQrSnapshot? = null,
    val loading: Boolean = false,
    val attempted: Boolean = false,
    val ageSeconds: Long = 0,
    val failed: Boolean = false
) {
    val statusText: String get() = when {
        loading && snapshot != null -> "正在刷新（缓存于${ageSeconds}秒前）"
        loading -> "正在获取二维码"
        snapshot != null && ageSeconds >= 60 -> "缓存二维码（${ageSeconds}秒前），请刷新确认"
        snapshot != null && failed -> "网络不稳定，继续显示缓存二维码"
        snapshot != null -> "${ageSeconds}秒前更新"
        failed -> "网络暂不可用，正在自动重试"
        else -> ""
    }
}

/** Visible-page lifecycle; stopping a page does not cancel the repository's shared fetch. */
internal class PaymentQrLoader(
    private val repository: PaymentQrRepository,
    private val scope: CoroutineScope,
    private val onFirstRefreshFinished: () -> Unit = {}
) {
    private val mutableState = MutableStateFlow(PaymentQrUiState())
    val state = mutableState.asStateFlow()
    private var loop: Job? = null
    private var refreshRequested = false

    fun start(forceRefresh: Boolean = false) {
        if (loop?.isActive == true) {
            if (forceRefresh && !mutableState.value.loading) refreshRequested = true
            return
        }
        refreshRequested = forceRefresh
        loop = scope.launch {
            var owner = repository.currentOwner()
            var cached = repository.cachedForDisplay()
            var retryAt = repository.elapsedNow() + (cached?.refreshDelayMillis(repository.elapsedNow()) ?: 0)
            var failures = 0
            var attempted = false
            var notified = false
            mutableState.value = display(cached, attempted = false, failed = false)
            while (isActive) {
                val currentOwner = repository.currentOwner()
                if (currentOwner == null) {
                    mutableState.value = PaymentQrUiState(attempted = true)
                    break
                }
                if (currentOwner != owner) {
                    owner = currentOwner
                    retryAt = 0
                    failures = 0
                    attempted = false
                }
                cached = repository.cachedForDisplay()
                mutableState.value = display(cached, attempted, failures > 0)
                if (refreshRequested || repository.elapsedNow() >= retryAt) {
                    refreshRequested = false
                    mutableState.value = display(cached, attempted, failures > 0).copy(loading = true)
                    val result = refreshWhileDisplaying(attempted, failures > 0)
                    attempted = true
                    failures = if (result.isSuccess) 0 else failures + 1
                    cached = repository.cachedForDisplay()
                    mutableState.value = display(cached, attempted, failures > 0)
                    retryAt = repository.elapsedNow() + if (result.isSuccess) {
                        cached?.refreshDelayMillis(repository.elapsedNow()) ?: 1_000L
                    } else retryDelayMillis(failures)
                    if (!notified) {
                        notified = true
                        onFirstRefreshFinished()
                    }
                }
                delay(1_000L)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        refreshRequested = false
        mutableState.value = PaymentQrUiState()
    }

    private suspend fun refreshWhileDisplaying(attempted: Boolean, failed: Boolean): Result<PaymentQrSnapshot> =
        coroutineScope {
            val pending = async {
                try { repository.refresh() } catch (error: CancellationException) {
                    // Identity changes may cancel the producer; page cancellation
                    // must still propagate to this consumer.
                    currentCoroutineContext().ensureActive()
                    Result.failure(error)
                }
            }
            var result = withTimeoutOrNull(1_000L) { pending.await() }
            while (result == null) {
                // Keep age/expiry live while a slow network request is in flight.
                mutableState.value = display(repository.cachedForDisplay(), attempted, failed).copy(loading = true)
                result = withTimeoutOrNull(1_000L) { pending.await() }
            }
            result
        }

    private fun display(value: PaymentQrSnapshot?, attempted: Boolean, failed: Boolean) = PaymentQrUiState(
        snapshot = value,
        attempted = attempted,
        ageSeconds = value?.ageMillis(repository.elapsedNow())?.div(1_000) ?: 0,
        failed = failed
    )

    companion object {
        fun retryDelayMillis(failures: Int): Long = when (failures) {
            1 -> 5_000L
            2 -> 10_000L
            3 -> 20_000L
            else -> 30_000L
        }
    }
}
