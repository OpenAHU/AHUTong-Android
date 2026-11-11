package com.ahu.ahutong.personalization.promotion

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.model.ModelStateStore
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.PromotionActionQualificationEntity
import com.ahu.ahutong.personalization.storage.PromotionEvaluationWindowEntity
import com.ahu.ahutong.personalization.storage.PromotionTransitionJournalEntity
import com.ahu.ahutong.personalization.storage.ShadowEvaluationEntity
import com.ahu.ahutong.personalization.storage.CandidateShadowEvaluationEntity
import com.ahu.ahutong.personalization.storage.TinyPromotionStateEntity
import com.ahu.ahutong.personalization.storage.TinyRuntimeHealthStateEntity
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class PromotionStage { SHADOW, ELIGIBLE, MIXED, PRIMARY }

enum class EffectiveDecisionTier(val lambda: Float) {
    STAT_ONLY(0f),
    MIXED_10(0.10f),
    MIXED_25(0.25f),
    MIXED_50(0.50f),
    PRIMARY(1f)
}

data class PromotionSnapshot(
    val stage: PromotionStage,
    val tier: EffectiveDecisionTier,
    val modelGeneration: Long,
    val holdoutSeed: String,
    val activeCheckpointId: String?,
    val activeChecksum: String?,
    val healthState: String,
    val lastReason: String
) {
    fun lambdaFor(actionId: String, qualifiedTier: EffectiveDecisionTier?): Float {
        if (healthState != "HEALTHY") return 0f
        val allowed = qualifiedTier?.lambda ?: 0f
        return minOf(tier.lambda, allowed)
    }
}

object PromotionStateMachine {
    fun allows(
        fromStage: PromotionStage,
        fromLambda: Float,
        toStage: PromotionStage,
        toLambda: Float
    ): Boolean {
        val fromRank = rank(fromStage, fromLambda)
        val toRank = rank(toStage, toLambda)
        return toRank <= fromRank || toRank == fromRank + 1
    }

    private fun rank(stage: PromotionStage, lambda: Float): Int = when {
        stage == PromotionStage.SHADOW -> 0
        stage == PromotionStage.ELIGIBLE -> 1
        stage == PromotionStage.PRIMARY -> 5
        lambda < 0.25f -> 2
        lambda < 0.50f -> 3
        else -> 4
    }
}

@Singleton
class LocalPromotionManager @Inject constructor(
    private val dao: BehaviorDao,
    private val modelStateStore: ModelStateStore
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun snapshot(profileKey: String): PromotionSnapshot {
        val state = ensureState(profileKey)
        val tier = tierOf(state)
        return PromotionSnapshot(
            PromotionStage.valueOf(state.stage),
            tier,
            state.modelGenerationVersion,
            state.holdoutSeed,
            state.activeCheckpointId,
            state.activeChecksum,
            state.healthState,
            state.lastTransitionReason
        )
    }

    suspend fun actionLambdas(profileKey: String): Map<String, Float> {
        val snapshot = snapshot(profileKey)
        val qualifications = dao.actionQualifications(profileKey).associateBy { it.actionId }
        return AppActionCatalog.outputIds.associateWith { actionId ->
            val tier = qualifications[actionId]?.highestQualifiedTier?.let(::tierByName)
            snapshot.lambdaFor(actionId, tier)
        }
    }

    suspend fun evaluate(profileKey: String) = locks.getOrPut(profileKey) { Mutex() }.withLock {
        var state = ensureState(profileKey)
        evaluateCandidate(profileKey)
        state = ensureState(profileKey)
        val evaluations = dao.promotionEvaluations(profileKey, state.evidenceHighWatermark, 1_000)
            .filter { it.activeCheckpointId == state.activeCheckpointId }
        if (evaluations.size < WINDOW_SIZE) return@withLock
        val window = evaluations.take(WINDOW_SIZE)
        val aggregates = aggregate(window)
        val perActionHealthy = perActionQualification(profileKey, window, state)
        val qualified = qualityQualified(aggregates) && perActionHealthy && resourceQualified(window)
        val windowId = UUID.randomUUID().toString()
        val ece = expectedCalibrationError(window)
        dao.insertPromotionWindow(
            PromotionEvaluationWindowEntity(
                profileKey = profileKey,
                windowId = windowId,
                purpose = "TIER_EVIDENCE",
                stage = state.stage,
                checkpointId = state.activeCheckpointId,
                startEvaluationSeq = window.first().evaluationSeq,
                endEvaluationSeq = window.last().evaluationSeq,
                startEpochDay = window.minOf(ShadowEvaluationEntity::occurredEpochDay),
                endEpochDay = window.maxOf(ShadowEvaluationEntity::occurredEpochDay),
                eligibleSampleCount = window.size,
                organicNonNoneSampleCount = window.count(ShadowEvaluationEntity::isOrganicNonNone),
                pairedSampleCount = window.count(ShadowEvaluationEntity::paired),
                metricsJson = aggregates.toJson(),
                ece = ece,
                perActionDigest = if (perActionHealthy) "PASS" else "REGRESSION",
                inferenceP95Nanos = percentile95(window.map(ShadowEvaluationEntity::tinyInferenceNanos)),
                trainingP95Nanos = percentile95(window.map(ShadowEvaluationEntity::trainingNanos)),
                status = "FROZEN",
                qualified = qualified && ece <= ECE_LIMIT,
                consumedTransitionSequence = null
            )
        )
        val updatedEvidence = state.copy(
            evidenceHighWatermark = window.last().evaluationSeq,
            consecutivePassingWindows = if (qualified && ece <= ECE_LIMIT) state.consecutivePassingWindows + 1 else 0,
            consecutiveFailingWindows = if (qualified && ece <= ECE_LIMIT) 0 else state.consecutiveFailingWindows + 1,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val afterQuality = when {
            updatedEvidence.stage != PromotionStage.SHADOW.name &&
                (updatedEvidence.consecutiveFailingWindows >= 3 || ece > 0.15) ->
                downgradeToShadow(updatedEvidence, "HARD_QUALITY_OR_CALIBRATION_REGRESSION")
            shouldDowngrade(updatedEvidence, ece) ->
                downgradeOne(updatedEvidence, "QUALITY_OR_CALIBRATION_REGRESSION")
            else -> maybePromote(profileKey, updatedEvidence)
        }
        dao.upsertPromotionState(afterQuality)
    }

    suspend fun recordRuntimeFailure(profileKey: String, hard: Boolean, reason: String) =
        locks.getOrPut(profileKey) { Mutex() }.withLock {
            val state = ensureState(profileKey)
            val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
            val rebuilt = if (hard) {
                modelStateStore.reset(profileKey)
                modelStateStore.loadOrCreate(profileKey)
            } else null
            val updated = if (hard) {
                state.copy(
                    stage = PromotionStage.SHADOW.name,
                    mixedLambda = 0f,
                    stageGeneration = state.stageGeneration + 1,
                    transitionSequence = state.transitionSequence + 1,
                    modelGenerationVersion = state.modelGenerationVersion + 1,
                    activeCheckpointId = rebuilt?.active?.checkpointId,
                    activeChecksum = rebuilt?.active?.checksum,
                    candidateCheckpointId = null,
                    trainingRevision = rebuilt?.training?.trainingRevision ?: 0,
                    healthState = "QUARANTINED",
                    cooldownUntilEpochDay = today + HARD_COOLDOWN_DAYS,
                    minimumNewEvidenceSeq = maxOf(state.minimumNewEvidenceSeq, state.evidenceHighWatermark + 1),
                    consecutivePassingWindows = 0,
                    lastTransitionReason = reason,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            } else {
                state.copy(healthState = "LATCHED_STAT_ONLY", lastTransitionReason = reason, updatedAtEpochMs = System.currentTimeMillis())
            }
            if (hard) persistTransition(state, updated) else dao.upsertPromotionState(updated)
        }

    suspend fun recordInferenceAttempt(profileKey: String, checkpointId: String?, success: Boolean, failureCode: String? = null): Boolean {
        val now = System.currentTimeMillis()
        val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        val previous = dao.runtimeHealth(profileKey) ?: TinyRuntimeHealthStateEntity(
            profileKey, 0, 0, today, 0, 0, checkpointId, null, null, null
        )
        val bitMask = (1L shl RECENT_ATTEMPT_WINDOW) - 1L
        val bits = ((previous.recentAttemptBits shl 1) or if (success) 0L else 1L) and bitMask
        val count = (previous.recentAttemptCount + 1).coerceAtMost(RECENT_ATTEMPT_WINDOW)
        val failuresToday = (if (previous.failureEpochDay == today) previous.failuresToday else 0) + if (success) 0 else 1
        dao.upsertRuntimeHealth(
            previous.copy(
                recentAttemptBits = bits,
                recentAttemptCount = count,
                failureEpochDay = today,
                failuresToday = failuresToday,
                totalFailures = previous.totalFailures + if (success) 0 else 1,
                lastCheckpointId = checkpointId,
                lastFailureCode = if (success) previous.lastFailureCode else failureCode,
                lastFailureEpochMs = if (success) previous.lastFailureEpochMs else now,
                lastSuccessEpochMs = if (success) now else previous.lastSuccessEpochMs
            )
        )
        if (success) {
            clearTransientHealthLatch(profileKey)
            return false
        } else {
            val recentFailures = java.lang.Long.bitCount(bits)
            val hard = recentFailures >= MAX_RECENT_FAILURES || failuresToday >= MAX_DAILY_FAILURES
            recordRuntimeFailure(
                profileKey,
                hard = hard,
                reason = failureCode ?: "TINY_INFERENCE_FAILURE"
            )
            return hard
        }
    }

    suspend fun clearTransientHealthLatch(profileKey: String) {
        val state = ensureState(profileKey)
        if (state.healthState == "LATCHED_STAT_ONLY") {
            dao.upsertPromotionState(state.copy(healthState = "HEALTHY", updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    private suspend fun ensureState(profileKey: String): TinyPromotionStateEntity {
        var model = modelStateStore.loadOrCreate(profileKey)
        dao.promotionState(profileKey)?.let { persisted ->
            if (
                persisted.featureSchemaVersion == 3 &&
                FeatureExtractor.FEATURE_SCHEMA_VERSION == 4 &&
                persisted.outputSchemaVersion == AppActionCatalog.OUTPUT_SCHEMA_VERSION &&
                persisted.actionCatalogVersion == AppActionCatalog.ACTION_CATALOG_VERSION &&
                persisted.promotionConfigVersion == PROMOTION_CONFIG_VERSION
            ) {
                if (persisted.activeCheckpointId == model.active.checkpointId) {
                    return persisted.copy(
                        activeCheckpointId = model.active.checkpointId,
                        activeChecksum = model.active.checksum,
                        candidateCheckpointId = model.candidate?.checkpointId,
                        trainingRevision = model.training.trainingRevision,
                        featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
                        lastTransitionReason = "FEATURE_SCHEMA_V3_TO_V4_MIGRATED",
                        updatedAtEpochMs = System.currentTimeMillis()
                    ).also { dao.upsertPromotionState(it) }
                }

                // A successful v3 -> v4 expansion deliberately keeps the checkpoint id.
                // A different id means the old file could not be migrated and loadOrCreate
                // recovered with a fresh model. Preserve accumulated samples/statistics, but
                // never let that unproven replacement inherit the previous promotion stage.
                val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
                return initialState(
                    profileKey = profileKey,
                    activeCheckpointId = model.active.checkpointId,
                    activeChecksum = model.active.checksum,
                    candidateCheckpointId = model.candidate?.checkpointId,
                    trainingRevision = model.training.trainingRevision
                ).copy(
                    holdoutSeed = persisted.holdoutSeed,
                    modelGenerationVersion = persisted.modelGenerationVersion + 1,
                    stageGeneration = persisted.stageGeneration + 1,
                    transitionSequence = persisted.transitionSequence + 1,
                    enteredEpochDay = today,
                    evidenceHighWatermark = persisted.evidenceHighWatermark,
                    featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
                    healthState = "LATCHED_STAT_ONLY",
                    cooldownUntilEpochDay = today + HARD_COOLDOWN_DAYS,
                    minimumNewEvidenceSeq = maxOf(
                        persisted.minimumNewEvidenceSeq,
                        persisted.evidenceHighWatermark + 1
                    ),
                    lastTransitionReason = "FEATURE_SCHEMA_MIGRATION_FAILED_STAT_ONLY"
                ).also { dao.upsertPromotionState(it) }
            }
            if (
                persisted.featureSchemaVersion != FeatureExtractor.FEATURE_SCHEMA_VERSION ||
                persisted.outputSchemaVersion != AppActionCatalog.OUTPUT_SCHEMA_VERSION ||
                persisted.actionCatalogVersion != AppActionCatalog.ACTION_CATALOG_VERSION ||
                persisted.promotionConfigVersion != PROMOTION_CONFIG_VERSION
            ) {
                // Keep aggregate evaluation/telemetry lifecycle state so consent and remote deletion
                // capability survive an app/model schema upgrade. Old windows remain version-bound.
                dao.deletePredictionModelStateForSchema(profileKey)
                modelStateStore.reset(profileKey)
                model = modelStateStore.loadOrCreate(profileKey)
                return initialState(profileKey, model.active.checkpointId, model.active.checksum, model.candidate?.checkpointId, model.training.trainingRevision)
                    .copy(
                        holdoutSeed = persisted.holdoutSeed,
                        modelGenerationVersion = persisted.modelGenerationVersion + 1,
                        stageGeneration = persisted.stageGeneration + 1,
                        transitionSequence = persisted.transitionSequence + 1,
                        lastTransitionReason = "SCHEMA_INCOMPATIBLE_RESET"
                    )
                    .also { dao.upsertPromotionState(it) }
            }
            if (
                persisted.activeCheckpointId == model.active.checkpointId &&
                persisted.activeChecksum == model.active.checksum
            ) {
                if (persisted.candidateCheckpointId != model.candidate?.checkpointId ||
                    persisted.trainingRevision != model.training.trainingRevision
                ) {
                    return persisted.copy(
                        candidateCheckpointId = model.candidate?.checkpointId,
                        trainingRevision = model.training.trainingRevision,
                        updatedAtEpochMs = System.currentTimeMillis()
                    ).also { dao.upsertPromotionState(it) }
                }
                return persisted
            }
            val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
            val recovered = persisted.copy(
                stage = PromotionStage.SHADOW.name,
                mixedLambda = 0f,
                stageGeneration = persisted.stageGeneration + 1,
                transitionSequence = persisted.transitionSequence + 1,
                enteredEpochDay = today,
                modelGenerationVersion = persisted.modelGenerationVersion + 1,
                activeCheckpointId = model.active.checkpointId,
                activeChecksum = model.active.checksum,
                candidateCheckpointId = model.candidate?.checkpointId,
                trainingRevision = model.training.trainingRevision,
                healthState = "HEALTHY",
                cooldownUntilEpochDay = today + HARD_COOLDOWN_DAYS,
                minimumNewEvidenceSeq = maxOf(persisted.minimumNewEvidenceSeq, persisted.evidenceHighWatermark + 1),
                consecutivePassingWindows = 0,
                consecutiveFailingWindows = 0,
                lastTransitionReason = "CHECKPOINT_BINDING_RECOVERY",
                updatedAtEpochMs = System.currentTimeMillis()
            )
            persistTransition(persisted, recovered)
            return recovered
        }
        return initialState(profileKey, model.active.checkpointId, model.active.checksum, model.candidate?.checkpointId, model.training.trainingRevision)
            .also { dao.upsertPromotionState(it) }
    }

    private fun initialState(
        profileKey: String,
        activeCheckpointId: String,
        activeChecksum: String,
        candidateCheckpointId: String?,
        trainingRevision: Long
    ): TinyPromotionStateEntity {
        val now = System.currentTimeMillis()
        return TinyPromotionStateEntity(
            profileKey = profileKey,
            holdoutSeed = UUID.randomUUID().toString(),
            stage = PromotionStage.SHADOW.name,
            mixedLambda = 0f,
            modelGenerationVersion = 1L,
            stageGeneration = 1L,
            transitionSequence = 0L,
            enteredEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            evidenceHighWatermark = 0L,
            activeCheckpointId = activeCheckpointId,
            activeChecksum = activeChecksum,
            candidateCheckpointId = candidateCheckpointId,
            trainingRevision = trainingRevision,
            featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
            outputSchemaVersion = AppActionCatalog.OUTPUT_SCHEMA_VERSION,
            actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
            promotionConfigVersion = PROMOTION_CONFIG_VERSION,
            consecutivePassingWindows = 0,
            consecutiveFailingWindows = 0,
            cooldownUntilEpochDay = 0L,
            minimumNewEvidenceSeq = 1L,
            healthState = "HEALTHY",
            lastTransitionReason = "INITIALIZED_SHADOW",
            updatedAtEpochMs = now
        )
    }

    private suspend fun evaluateCandidate(profileKey: String) {
        val model = modelStateStore.state(profileKey)
        val candidate = model.candidate ?: run {
            dao.deleteCandidateEvaluations(profileKey)
            return
        }
        if (model.candidateCreatedAtEpochMs?.let {
                System.currentTimeMillis() - it >= CANDIDATE_MAX_AGE_MS
            } == true
        ) {
            modelStateStore.discardCandidate(profileKey)
            dao.consumeCandidateEvaluations(profileKey, candidate.checkpointId)
            dao.deleteConsumedCandidateEvaluations(profileKey)
            return
        }
        dao.deleteStaleCandidateEvaluations(profileKey, candidate.checkpointId)
        val evidence = dao.candidateEvaluations(profileKey, candidate.checkpointId)
        if (evidence.size < CANDIDATE_TOTAL_EVIDENCE) return
        val frozen = evidence.take(CANDIDATE_TOTAL_EVIDENCE)
        val qualified = frozen.chunked(CANDIDATE_WINDOW_SIZE).all(::candidateWindowQualified)
        if (qualified) {
            // A quality report must never combine predictions from two active checkpoints.
            // Freeze any incomplete aggregate before the filesystem checkpoint swap.
            dao.suppressOpenTelemetryAggregateWindowsForCheckpointSwap(profileKey, System.currentTimeMillis())
            val activated = modelStateStore.activateCandidate(profileKey, candidate.checkpointId)
            val current = dao.promotionState(profileKey) ?: ensureState(profileKey)
            transition(
                current,
                PromotionStage.SHADOW,
                0f,
                "CANDIDATE_ACTIVATED_REVALIDATION",
                activated.active.checkpointId,
                activated.active.checksum,
                checkpointChanged = true,
                trainingRevision = activated.training.trainingRevision,
                candidateCheckpointId = null
            )
        } else {
            modelStateStore.discardCandidate(profileKey)
        }
        dao.consumeCandidateEvaluations(profileKey, candidate.checkpointId)
        dao.deleteConsumedCandidateEvaluations(profileKey)
    }

    private fun candidateWindowQualified(values: List<CandidateShadowEvaluationEntity>): Boolean {
        if (values.size < CANDIDATE_WINDOW_SIZE || values.any { it.candidateStatus != "OK" }) return false
        val activeMrr = values.sumOf(CandidateShadowEvaluationEntity::activeMrr) / values.size
        val candidateMrr = values.sumOf(CandidateShadowEvaluationEntity::candidateMrr) / values.size
        val activeLoss = values.sumOf(CandidateShadowEvaluationEntity::activeLogLoss) / values.size
        val candidateLoss = values.sumOf(CandidateShadowEvaluationEntity::candidateLogLoss) / values.size
        val activeBrier = values.sumOf(CandidateShadowEvaluationEntity::activeBrier) / values.size
        val candidateBrier = values.sumOf(CandidateShadowEvaluationEntity::candidateBrier) / values.size
        val activeTop3 = values.sumOf(CandidateShadowEvaluationEntity::activeTop3).toDouble() / values.size
        val candidateTop3 = values.sumOf(CandidateShadowEvaluationEntity::candidateTop3).toDouble() / values.size
        val hasMeaningfulWin = candidateMrr >= activeMrr * 1.01 || candidateLoss <= activeLoss * 0.99
        val calibrated = candidateCalibrationError(values, candidate = true) <= ECE_LIMIT &&
            candidateCalibrationError(values, candidate = true) <= candidateCalibrationError(values, candidate = false) + 0.02
        val perActionHealthy = values.groupBy(CandidateShadowEvaluationEntity::trueLabel).all { (_, samples) ->
            if (samples.size < 30) true else {
                samples.sumOf(CandidateShadowEvaluationEntity::candidateMrr) / samples.size >=
                    samples.sumOf(CandidateShadowEvaluationEntity::activeMrr) / samples.size * 0.95 &&
                    samples.sumOf(CandidateShadowEvaluationEntity::candidateLogLoss) / samples.size <=
                    samples.sumOf(CandidateShadowEvaluationEntity::activeLogLoss) / samples.size * 1.05
            }
        }
        return hasMeaningfulWin &&
            candidateMrr >= activeMrr * 0.99 && candidateLoss <= activeLoss * 1.01 &&
            candidateBrier <= activeBrier * 1.01 && candidateTop3 + 0.01 >= activeTop3 &&
            calibrated && perActionHealthy &&
            percentile95(values.map(CandidateShadowEvaluationEntity::candidateInferenceNanos)) < 5_000_000L
    }

    private fun candidateCalibrationError(
        values: List<CandidateShadowEvaluationEntity>,
        candidate: Boolean
    ): Double = (0 until 10).sumOf { bin ->
        val low = bin / 10.0
        val high = (bin + 1) / 10.0
        val samples = values.filter {
            val confidence = if (candidate) it.candidateConfidence else it.activeConfidence
            confidence >= low && (confidence < high || bin == 9)
        }
        if (samples.isEmpty()) 0.0 else {
            val confidence = samples.sumOf { if (candidate) it.candidateConfidence else it.activeConfidence } / samples.size
            val accuracy = samples.count {
                (if (candidate) it.candidateMrr else it.activeMrr) == 1.0
            }.toDouble() / samples.size
            abs(confidence - accuracy) * samples.size / values.size
        }
    }

    private suspend fun maybePromote(profileKey: String, state: TinyPromotionStateEntity): TinyPromotionStateEntity {
        val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        if (today < state.cooldownUntilEpochDay) return state
        val all = dao.promotionEvaluations(profileKey, state.minimumNewEvidenceSeq - 1, 10_000)
            .filter { it.activeCheckpointId == state.activeCheckpointId }
        val totalNonNone = all.count(ShadowEvaluationEntity::isOrganicNonNone)
        val spanDays = if (all.isEmpty()) 0L else all.maxOf(ShadowEvaluationEntity::occurredEpochDay) - all.minOf(ShadowEvaluationEntity::occurredEpochDay)
        val currentStage = PromotionStage.valueOf(state.stage)
        val target = when {
            currentStage == PromotionStage.SHADOW &&
                all.size >= 500 && totalNonNone >= 300 && spanDays >= 14 && state.consecutivePassingWindows >= 3 ->
                Triple(PromotionStage.ELIGIBLE, 0f, "LOCAL_QUALITY_ELIGIBLE")
            currentStage == PromotionStage.ELIGIBLE && state.consecutivePassingWindows >= 1 && residentDays(state, today) >= 7 ->
                Triple(PromotionStage.MIXED, 0.10f, "AUTO_PROMOTE_MIXED_10")
            currentStage == PromotionStage.MIXED && state.mixedLambda == 0.10f && state.consecutivePassingWindows >= 2 && residentDays(state, today) >= 7 ->
                Triple(PromotionStage.MIXED, 0.25f, "AUTO_PROMOTE_MIXED_25")
            currentStage == PromotionStage.MIXED && state.mixedLambda == 0.25f && state.consecutivePassingWindows >= 2 && residentDays(state, today) >= 7 ->
                Triple(PromotionStage.MIXED, 0.50f, "AUTO_PROMOTE_MIXED_50")
            currentStage == PromotionStage.MIXED && state.mixedLambda == 0.50f && state.consecutivePassingWindows >= 3 && residentDays(state, today) >= 14 ->
                Triple(PromotionStage.PRIMARY, 1f, "AUTO_PROMOTE_PRIMARY")
            else -> null
        } ?: return state
        return transition(state, target.first, target.second, target.third, state.activeCheckpointId, state.activeChecksum)
    }

    private suspend fun transition(
        state: TinyPromotionStateEntity,
        target: PromotionStage,
        lambda: Float,
        reason: String,
        checkpointId: String?,
        checksum: String?,
        checkpointChanged: Boolean = false,
        trainingRevision: Long = state.trainingRevision,
        candidateCheckpointId: String? = state.candidateCheckpointId
    ): TinyPromotionStateEntity {
        check(PromotionStateMachine.allows(PromotionStage.valueOf(state.stage), state.mixedLambda, target, lambda)) {
            "promotion cannot skip tiers"
        }
        val journalId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val updated = state.copy(
            stage = target.name,
            mixedLambda = lambda,
            stageGeneration = state.stageGeneration + 1,
            transitionSequence = state.transitionSequence + 1,
            enteredEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            activeCheckpointId = checkpointId,
            activeChecksum = checksum,
            candidateCheckpointId = candidateCheckpointId,
            trainingRevision = trainingRevision,
            minimumNewEvidenceSeq = if (checkpointChanged) {
                state.evidenceHighWatermark + 1
            } else {
                state.minimumNewEvidenceSeq
            },
            consecutivePassingWindows = 0,
            healthState = "HEALTHY",
            lastTransitionReason = reason,
            updatedAtEpochMs = now
        )
        persistTransition(
            state,
            updated,
            journalId = journalId,
            preparedAt = now
        )
        return updated
    }

    private suspend fun persistTransition(
        previous: TinyPromotionStateEntity,
        updated: TinyPromotionStateEntity,
        journalId: String = UUID.randomUUID().toString(),
        preparedAt: Long = System.currentTimeMillis()
    ) {
        dao.persistPromotionTransition(
            PromotionTransitionJournalEntity(
                journalId = journalId,
                profileKey = previous.profileKey,
                expectedGeneration = previous.modelGenerationVersion,
                fromStage = previous.stage,
                toStage = updated.stage,
                checkpointId = updated.activeCheckpointId,
                checksum = updated.activeChecksum,
                state = "PREPARED",
                preparedAtEpochMs = preparedAt,
                committedAtEpochMs = null
            ),
            updated,
            preparedAt
        )
    }

    private suspend fun perActionQualification(
        profileKey: String,
        window: List<ShadowEvaluationEntity>,
        state: TinyPromotionStateEntity
    ): Boolean {
        var noMajorRegression = true
        window.filter { it.isOrganicNonNone && it.paired }.groupBy(ShadowEvaluationEntity::trueLabel)
            .forEach { (actionId, samples) ->
                if (samples.size < 30) return@forEach
                val statMrr = samples.sumOf(ShadowEvaluationEntity::statReciprocalRank) / samples.size
                val tinyMrr = samples.sumOf(ShadowEvaluationEntity::tinyReciprocalRank) / samples.size
                val statLoss = samples.sumOf(ShadowEvaluationEntity::statLogLoss) / samples.size
                val tinyLoss = samples.sumOf(ShadowEvaluationEntity::tinyLogLoss) / samples.size
                val healthy = tinyMrr >= statMrr * 0.95 && tinyLoss <= statLoss * 1.05
                noMajorRegression = noMajorRegression && healthy
                val tier = if (healthy) nextTierForEvidence(state).name else EffectiveDecisionTier.STAT_ONLY.name
                dao.upsertActionQualification(
                    PromotionActionQualificationEntity(profileKey, actionId, tier, state.activeCheckpointId, samples.size, null, if (healthy) null else "ACTION_REGRESSION")
                )
            }
        return noMajorRegression
    }

    private fun qualityQualified(value: Aggregate): Boolean {
        val calibrationLoss = (value.tinyBrier <= value.statBrier * 0.98 && value.tinyLogLoss <= value.statLogLoss * 1.01) ||
            (value.tinyLogLoss <= value.statLogLoss * 0.98 && value.tinyBrier <= value.statBrier * 1.01)
        return value.pairedRate >= 0.99 &&
            value.tinyPrecision1 >= value.statPrecision1 &&
            value.tinyMrr >= value.statMrr * 1.02 &&
            value.tinyRecall3 >= value.statRecall3 + 0.01 &&
            value.tinyMrr > value.recentMrr &&
            value.tinyMrr > value.timeMrr &&
            value.tinyRecall3 > value.recentRecall3 &&
            value.tinyRecall3 > value.timeRecall3 &&
            value.tinyWinRate - value.statWinRate >= 0.05 && calibrationLoss
    }

    private fun resourceQualified(values: List<ShadowEvaluationEntity>): Boolean =
        percentile95(values.map(ShadowEvaluationEntity::tinyInferenceNanos)) < 5_000_000L &&
            percentile95(values.map(ShadowEvaluationEntity::trainingNanos)) < 50_000_000L &&
            values.all { it.modelSizeBytes <= 512 * 1024 }

    private fun shouldDowngrade(state: TinyPromotionStateEntity, ece: Double): Boolean =
        state.stage != PromotionStage.SHADOW.name && (state.consecutiveFailingWindows >= 2 || ece > 0.15)

    private suspend fun downgradeOne(state: TinyPromotionStateEntity, reason: String): TinyPromotionStateEntity {
        val (stage, lambda) = when {
            state.stage == PromotionStage.PRIMARY.name -> PromotionStage.MIXED to 0.50f
            state.stage == PromotionStage.MIXED.name && state.mixedLambda == 0.50f -> PromotionStage.MIXED to 0.25f
            state.stage == PromotionStage.MIXED.name && state.mixedLambda == 0.25f -> PromotionStage.MIXED to 0.10f
            state.stage == PromotionStage.MIXED.name -> PromotionStage.ELIGIBLE to 0f
            else -> PromotionStage.SHADOW to 0f
        }
        val updated = state.copy(
            stage = stage.name,
            mixedLambda = lambda,
            stageGeneration = state.stageGeneration + 1,
            transitionSequence = state.transitionSequence + 1,
            enteredEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay(),
            consecutivePassingWindows = 0,
            cooldownUntilEpochDay = LocalDate.now(ZoneOffset.UTC).toEpochDay() +
                qualityCooldownDays(state.consecutiveFailingWindows),
            minimumNewEvidenceSeq = state.evidenceHighWatermark + 1,
            lastTransitionReason = reason,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        persistTransition(state, updated)
        return updated
    }

    private suspend fun downgradeToShadow(
        state: TinyPromotionStateEntity,
        reason: String
    ): TinyPromotionStateEntity {
        val today = LocalDate.now(ZoneOffset.UTC).toEpochDay()
        val updated = state.copy(
            stage = PromotionStage.SHADOW.name,
            mixedLambda = 0f,
            stageGeneration = state.stageGeneration + 1,
            transitionSequence = state.transitionSequence + 1,
            enteredEpochDay = today,
            consecutivePassingWindows = 0,
            cooldownUntilEpochDay = today + qualityCooldownDays(state.consecutiveFailingWindows),
            minimumNewEvidenceSeq = state.evidenceHighWatermark + 1,
            lastTransitionReason = reason,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        persistTransition(state, updated)
        return updated
    }

    private fun qualityCooldownDays(failingWindows: Int): Long = when {
        failingWindows >= 4 -> 28L
        failingWindows >= 3 -> 14L
        else -> QUALITY_COOLDOWN_DAYS
    }

    private fun aggregate(values: List<ShadowEvaluationEntity>): Aggregate {
        val paired = values.filter(ShadowEvaluationEntity::paired)
        val denominator = paired.size.coerceAtLeast(1)
        return Aggregate(
            statPrecision1 = paired.sumOf(ShadowEvaluationEntity::statTop1).toDouble() / denominator,
            tinyPrecision1 = paired.sumOf(ShadowEvaluationEntity::tinyTop1).toDouble() / denominator,
            statMrr = paired.sumOf(ShadowEvaluationEntity::statReciprocalRank) / denominator,
            tinyMrr = paired.sumOf(ShadowEvaluationEntity::tinyReciprocalRank) / denominator,
            recentMrr = paired.sumOf(ShadowEvaluationEntity::recentReciprocalRank) / denominator,
            timeMrr = paired.sumOf(ShadowEvaluationEntity::timeReciprocalRank) / denominator,
            statRecall3 = paired.sumOf(ShadowEvaluationEntity::statTop3).toDouble() / denominator,
            tinyRecall3 = paired.sumOf(ShadowEvaluationEntity::tinyTop3).toDouble() / denominator,
            recentRecall3 = paired.sumOf(ShadowEvaluationEntity::recentTop3).toDouble() / denominator,
            timeRecall3 = paired.sumOf(ShadowEvaluationEntity::timeTop3).toDouble() / denominator,
            statBrier = paired.sumOf(ShadowEvaluationEntity::statBrier) / denominator,
            tinyBrier = paired.sumOf(ShadowEvaluationEntity::tinyBrier) / denominator,
            statLogLoss = paired.sumOf(ShadowEvaluationEntity::statLogLoss) / denominator,
            tinyLogLoss = paired.sumOf(ShadowEvaluationEntity::tinyLogLoss) / denominator,
            tinyWinRate = paired.count { it.winner == "TINY" }.toDouble() / denominator,
            statWinRate = paired.count { it.winner == "STAT" }.toDouble() / denominator,
            pairedRate = paired.size.toDouble() / values.size.coerceAtLeast(1)
        )
    }

    private fun expectedCalibrationError(values: List<ShadowEvaluationEntity>): Double {
        if (values.isEmpty()) return 1.0
        val paired = values.filter(ShadowEvaluationEntity::paired)
        if (paired.isEmpty()) return 1.0
        return (0 until 10).sumOf { bin ->
            val low = bin / 10.0
            val high = (bin + 1) / 10.0
            val samples = paired.filter { it.tinyTop1Confidence >= low && (it.tinyTop1Confidence < high || bin == 9) }
            if (samples.isEmpty()) 0.0 else {
                val confidence = samples.sumOf(ShadowEvaluationEntity::tinyTop1Confidence) / samples.size
                val accuracy = samples.sumOf(ShadowEvaluationEntity::tinyTop1).toDouble() / samples.size
                abs(confidence - accuracy) * samples.size / paired.size
            }
        }
    }

    private fun tierOf(state: TinyPromotionStateEntity): EffectiveDecisionTier = when {
        state.stage == PromotionStage.PRIMARY.name -> EffectiveDecisionTier.PRIMARY
        state.stage == PromotionStage.MIXED.name && state.mixedLambda >= 0.50f -> EffectiveDecisionTier.MIXED_50
        state.stage == PromotionStage.MIXED.name && state.mixedLambda >= 0.25f -> EffectiveDecisionTier.MIXED_25
        state.stage == PromotionStage.MIXED.name -> EffectiveDecisionTier.MIXED_10
        else -> EffectiveDecisionTier.STAT_ONLY
    }

    private fun nextTierForEvidence(state: TinyPromotionStateEntity): EffectiveDecisionTier = when (state.stage) {
            PromotionStage.SHADOW.name -> EffectiveDecisionTier.STAT_ONLY
            PromotionStage.ELIGIBLE.name -> EffectiveDecisionTier.MIXED_10
            PromotionStage.PRIMARY.name -> EffectiveDecisionTier.PRIMARY
            else -> when {
                state.mixedLambda < 0.25f -> EffectiveDecisionTier.MIXED_25
                state.mixedLambda < 0.50f -> EffectiveDecisionTier.MIXED_50
                else -> EffectiveDecisionTier.PRIMARY
            }
        }

    private fun tierByName(name: String): EffectiveDecisionTier = runCatching { EffectiveDecisionTier.valueOf(name) }
        .getOrDefault(EffectiveDecisionTier.STAT_ONLY)

    private fun residentDays(state: TinyPromotionStateEntity, today: Long) = (today - state.enteredEpochDay).coerceAtLeast(0)

    private fun percentile95(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * 0.95).toInt()]
    }

    private data class Aggregate(
        val statPrecision1: Double,
        val tinyPrecision1: Double,
        val statMrr: Double,
        val tinyMrr: Double,
        val recentMrr: Double,
        val timeMrr: Double,
        val statRecall3: Double,
        val tinyRecall3: Double,
        val recentRecall3: Double,
        val timeRecall3: Double,
        val statBrier: Double,
        val tinyBrier: Double,
        val statLogLoss: Double,
        val tinyLogLoss: Double,
        val tinyWinRate: Double,
        val statWinRate: Double,
        val pairedRate: Double
    ) {
        fun toJson(): String = """{"statPrecision1":$statPrecision1,"tinyPrecision1":$tinyPrecision1,"statMrr":$statMrr,"tinyMrr":$tinyMrr,"recentMrr":$recentMrr,"timeMrr":$timeMrr,"statRecall3":$statRecall3,"tinyRecall3":$tinyRecall3,"statBrier":$statBrier,"tinyBrier":$tinyBrier,"statLogLoss":$statLogLoss,"tinyLogLoss":$tinyLogLoss,"tinyWinRate":$tinyWinRate,"statWinRate":$statWinRate,"pairedRate":$pairedRate}"""
    }

    private companion object {
        const val PROMOTION_CONFIG_VERSION = 1
        const val WINDOW_SIZE = 100
        const val CANDIDATE_WINDOW_SIZE = 100
        const val CANDIDATE_TOTAL_EVIDENCE = 200
        const val CANDIDATE_MAX_AGE_MS = 30L * 86_400_000L
        const val ECE_LIMIT = 0.08
        const val QUALITY_COOLDOWN_DAYS = 7L
        const val HARD_COOLDOWN_DAYS = 14L
        const val RECENT_ATTEMPT_WINDOW = 20
        const val MAX_RECENT_FAILURES = 3
        const val MAX_DAILY_FAILURES = 3
    }
}
