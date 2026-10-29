package com.ahu.ahutong.personalization.journey

import android.util.Log
import com.ahu.ahutong.personalization.action.ActionSource
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.context.ContextSnapshotCodec
import com.ahu.ahutong.personalization.context.ImmutableBooleanVector
import com.ahu.ahutong.personalization.context.ImmutableFloatVector
import com.ahu.ahutong.personalization.context.PredictionInput
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.JourneyShadowEvaluationEntity
import com.ahu.ahutong.personalization.storage.JourneyTrainingSampleEntity
import com.ahu.ahutong.personalization.storage.PendingJourneyEntity
import com.ahu.ahutong.personalization.storage.transaction
import com.ahu.ahutong.personalization.telemetry.TelemetryAggregateStore
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class JourneyRecommendation(
    val journeyId: String,
    val action: AppActionId,
    val probability: Float,
    val holdout: Boolean
)

@Singleton
class JourneyPredictionEngine @Inject constructor(
    private val database: BehaviorDatabase,
    private val dao: BehaviorDao,
    private val frequencyPredictor: JourneyFrequencyPredictor,
    private val tinyPredictor: TinyJourneyMlpPredictor,
    private val trainer: JourneyOnDeviceTrainer,
    private val promotionManager: JourneyPromotionManager,
    private val modelStore: JourneyModelStateStore,
    private val telemetryAggregateStore: TelemetryAggregateStore
) {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Background journey task failed", error)
        }
    )
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val deadlineJobs = ConcurrentHashMap<String, Job>()
    private val dwellJobs = ConcurrentHashMap<String, Job>()

    suspend fun start(
        profileKey: String,
        sessionId: String,
        processInstanceId: String,
        sequenceNo: Long,
        triggerEventId: String,
        input: PredictionInput,
        tainted: Boolean,
        holdout: Boolean
    ): JourneyRecommendation? = locks.getOrPut(profileKey) { Mutex() }.withLock {
        dao.latestPendingJourney(profileKey)?.let { existing ->
            dao.censorJourneyCas(existing.journeyId, profileKey, "REPLACED_BY_STRONG_CONTEXT")
            cancelJobs(existing.journeyId)
        }
        val (stat, tiny) = coroutineScope {
            val statDeferred = async { frequencyPredictor.predict(input) }
            val tinyDeferred = async { runCatching { tinyPredictor.predict(input) }.getOrNull() }
            statDeferred.await() to tinyDeferred.await()
        }
        val promotion = promotionManager.snapshot(profileKey)
        val model = modelStore.state(profileKey)
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val pending = PendingJourneyEntity(
            journeyId = UUID.randomUUID().toString(),
            profileKey = profileKey,
            sessionId = sessionId,
            processInstanceId = processInstanceId,
            triggerEventId = triggerEventId,
            sequenceNo = sequenceNo,
            createdAtEpochMs = System.currentTimeMillis(),
            createdAtElapsedMs = nowElapsed,
            deadlineElapsedMs = nowElapsed + JOURNEY_WINDOW_MS,
            maximumActions = MAX_ACTIONS,
            observedActionCount = 0,
            observedActionIdsCsv = "",
            lastLeafEnteredAtElapsedMs = null,
            lastLeafActionId = null,
            lastLeafEventId = null,
            featureSchemaVersion = input.featureSchemaVersion,
            journeyOutputSchemaVersion = JourneyGoalCatalog.OUTPUT_SCHEMA_VERSION,
            features = input.features.toBytes(),
            inputDigest = input.inputDigest,
            contextSnapshotJson = ContextSnapshotCodec.encode(input.snapshot),
            statProbabilities = BinaryCodec.floats(stat.probabilities),
            tinyProbabilities = tiny?.let { BinaryCodec.floats(it.probabilities) },
            statModelVersion = stat.modelVersion,
            tinyModelVersion = tiny?.modelVersion,
            statInferenceNanos = stat.inferenceNanos,
            tinyInferenceNanos = tiny?.inferenceNanos,
            activeCheckpointId = model.active.checkpointId,
            candidateCheckpointId = model.candidate?.checkpointId,
            stageAtDecision = promotion.stage.name,
            mixedLambda = promotion.lambda,
            isPromotionHoldout = holdout,
            interventionState = if (tainted) "TAINTED_CHAIN" else "NONE",
            resolutionStatus = "PENDING",
            censorReason = null,
            finalTargetActionId = null,
            consumedTerminalEventId = null
        )
        dao.insertPendingJourney(pending)
        scheduleDeadline(pending)
        recommendation(pending)
    }

    suspend fun onAction(
        profileKey: String,
        sessionId: String,
        action: AppActionId,
        source: ActionSource,
        eventId: String,
        elapsedMs: Long
    ) = locks.getOrPut(profileKey) { Mutex() }.withLock {
        val pending = dao.latestPendingJourney(profileKey) ?: return@withLock
        if (pending.sessionId != sessionId) return@withLock
        val spec = AppActionCatalog.spec(action)
        if (source != ActionSource.ORGANIC || spec.sideEffect == SideEffect.TRANSACTION) {
            dao.censorJourneyCas(pending.journeyId, profileKey, "TAINTED_${source.name}_${spec.sideEffect.name}")
            cancelJobs(pending.journeyId)
            return@withLock
        }
        val path = pending.observedActionIdsCsv.split(',').filter(String::isNotBlank) + action.stableId
        val count = pending.observedActionCount + 1
        if (count > pending.maximumActions) {
            resolve(
                pending,
                JourneyGoalCatalog.NONE_OUTPUT_ID,
                eventId,
                JourneyTrainingLabelPolicy.INTERVENTION_FREE_MAX_STEPS
            )
            return@withLock
        }
        val updated = pending.copy(
            observedActionCount = count,
            observedActionIdsCsv = path.joinToString(","),
            lastLeafEnteredAtElapsedMs = if (JourneyGoalCatalog.isSafeTerminal(action)) elapsedMs else null,
            lastLeafActionId = action.stableId.takeIf { JourneyGoalCatalog.isSafeTerminal(action) },
            lastLeafEventId = eventId.takeIf { JourneyGoalCatalog.isSafeTerminal(action) }
        )
        dao.updatePendingJourney(updated)
        when {
            JourneyGoalCatalog.isImmediateMilestone(action) -> resolve(
                updated,
                action.stableId,
                eventId,
                JourneyTrainingLabelPolicy.ORGANIC_JOURNEY
            )
            JourneyGoalCatalog.isSafeTerminal(action) -> scheduleDwell(updated, action, eventId)
            count == pending.maximumActions -> resolve(
                updated,
                JourneyGoalCatalog.NONE_OUTPUT_ID,
                eventId,
                JourneyTrainingLabelPolicy.INTERVENTION_FREE_MAX_STEPS
            )
        }
    }

    suspend fun onExplicitMilestone(profileKey: String, target: AppActionId, eventId: String) =
        locks.getOrPut(profileKey) { Mutex() }.withLock {
            if (!JourneyGoalCatalog.isSafeTerminal(target)) return@withLock
            dao.latestPendingJourney(profileKey)?.let {
                resolve(it, target.stableId, eventId, JourneyTrainingLabelPolicy.ORGANIC_JOURNEY)
            }
        }

    suspend fun censorProfile(profileKey: String, reason: String) = locks.getOrPut(profileKey) { Mutex() }.withLock {
        dao.latestPendingJourney(profileKey)?.let { pending ->
            dao.censorJourneyCas(pending.journeyId, profileKey, reason)
            cancelJobs(pending.journeyId)
        }
    }

    suspend fun recoverProfile(profileKey: String, processInstanceId: String) {
        dao.censorStaleProcessJourneys(profileKey, processInstanceId)
        trainer.resumeProfile(profileKey)
    }

    suspend fun clearProfile(profileKey: String) {
        dao.latestPendingJourney(profileKey)?.let { cancelJobs(it.journeyId) }
        trainer.cancelProfile(profileKey)
        modelStore.reset(profileKey)
    }

    suspend fun runIdleTrainingSlice(budgetMillis: Long): JourneyTrainingSliceResult {
        val result = trainer.runIdleSlice(budgetMillis)
        val activeProfile = result.profileKey
        if (activeProfile != null && result.elapsedNanos > MAX_TRAINING_SLICE_NANOS) {
            promotionManager.markUnhealthy(activeProfile, "TRAINING_SLICE_BUDGET_EXCEEDED")
        }
        return result
    }

    suspend fun currentRecommendation(profileKey: String): JourneyRecommendation? {
        val pending = dao.latestPendingJourney(profileKey) ?: return null
        return recommendation(pending)
    }

    private fun scheduleDwell(pending: PendingJourneyEntity, action: AppActionId, eventId: String) {
        dwellJobs.remove(pending.journeyId)?.cancel()
        dwellJobs[pending.journeyId] = scope.launch {
            delay(LEAF_DWELL_MS)
            locks.getOrPut(pending.profileKey) { Mutex() }.withLock {
                val current = dao.pendingJourney(pending.journeyId) ?: return@withLock
                if (current.resolutionStatus == "PENDING" && current.lastLeafActionId == action.stableId &&
                    current.lastLeafEventId == eventId
                ) resolve(
                    current,
                    action.stableId,
                    eventId,
                    JourneyTrainingLabelPolicy.ORGANIC_JOURNEY
                )
            }
        }
    }

    private fun scheduleDeadline(pending: PendingJourneyEntity) {
        deadlineJobs.remove(pending.journeyId)?.cancel()
        deadlineJobs[pending.journeyId] = scope.launch {
            val remaining = (pending.deadlineElapsedMs - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)
            delay(remaining + 250)
            locks.getOrPut(pending.profileKey) { Mutex() }.withLock {
                val current = dao.pendingJourney(pending.journeyId) ?: return@withLock
                if (current.resolutionStatus == "PENDING" && current.interventionState == "NONE") {
                    resolve(
                        current,
                        JourneyGoalCatalog.NONE_OUTPUT_ID,
                        UUID.randomUUID().toString(),
                        JourneyTrainingLabelPolicy.INTERVENTION_FREE_TIMEOUT
                    )
                }
            }
        }
    }

    private suspend fun resolve(
        pending: PendingJourneyEntity,
        targetActionId: String,
        terminalEventId: String,
        labelSource: String
    ) {
        if (pending.resolutionStatus != "PENDING" || pending.interventionState != "NONE") return
        val targetIndex = JourneyGoalCatalog.outputIndex[targetActionId] ?: return
        val input = restoreInput(pending)
        database.transaction {
            check(dao.resolveJourneyCas(pending.journeyId, pending.profileKey, targetActionId, terminalEventId) == 1)
            frequencyPredictor.update(input, targetActionId)
            val shadowEvaluation = evaluation(pending, targetActionId)
            dao.insertJourneyEvaluation(shadowEvaluation)
            telemetryAggregateStore.contributeJourney(shadowEvaluation)
            trainer.enqueue(
                JourneyTrainingSampleEntity(
                    sampleId = UUID.randomUUID().toString(),
                    profileKey = pending.profileKey,
                    journeyId = pending.journeyId,
                    featureSchemaVersion = pending.featureSchemaVersion,
                    journeyOutputSchemaVersion = pending.journeyOutputSchemaVersion,
                    features = pending.features,
                    targetIndex = targetIndex,
                    targetActionId = targetActionId,
                    targetFamily = AppActionId.fromStableId(targetActionId)?.let { AppActionCatalog.spec(it).family.name } ?: "TECHNICAL",
                    journeyLength = pending.observedActionCount,
                    occurredEpochDay = input.snapshot.epochDay,
                    replayPriority = replayPriority(pending.journeyId, targetActionId),
                    trainingCount = 0,
                    labelSource = labelSource
                )
            )
        }
        cancelJobs(pending.journeyId)
        promotionManager.evaluate(pending.profileKey)
    }

    private suspend fun recommendation(pending: PendingJourneyEntity): JourneyRecommendation? {
        if (pending.isPromotionHoldout || pending.interventionState != "NONE") return null
        val stat = BinaryCodec.floats(pending.statProbabilities)
        val tiny = pending.tinyProbabilities?.let(BinaryCodec::floats)
        val probabilities = FloatArray(stat.size) { index ->
            (1f - pending.mixedLambda) * stat[index] + pending.mixedLambda * (tiny?.get(index) ?: stat[index])
        }
        val index = probabilities.indices
            .filter { JourneyGoalCatalog.outputIds[it] !in setOf(JourneyGoalCatalog.NONE_OUTPUT_ID, JourneyGoalCatalog.OTHER_OUTPUT_ID) }
            .maxByOrNull { probabilities[it] } ?: return null
        val action = AppActionId.fromStableId(JourneyGoalCatalog.outputIds[index]) ?: return null
        if (probabilities[index] < RECOMMENDATION_THRESHOLD || !AppActionCatalog.spec(action).suggestible) return null
        return JourneyRecommendation(pending.journeyId, action, probabilities[index], pending.isPromotionHoldout)
    }

    private suspend fun evaluation(pending: PendingJourneyEntity, targetActionId: String): JourneyShadowEvaluationEntity {
        val stat = BinaryCodec.floats(pending.statProbabilities)
        val tiny = pending.tinyProbabilities?.let(BinaryCodec::floats)
        val effective = FloatArray(stat.size) { index ->
            (1f - pending.mixedLambda) * stat[index] +
                pending.mixedLambda * (tiny?.get(index) ?: stat[index])
        }
        val target = JourneyGoalCatalog.outputIndex.getValue(targetActionId)
        fun rank(values: FloatArray): Int = values.indices.sortedWith(compareByDescending<Int> { values[it] }.thenBy { it }).indexOf(target) + 1
        fun brier(values: FloatArray): Double = values.indices.sumOf { index ->
            val expected = if (index == target) 1.0 else 0.0
            val delta = values[index] - expected
            delta * delta
        } / values.size
        fun logLoss(values: FloatArray): Double = -ln(values[target].coerceAtLeast(1e-7f).toDouble())
        val statRank = rank(stat)
        val tinyRank = tiny?.let(::rank) ?: Int.MAX_VALUE
        val effectiveRank = rank(effective)
        return JourneyShadowEvaluationEntity(
            profileKey = pending.profileKey,
            evaluationSeq = dao.maxJourneyEvaluationSeq(pending.profileKey) + 1,
            journeyId = pending.journeyId,
            occurredEpochDay = ContextSnapshotCodec.decode(pending.contextSnapshotJson).epochDay,
            trueLabel = targetActionId,
            journeyLength = pending.observedActionCount,
            statTop1 = if (statRank == 1) 1 else 0,
            statTop3 = if (statRank in 1..3) 1 else 0,
            statReciprocalRank = 1.0 / statRank,
            statBrier = brier(stat),
            statLogLoss = logLoss(stat),
            statTop1Confidence = stat.maxOrNull()?.toDouble() ?: 0.0,
            tinyTop1 = if (tinyRank == 1) 1 else 0,
            tinyTop3 = if (tinyRank in 1..3) 1 else 0,
            tinyReciprocalRank = if (tiny == null) 0.0 else 1.0 / tinyRank,
            tinyBrier = tiny?.let(::brier) ?: 1.0,
            tinyLogLoss = tiny?.let(::logLoss) ?: 50.0,
            tinyTop1Confidence = tiny?.maxOrNull()?.toDouble() ?: 0.0,
            effectiveTop1 = if (effectiveRank == 1) 1 else 0,
            effectiveTop3 = if (effectiveRank in 1..3) 1 else 0,
            effectiveReciprocalRank = 1.0 / effectiveRank,
            effectiveBrier = brier(effective),
            effectiveLogLoss = logLoss(effective),
            effectiveTop1Confidence = effective.maxOrNull()?.toDouble() ?: 0.0,
            tinyAvailable = tiny != null,
            promotionEligible = pending.isPromotionHoldout && pending.interventionState == "NONE",
            tinyCheckpointId = pending.candidateCheckpointId ?: pending.activeCheckpointId,
            statInferenceNanos = pending.statInferenceNanos,
            tinyInferenceNanos = pending.tinyInferenceNanos ?: 0,
            stage = pending.stageAtDecision,
            featureSchemaVersion = pending.featureSchemaVersion,
            journeyOutputSchemaVersion = pending.journeyOutputSchemaVersion
        )
    }

    private fun restoreInput(pending: PendingJourneyEntity): PredictionInput = PredictionInput(
        pending.profileKey,
        pending.journeyId,
        pending.featureSchemaVersion,
        AppActionCatalog.OUTPUT_SCHEMA_VERSION,
        AppActionCatalog.ACTION_CATALOG_VERSION,
        ImmutableFloatVector(BinaryCodec.floats(pending.features)),
        ImmutableBooleanVector(BooleanArray(AppActionCatalog.outputIds.size) { true }),
        pending.inputDigest,
        ContextSnapshotCodec.decode(pending.contextSnapshotJson)
    )

    private fun replayPriority(journeyId: String, target: String): Float {
        val digest = MessageDigest.getInstance("SHA-256").digest("$journeyId|$target".toByteArray())
        return ((digest[0].toInt() and 0xff) / 255f) + if (target == JourneyGoalCatalog.NONE_OUTPUT_ID) 0f else 1f
    }

    private fun cancelJobs(journeyId: String) {
        deadlineJobs.remove(journeyId)?.cancel()
        dwellJobs.remove(journeyId)?.cancel()
    }

    private companion object {
        const val TAG = "JourneyPrediction"
        const val JOURNEY_WINDOW_MS = 120_000L
        const val MAX_ACTIONS = 5
        const val LEAF_DWELL_MS = 4_000L
        const val RECOMMENDATION_THRESHOLD = 0.70f
        const val MAX_TRAINING_SLICE_NANOS = 50_000_000L
    }
}
