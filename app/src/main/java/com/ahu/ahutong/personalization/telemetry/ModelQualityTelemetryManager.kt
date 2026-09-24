package com.ahu.ahutong.personalization.telemetry

import android.content.Context
import android.util.Base64
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ahu.ahutong.data.dao.PreferencesManager
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.TelemetryAggregateWindowEntity
import com.ahu.ahutong.personalization.storage.TelemetryDeletionTombstoneEntity
import com.ahu.ahutong.personalization.storage.TelemetryReportEntity
import com.ahu.ahutong.personalization.storage.TelemetryStateEntity
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ModelQualityTelemetryManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: BehaviorDao,
    private val preferences: PreferencesManager,
    private val secretStore: TelemetrySecretStore,
    private val aggregateStore: TelemetryAggregateStore
) {
    private val gson = Gson()
    private val secureRandom = SecureRandom()

    suspend fun setConsent(profileKey: String?, enabled: Boolean, localModelGenerationVersion: Long? = null) {
        if (profileKey == null) return
        preferences.setModelQualityTelemetryEnabled(profileKey, enabled)
        if (enabled) state(profileKey, localModelGenerationVersion ?: 1L) else revoke(profileKey, deleteRemote = true)
    }

    suspend fun isConsentEnabled(profileKey: String): Boolean =
        preferences.modelQualityTelemetryEnabled(profileKey).first()

    suspend fun reconcileProfile(profileKey: String, localModelGenerationVersion: Long) {
        val enabled = isConsentEnabled(profileKey)
        val persisted = dao.telemetryState(profileKey)
        when {
            !enabled && persisted != null -> revoke(profileKey, deleteRemote = true)
            enabled && persisted == null -> state(profileKey, localModelGenerationVersion)
            enabled && persisted != null && persisted.localModelGenerationVersion != localModelGenerationVersion ->
                rotateModelGeneration(persisted, localModelGenerationVersion)
        }
        if (dao.pendingDeletionTombstones(System.currentTimeMillis(), 1).isNotEmpty()) scheduleUpload()
    }

    suspend fun onNewEvaluation(profileKey: String) {
        if (!isConsentEnabled(profileKey)) return
        if (TELEMETRY_SERVER_SCHEMA_VERSION >= 3) {
            maybeCreateV3Report(profileKey)
        } else {
            maybeCreateReport(profileKey)
        }
    }

    suspend fun revoke(profileKey: String, deleteRemote: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val state = dao.telemetryState(profileKey)
        if (state == null) {
            dao.deleteTelemetryReports(profileKey)
            dao.deleteTelemetryAggregateWindows(profileKey)
            dao.deleteTelemetryV3AggregateWindows(profileKey)
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        if (deleteRemote) {
            val now = System.currentTimeMillis()
            val tombstone = TelemetryDeletionTombstoneEntity(
                deletionId = UUID.randomUUID().toString(),
                consentLifecycleId = state.consentLifecycleId,
                telemetryId = state.telemetryId,
                modelGenerationId = state.modelGenerationId,
                encryptedRevocationCapability = state.encryptedRevocationCapability,
                revocationKeyAlias = state.revocationKeyAlias,
                attemptCount = 0,
                nextAttemptEpochMs = now,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + TOMBSTONE_TTL_MS,
                state = "PENDING"
            )
            dao.revokeTelemetryLifecycle(profileKey, state.consentLifecycleId, tombstone)
        } else {
            dao.deleteTelemetryReports(profileKey)
            dao.deleteTelemetryAggregateWindows(profileKey)
            dao.deleteTelemetryV3AggregateWindows(profileKey)
            dao.deleteTelemetryState(profileKey)
            secretStore.delete(state.revocationKeyAlias)
        }
        workManager.cancelUniqueWork(WORK_NAME)
        if (deleteRemote) scheduleUpload()
    }

    private suspend fun maybeCreateReport(profileKey: String) {
        val state = dao.telemetryState(profileKey) ?: return
        val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        if (state.lastReportCreatedEpochDay == today || state.lifecycleState != "ACTIVE") return
        var window = dao.nextClosedTelemetryAggregateWindow(profileKey, state.consentLifecycleId)
        while (window != null && (
                window.telemetryId != state.telemetryId ||
                    window.eligibleSampleCount < MIN_PAIRED_SAMPLES ||
                    window.pairedSampleCount < MIN_PAIRED_SAMPLES
                )
        ) {
            dao.transitionTelemetryAggregateWindow(
                window.windowId,
                expectedState = "CLOSED",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
            window = dao.nextClosedTelemetryAggregateWindow(profileKey, state.consentLifecycleId)
        }
        window ?: return

        val learning = dao.learningState(profileKey)
        val reportId = UUID.randomUUID().toString()
        val capability = secretStore.decrypt(state.revocationKeyAlias, state.encryptedRevocationCapability)
            ?: return
        val pairwise = PairwiseAggregate(window.tinyWins, window.statWins, window.ties, window.pairedSampleCount)
        val report = ModelQualityEvaluationReport(
            reportId = reportId,
            telemetryId = state.telemetryId,
            modelGenerationId = window.modelGenerationId,
            windowId = window.windowId,
            revocationCapabilityHash = sha256(capability),
            windowStartDay = LocalDate.ofEpochDay(window.windowStartEpochDay).toString(),
            windowEndDay = LocalDate.ofEpochDay(window.windowEndEpochDay).toString(),
            statLearnedDays = learning?.statLearningStartedEpochDay?.let {
                (window.windowEndEpochDay - it + 1).coerceAtLeast(0)
            },
            tinyLearnedDays = learning?.tinyTrainingStartedEpochDay?.let {
                (window.windowEndEpochDay - it + 1).coerceAtLeast(0)
            },
            eligibleSampleCount = window.eligibleSampleCount,
            organicNonNoneSampleCount = window.organicNonNoneSampleCount,
            statistical = window.statAggregate(),
            tinyMlp = window.tinyAggregate(),
            pairwise = pairwise,
            appVersionCode = window.appVersionCode,
            metricSchemaVersion = window.metricSchemaVersion,
            featureSchemaVersion = window.featureSchemaVersion,
            outputSchemaVersion = window.outputSchemaVersion,
            actionCatalogVersion = window.actionCatalogVersion,
            trainingConfigVersion = window.trainingConfigVersion,
            perAction = aggregateStore.readPerAction(window.perActionJson)
                .filter { it.eligibleSampleCount >= MIN_ACTION_SAMPLES && it.pairedSampleCount >= MIN_ACTION_SAMPLES }
                .map { it.toPayload() },
            statInferenceNanosSum = window.statInferenceNanosSum,
            tinyInferenceNanosSum = window.tinyInferenceNanosSum,
            trainingNanosSum = window.trainingNanosSum,
            modelSizeBytesMax = window.modelSizeBytesMax
        )
        TelemetryPayloadValidator.requireValid(report)
        val reportJson = gson.toJson(report)
        val reportSha = sha256(reportJson)
        val batchId = UUID.randomUUID().toString()
        val exactBody = gson.toJson(ModelQualityBatchRequest(batchId = batchId, reports = listOf(report)))
        val bodySha = sha256(exactBody)
        if (exactBody.toByteArray(Charsets.UTF_8).size > MAX_REPORT_BYTES) {
            dao.transitionTelemetryAggregateWindow(
                window.windowId,
                expectedState = "CLOSED",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
            return
        }
        val now = System.currentTimeMillis()
        dao.queueTelemetryReport(
            TelemetryReportEntity(
                reportId = reportId,
                batchId = batchId,
                profileKey = profileKey,
                consentLifecycleId = state.consentLifecycleId,
                telemetryId = state.telemetryId,
                modelGenerationId = window.modelGenerationId,
                windowId = window.windowId,
                payloadJson = reportJson,
                payloadSha256Hex = reportSha,
                exactRequestBodyJson = exactBody,
                bodySha256Hex = bodySha,
                state = "READY",
                attemptCount = 0,
                nextAttemptEpochMs = now,
                lastAttemptEpochDay = null,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + REPORT_TTL_MS
            ),
            state.copy(
                lastReportedEvaluationSeq = window.endEvaluationSeq,
                lastReportCreatedEpochDay = today,
                updatedAtEpochMs = now
            ),
            aggregateWindowId = window.windowId
        )
        dao.trimTelemetryReports(profileKey, MAX_QUEUED_REPORTS)
        dao.deleteOldTerminalTelemetryAggregateWindows(now - TERMINAL_WINDOW_TTL_MS)
        dao.deleteOldTerminalTelemetryV3AggregateWindows(now - TERMINAL_WINDOW_TTL_MS)
        scheduleUpload()
    }

    private suspend fun maybeCreateV3Report(profileKey: String) {
        val state = dao.telemetryState(profileKey) ?: return
        val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        if (state.lastReportCreatedEpochDay == today || state.lifecycleState != "ACTIVE") return
        var window = dao.nextClosedTelemetryV3AggregateWindow(profileKey, state.consentLifecycleId)
        var aggregate: StoredTelemetryV3Aggregate? = null
        while (window != null) {
            val task = runCatching { TelemetryV3Task.valueOf(window.task) }.getOrNull()
            aggregate = task?.let { aggregateStore.readV3Aggregate(window.aggregateJson, it) }
            val validWindow = window.telemetryId == state.telemetryId &&
                window.modelGenerationId == state.modelGenerationId &&
                window.sampleCount >= TELEMETRY_V3_MIN_TASK_SAMPLES &&
                aggregate != null
            if (validWindow) break
            dao.transitionTelemetryV3AggregateWindow(
                window.windowId,
                expectedState = "CLOSED",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
            window = dao.nextClosedTelemetryV3AggregateWindow(profileKey, state.consentLifecycleId)
        }
        window ?: return
        val validAggregate = aggregate ?: return
        val capability = secretStore.decrypt(state.revocationKeyAlias, state.encryptedRevocationCapability)
            ?: return
        val reportId = UUID.randomUUID().toString()
        val report = ModelQualityV3TaskReport(
            reportId = reportId,
            telemetryId = state.telemetryId,
            modelGenerationId = window.modelGenerationId,
            windowId = window.windowId,
            revocationCapabilityHash = sha256(capability),
            task = window.task,
            windowStartDay = LocalDate.ofEpochDay(window.windowStartEpochDay).toString(),
            windowEndDay = LocalDate.ofEpochDay(window.windowEndEpochDay).toString(),
            sampleCount = window.sampleCount,
            naturalHoldoutSampleCount = window.naturalHoldoutSampleCount,
            appVersionCode = window.appVersionCode,
            featureSchemaVersion = window.featureSchemaVersion,
            outputSchemaVersion = window.outputSchemaVersion,
            metricSchemaVersion = window.metricSchemaVersion,
            classification = validAggregate.classification,
            ranking = validAggregate.ranking,
            candidateShadow = validAggregate.candidateShadow,
            delivery = validAggregate.delivery
        )
        TelemetryV3PayloadValidator.requireValid(report)
        val reportJson = gson.toJson(report)
        val batchId = UUID.randomUUID().toString()
        val exactBody = gson.toJson(ModelQualityV3BatchRequest(batchId = batchId, reports = listOf(report)))
        if (exactBody.toByteArray(Charsets.UTF_8).size > MAX_REPORT_BYTES) {
            dao.transitionTelemetryV3AggregateWindow(
                window.windowId,
                expectedState = "CLOSED",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
            return
        }
        val now = System.currentTimeMillis()
        dao.queueTelemetryV3Report(
            TelemetryReportEntity(
                reportId = reportId,
                batchId = batchId,
                profileKey = profileKey,
                consentLifecycleId = state.consentLifecycleId,
                telemetryId = state.telemetryId,
                modelGenerationId = window.modelGenerationId,
                windowId = window.windowId,
                payloadJson = reportJson,
                payloadSha256Hex = sha256(reportJson),
                exactRequestBodyJson = exactBody,
                bodySha256Hex = sha256(exactBody),
                state = "READY",
                attemptCount = 0,
                nextAttemptEpochMs = now,
                lastAttemptEpochDay = null,
                createdAtEpochMs = now,
                expiresAtEpochMs = now + REPORT_TTL_MS,
                schemaVersion = 3
            ),
            state.copy(lastReportCreatedEpochDay = today, updatedAtEpochMs = now),
            aggregateWindowId = window.windowId
        )
        dao.trimTelemetryReports(profileKey, MAX_QUEUED_REPORTS)
        dao.deleteOldTerminalTelemetryV3AggregateWindows(now - TERMINAL_WINDOW_TTL_MS)
        scheduleUpload()
    }

    private suspend fun state(profileKey: String, localModelGenerationVersion: Long): TelemetryStateEntity {
        dao.telemetryState(profileKey)?.let { return it }
        val lifecycleId = UUID.randomUUID().toString()
        val capabilityBytes = ByteArray(32).also(secureRandom::nextBytes)
        val capability = Base64.encodeToString(capabilityBytes, Base64.NO_WRAP or Base64.URL_SAFE)
        val encrypted = secretStore.createAndEncrypt(lifecycleId, capability)
        val startSeq = dao.maxEvaluationSeq(profileKey)
        return TelemetryStateEntity(
            profileKey = profileKey,
            consentLifecycleId = lifecycleId,
            telemetryId = UUID.randomUUID().toString(),
            modelGenerationId = UUID.randomUUID().toString(),
            localModelGenerationVersion = localModelGenerationVersion,
            encryptedRevocationCapability = encrypted.ciphertext,
            revocationKeyAlias = encrypted.alias,
            consentGeneration = 1L,
            aggregationStartEvaluationSeq = startSeq,
            lastReportedEvaluationSeq = startSeq,
            lastReportCreatedEpochDay = null,
            lifecycleState = "ACTIVE",
            updatedAtEpochMs = System.currentTimeMillis()
        ).also { dao.upsertTelemetryState(it) }
    }

    private suspend fun rotateModelGeneration(state: TelemetryStateEntity, localVersion: Long) {
        val highWatermark = dao.maxEvaluationSeq(state.profileKey)
        dao.openTelemetryAggregateWindow(state.profileKey, state.consentLifecycleId)?.let { open ->
            dao.transitionTelemetryAggregateWindow(
                open.windowId,
                expectedState = "OPEN",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
        }
        dao.openTelemetryV3AggregateWindows(state.profileKey).forEach { open ->
            dao.transitionTelemetryV3AggregateWindow(
                open.windowId,
                expectedState = "OPEN",
                state = "SUPPRESSED",
                updatedAtEpochMs = System.currentTimeMillis()
            )
        }
        dao.upsertTelemetryState(
            state.copy(
                modelGenerationId = UUID.randomUUID().toString(),
                localModelGenerationVersion = localVersion,
                aggregationStartEvaluationSeq = highWatermark,
                lastReportedEvaluationSeq = highWatermark,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    private fun TelemetryAggregateWindowEntity.statAggregate() = ModelAggregate(
        statModelVersion,
        statTop1Correct,
        statTop3Hit,
        quantize(statReciprocalRankSum),
        quantize(statBrierSum),
        quantize(statLogLossSum)
    )

    private fun TelemetryAggregateWindowEntity.tinyAggregate() = ModelAggregate(
        tinyModelVersion,
        tinyTop1Correct,
        tinyTop3Hit,
        quantize(tinyReciprocalRankSum),
        quantize(tinyBrierSum),
        quantize(tinyLogLossSum)
    )

    private fun StoredActionMetric.toPayload() = ActionMetricSums(
        actionId = actionId,
        eligibleSampleCount = eligibleSampleCount,
        pairedSampleCount = pairedSampleCount,
        statistical = ModelMetricSums(
            statTop1Correct,
            statTop3Hit,
            quantize(statReciprocalRankSum),
            quantize(statBrierSum),
            quantize(statLogLossSum)
        ),
        tinyMlp = ModelMetricSums(
            tinyTop1Correct,
            tinyTop3Hit,
            quantize(tinyReciprocalRankSum),
            quantize(tinyBrierSum),
            quantize(tinyLogLossSum)
        ),
        pairwise = PairwiseAggregate(tinyWins, statWins, ties, pairedSampleCount)
    )

    private fun quantize(value: Double): Double = kotlin.math.round(value * 1_000_000.0) / 1_000_000.0

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun scheduleUpload() {
        val request = OneTimeWorkRequestBuilder<ModelQualityTelemetryWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "model_quality_telemetry"
        private const val MIN_PAIRED_SAMPLES = 64
        private const val MIN_ACTION_SAMPLES = 30
        private const val MAX_QUEUED_REPORTS = 7
        private const val MAX_REPORT_BYTES = 64 * 1024
        private const val REPORT_TTL_MS = 14L * 86_400_000L
        private const val TERMINAL_WINDOW_TTL_MS = 14L * 86_400_000L
        private const val TOMBSTONE_TTL_MS = 104L * 86_400_000L
    }
}
