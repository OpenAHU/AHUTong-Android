package com.ahu.ahutong.personalization.prefetch

import android.os.SystemClock
import com.ahu.ahutong.personalization.action.ActionSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PaymentQrOpenCommand(
    val commandId: String,
    val executionId: String,
    val decisionId: String,
    val profileGeneration: Long,
    val loginGeneration: Long,
    val expiresAtElapsedMs: Long,
    val source: ActionSource
)

@Singleton
class PaymentQrOpenCommandStore @Inject constructor() {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()
    fun setVisible(visible: Boolean) { _visible.value = visible }
    private val _command = MutableStateFlow<PaymentQrOpenCommand?>(null)
    val command: StateFlow<PaymentQrOpenCommand?> = _command.asStateFlow()
    private var activeProfileGeneration: Long = 0L
    private var activeLoginGeneration: Long = 0L

    @Synchronized
    fun activate(profileGeneration: Long, loginGeneration: Long) {
        if (activeProfileGeneration != profileGeneration || activeLoginGeneration != loginGeneration) {
            _command.value = null
            activeProfileGeneration = profileGeneration
            activeLoginGeneration = loginGeneration
        }
    }

    @Synchronized
    fun publish(executionId: String, decisionId: String, source: ActionSource) {
        _command.value = PaymentQrOpenCommand(
            UUID.randomUUID().toString(), executionId, decisionId,
            activeProfileGeneration, activeLoginGeneration,
            SystemClock.elapsedRealtime() + COMMAND_TTL_MS, source
        )
    }

    @Synchronized
    fun consume(commandId: String): PaymentQrOpenCommand? {
        val current = _command.value ?: return null
        if (current.commandId != commandId || current.expiresAtElapsedMs <= SystemClock.elapsedRealtime() ||
            current.profileGeneration != activeProfileGeneration ||
            current.loginGeneration != activeLoginGeneration
        ) {
            _command.value = null
            return null
        }
        _command.value = null
        return current
    }

    @Synchronized fun clear() { _command.value = null }

    private companion object { const val COMMAND_TTL_MS = 10_000L }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PaymentQrCommandEntryPoint {
    fun paymentQrOpenCommandStore(): PaymentQrOpenCommandStore
}
