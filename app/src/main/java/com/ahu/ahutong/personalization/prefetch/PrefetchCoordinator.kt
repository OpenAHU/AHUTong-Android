package com.ahu.ahutong.personalization.prefetch

import android.os.SystemClock
import com.ahu.ahutong.data.payment.PaymentQrRepository
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.PrefetchPolicy
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.utils.FileUtils
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import com.ahu.ahutong.core.common.map
import com.ahu.ahutong.core.common.AhuError
import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.toAhuError
import com.ahu.ahutong.data.toAhuResult
import com.ahu.ahutong.data.session.SessionStore

enum class PrefetchState { IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED, CONSUMED }

data class PrefetchDiagnostic(
    val actionId: String,
    val state: PrefetchState,
    val startedAtElapsedMs: Long,
    val finishedAtElapsedMs: Long?,
    val failureCode: String?
)

@Singleton
class PrefetchCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val paymentQrRepository: PaymentQrRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val inFlight = ConcurrentHashMap<String, Deferred<AhuResult<Unit>>>()
    private val networkStarts = ArrayDeque<Long>()
    private val networkSlots = Semaphore(1)
    private val localSlots = Semaphore(2)
    private var paymentQrStartsThisSession = 0
    private var lastPaymentQrStartElapsedMs = Long.MIN_VALUE
    private var estimatedNetworkBytesThisSession = 0L
    private var failuresThisSession = 0
    private val freshUntilElapsedMs = ConcurrentHashMap<String, Long>()
    private val _diagnostics = MutableStateFlow<Map<String, PrefetchDiagnostic>>(emptyMap())

    val diagnostics: StateFlow<Map<String, PrefetchDiagnostic>> = _diagnostics.asStateFlow()

    suspend fun beginSession() = mutex.withLock {
        paymentQrStartsThisSession = 0
        lastPaymentQrStartElapsedMs = Long.MIN_VALUE
        estimatedNetworkBytesThisSession = 0L
        failuresThisSession = 0
        freshUntilElapsedMs.clear()
    }

    suspend fun consider(probabilities: Map<String, Float>, holdout: Boolean, foreground: Boolean) {
        if (!canPrefetch(holdout, foreground)) return
        probabilities.entries.sortedByDescending(Map.Entry<String, Float>::value)
            .mapNotNull { (id, probability) -> AppActionId.fromStableId(id)?.let { Triple(it, probability, AppActionCatalog.spec(it)) } }
            .filter { (_, probability, spec) -> probability >= threshold(spec.prefetchPolicy) && spec.prefetchPolicy != PrefetchPolicy.NONE }
            .take(2)
            .forEach { (action, _, spec) -> launchSingleFlight(action, spec.prefetchPolicy) }
    }

    /**
     * A visible suggestion is a stronger product signal than a background probability candidate.
     * It may bypass the generic probability threshold, but never the user's prefetch setting,
     * holdout isolation, resource policy, budgets, TTL or single-flight protection.
     */
    suspend fun prefetchSuggestedAction(action: AppActionId, holdout: Boolean, foreground: Boolean) {
        if (!canPrefetch(holdout, foreground)) return
        val policy = AppActionCatalog.spec(action).prefetchPolicy
        if (policy != PrefetchPolicy.NONE) launchSingleFlight(action, policy)
    }

    suspend fun cancelAll() = mutex.withLock {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        _diagnostics.value = _diagnostics.value.mapValues { (_, value) ->
            if (value.state == PrefetchState.RUNNING) value.copy(state = PrefetchState.CANCELLED, finishedAtElapsedMs = SystemClock.elapsedRealtime()) else value
        }
    }

    fun onActionOpened(action: AppActionId): Boolean {
        val key = action.stableId
        if ((freshUntilElapsedMs[key] ?: 0L) > SystemClock.elapsedRealtime()) {
            _diagnostics.value = _diagnostics.value + (
                key to (_diagnostics.value[key]?.copy(
                    state = PrefetchState.CONSUMED,
                    finishedAtElapsedMs = SystemClock.elapsedRealtime()
                ) ?: PrefetchDiagnostic(key, PrefetchState.CONSUMED, 0L, SystemClock.elapsedRealtime(), null))
            )
            return true
        }
        return false
    }

    fun invalidate(action: AppActionId) {
        freshUntilElapsedMs.remove(action.stableId)
    }

    private suspend fun launchSingleFlight(action: AppActionId, policy: PrefetchPolicy) {
        val key = action.stableId
        mutex.withLock {
            if (inFlight[key]?.isActive == true) return
            if ((freshUntilElapsedMs[key] ?: 0L) > SystemClock.elapsedRealtime()) return
            if (policy == PrefetchPolicy.NETWORK_READ_ONLY || policy == PrefetchPolicy.SENSITIVE_MEMORY_ONLY) {
                val now = SystemClock.elapsedRealtime()
                while (networkStarts.isNotEmpty() && now - networkStarts.first() > NETWORK_WINDOW_MS) networkStarts.removeFirst()
                if (networkStarts.size >= MAX_NETWORK_STARTS_PER_WINDOW) return
                val estimatedBytes = estimatedBytes(action)
                if (failuresThisSession >= MAX_FAILURES_PER_SESSION ||
                    estimatedNetworkBytesThisSession + estimatedBytes > MAX_ESTIMATED_BYTES_PER_SESSION
                ) return
                if (action == AppActionId.OPEN_PAYMENT_QR) {
                    if (paymentQrStartsThisSession >= MAX_PAYMENT_QR_STARTS_PER_SESSION ||
                        now - lastPaymentQrStartElapsedMs < PAYMENT_QR_MIN_INTERVAL_MS
                    ) return
                    paymentQrStartsThisSession++
                    lastPaymentQrStartElapsedMs = now
                }
                networkStarts.addLast(now)
                estimatedNetworkBytesThisSession += estimatedBytes
            }
            val started = SystemClock.elapsedRealtime()
            _diagnostics.value = _diagnostics.value + (key to PrefetchDiagnostic(key, PrefetchState.RUNNING, started, null, null))
            val task = scope.async {
                val result = try {
                    if (policy == PrefetchPolicy.LOCAL_ONLY) localSlots.withPermit { execute(action) }
                    else networkSlots.withPermit { execute(action) }
                } catch (cancelled: CancellationException) {
                    _diagnostics.value = _diagnostics.value +
                        (key to PrefetchDiagnostic(key, PrefetchState.CANCELLED, started, SystemClock.elapsedRealtime(), null))
                    inFlight.remove(key)
                    throw cancelled
                } catch (error: Throwable) {
                    AhuResult.Failure(error.toAhuError())
                }
                val finished = SystemClock.elapsedRealtime()
                if (result.isFailure &&
                    (policy == PrefetchPolicy.NETWORK_READ_ONLY || policy == PrefetchPolicy.SENSITIVE_MEMORY_ONLY)
                ) {
                    mutex.withLock { failuresThisSession++ }
                }
                if (result.isSuccess) freshUntilElapsedMs[key] = finished + ttlMillis(action)
                _diagnostics.value = _diagnostics.value + (
                    key to PrefetchDiagnostic(
                        key,
                        if (result.isSuccess) PrefetchState.SUCCEEDED else PrefetchState.FAILED,
                        started,
                        finished,
                        result.errorOrNull()?.let { it::class.java.simpleName }
                    )
                )
                inFlight.remove(key)
                result
            }
            inFlight[key] = task
        }
    }

    private suspend fun execute(action: AppActionId): AhuResult<Unit> = when (action) {
        AppActionId.VIEW_SCHEDULE -> AHURepository.getSchedule(false).map { Unit }
        AppActionId.VIEW_GRADES -> AHURepository.getGrade(false).map { Unit }
        AppActionId.VIEW_EXAM_ROOM -> {
            val user = SessionStore.currentUser()
            val studentId = AHUCache.getJwxtStudentId() ?: user?.xh
            val name = user?.name
            if (studentId.isNullOrBlank() || name.isNullOrBlank()) {
                AhuResult.Failure(AhuError.ProtocolChanged("exam identity is not ready"))
            } else {
                AHURepository.getExamInfo(false, studentId, name).map { Unit }
            }
        }
        AppActionId.VIEW_SCHOOL_CALENDAR -> runCatching {
            val result = AHURepository.getSchoolCalendar()
            check(result.isSuccess) { "calendar prefetch rejected" }
            val response = checkNotNull(result.valueOrNull()) { "calendar response is missing" }
            check(response.isSuccessful) { "calendar download failed" }
            val body = checkNotNull(response.body()) { "calendar body is missing" }
            val file = FileUtils.saveResponseBodyToFile(context, body, "xiaoli.jpg")
            check(file != null && file.length() > 0L) { "calendar cache write failed" }
        }.toAhuResult()
        AppActionId.OPEN_LOST_FOUND -> runCatching {
            val result = AHURepository.getLostFoundList(1, 20, 1)
            check(result.isSuccess) { "lost-found prefetch rejected" }
            val response = checkNotNull(result.valueOrNull()) { "lost-found response is missing" }
            AHUCache.saveLostFoundList(1, response.data.list)
        }.toAhuResult()
        AppActionId.OPEN_PAYMENT_QR -> paymentQrRepository.prefetchPredictively().toAhuResult()
        else -> AhuResult.Failure(
            AhuError.ProtocolChanged("no audited prefetcher for ${action.stableId}")
        )
    }

    private fun threshold(policy: PrefetchPolicy): Float = when (policy) {
        PrefetchPolicy.LOCAL_ONLY -> 0.20f
        PrefetchPolicy.NETWORK_READ_ONLY -> 0.38f
        PrefetchPolicy.SENSITIVE_MEMORY_ONLY -> 0.65f
        PrefetchPolicy.NONE -> 1f
    }

    private suspend fun canPrefetch(holdout: Boolean, foreground: Boolean): Boolean {
        if (holdout || !foreground || !preferencesManager.predictivePrefetchEnabled.first()) return false
        if (!resourcePolicyAllowsPrefetch()) return false
        return !preferencesManager.wifiOnlyPrefetch.first() || isWifiConnected()
    }

    private fun isWifiConnected(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun resourcePolicyAllowsPrefetch(): Boolean {
        val battery = context.getSystemService(BatteryManager::class.java)
        if (battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.let { it in 0..14 } == true) {
            return false
        }
        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isPowerSaveMode == true) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            power?.currentThermalStatus?.let { it >= PowerManager.THERMAL_STATUS_SEVERE } == true
        ) return false
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        ) return false
        return true
    }

    private fun estimatedBytes(action: AppActionId): Long = when (action) {
        AppActionId.VIEW_SCHEDULE -> 256 * 1024L
        AppActionId.VIEW_GRADES -> 192 * 1024L
        AppActionId.VIEW_EXAM_ROOM -> 128 * 1024L
        AppActionId.VIEW_SCHOOL_CALENDAR -> 128 * 1024L
        AppActionId.OPEN_LOST_FOUND -> 256 * 1024L
        AppActionId.OPEN_PAYMENT_QR -> 16 * 1024L
        else -> 64 * 1024L
    }

    private fun ttlMillis(action: AppActionId): Long = when (action) {
        AppActionId.VIEW_SCHEDULE -> 6 * 60 * 60_000L
        AppActionId.VIEW_EXAM_ROOM -> 60 * 60_000L
        AppActionId.VIEW_SCHOOL_CALENDAR -> 24 * 60 * 60_000L
        AppActionId.VIEW_GRADES -> 15 * 60_000L
        AppActionId.OPEN_LOST_FOUND -> 5 * 60_000L
        AppActionId.OPEN_PAYMENT_QR -> 15_000L
        else -> 60_000L
    }

    private companion object {
        const val NETWORK_WINDOW_MS = 5 * 60_000L
        const val MAX_NETWORK_STARTS_PER_WINDOW = 3
        const val PAYMENT_QR_MIN_INTERVAL_MS = 60_000L
        const val MAX_PAYMENT_QR_STARTS_PER_SESSION = 2
        const val MAX_FAILURES_PER_SESSION = 3
        const val MAX_ESTIMATED_BYTES_PER_SESSION = 2 * 1024 * 1024L
    }
}
