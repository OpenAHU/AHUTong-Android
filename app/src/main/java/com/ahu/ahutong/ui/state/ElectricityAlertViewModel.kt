package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.model.ElectricityAlertRoom
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityUsageSource
import com.ahu.ahutong.data.session.AhuSessionState
import com.ahu.ahutong.data.session.SessionIdentity
import com.ahu.ahutong.electricity.ElectricityAlertChecker
import com.ahu.ahutong.electricity.LocalElectricityAlertStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class ElectricityAlertViewModel @Inject constructor(
    private val store: LocalElectricityAlertStore,
    source: ElectricityUsageSource,
    private val identity: SessionIdentity
) : ViewModel() {
    @Volatile private var activeProfile: String? = null
    private val sessionMutex = Mutex()
    private var checkJob: Job? = null
    private var showJob: Job? = null
    private var refreshRequested = false
    private val checker = ElectricityAlertChecker(store.files, source) { profile ->
        activeProfile == profile && identity.currentUser()?.xh == profile &&
            AhuSessionState.status.value != AhuSessionState.Status.Expired
    }
    private val _pending = MutableStateFlow<List<ElectricityAlertRoom>>(emptyList())
    val pending = _pending.asStateFlow()
    private val _presented = MutableStateFlow<List<ElectricityAlertRoom>>(emptyList())
    val presented = _presented.asStateFlow()
    private val _rechargeSelection = MutableStateFlow<RoomSelectionInfo?>(null)
    val rechargeSelection = _rechargeSelection.asStateFlow()

    init {
        viewModelScope.launch {
            store.configuration.collect { configuration ->
                if (!configuration.enabled) {
                    checkJob?.cancel()
                    dismiss()
                }
                _presented.value = _presented.value.filter { shown ->
                    configuration.rooms.any { it == shown }
                }
                requestRefresh()
            }
        }
    }

    /** Called on successful login, navigation and foreground return (including a day rollover). */
    fun resumeSession(usable: Boolean) {
        viewModelScope.launch {
            sessionMutex.withLock {
                val profile = identity.currentUser()?.xh?.takeIf { usable && it.isNotBlank() }
                if (activeProfile != profile) {
                    activeProfile = profile
                    checkJob?.cancel()
                    dismiss()
                    _rechargeSelection.value = null
                }
                store.activateProfile(profile)
            }
            requestRefresh()
        }
    }

    private fun requestRefresh() {
        if (activeProfile == null || !store.configuration.value.enabled) return
        refreshRequested = true
        if (checkJob?.isActive == true) return
        // Navigation may dispose its LaunchedEffect while a request is in flight. Keep the
        // daily check in ViewModel scope; only disabling alerts or changing account cancels it.
        checkJob = viewModelScope.launch {
            do {
                refreshRequested = false
                refresh()
            } while (refreshRequested)
        }
    }

    private suspend fun refresh() {
        val profile = activeProfile ?: return
        val configuration = store.configuration.value
        val rooms = withContext(Dispatchers.IO) {
            try { checker.check(profile, configuration, LocalDate.now()) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { emptyList() }
        }
        if (profile == activeProfile) {
            _pending.value = rooms.filter { room ->
                store.configuration.value.enabled && store.configuration.value.rooms.any { it == room }
            }
        }
    }

    fun showPending() {
        if (showJob?.isActive == true) return
        // Persisting the daily claim and retaining its dialog must survive a QR opening or
        // lifecycle pause. The host will defer rendering until it is safe to show again.
        showJob = viewModelScope.launch { presentPending() }
    }

    private suspend fun presentPending() {
        val profile = activeProfile ?: return
        val rooms = _pending.value
        if (rooms.isEmpty() || _presented.value.isNotEmpty()) return
        val claimed = withContext(Dispatchers.IO) {
            try { checker.claimPrompt(profile, LocalDate.now()) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { false }
        }
        if (claimed && profile == activeProfile && store.configuration.value.enabled) {
            _presented.value = rooms.filter { it in store.configuration.value.rooms }
            _pending.value = emptyList()
        }
    }

    fun dismiss() {
        _pending.value = emptyList()
        _presented.value = emptyList()
    }

    fun recharge(room: ElectricityAlertRoom) {
        _rechargeSelection.value = room.selection
        dismiss()
    }

    fun selectionConsumed() { _rechargeSelection.value = null }
}
