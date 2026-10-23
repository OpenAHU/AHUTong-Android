package com.ahu.ahutong.personalization.ui

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector

internal data class SuggestionCandidate(
    val action: AppActionId,
    val probability: Float
)

internal enum class OrdinaryNextActionGateReason {
    NO_ORGANICALLY_ELIGIBLE_ACTION,
    BELOW_CONFIDENCE_THRESHOLD,
    INSUFFICIENT_PROBABILITY_MARGIN,
    NON_SUGGESTIBLE_OUTPUT_DOMINATES
}

internal data class OrdinaryNextActionGateAssessment(
    val candidate: SuggestionCandidate?,
    val candidateProbability: Float?,
    val strongestCompetitorId: String?,
    val strongestCompetitorProbability: Float?,
    val probabilityMargin: Float?,
    val rejectionReason: OrdinaryNextActionGateReason?
) {
    val accepted: Boolean get() = candidate != null && rejectionReason == null
}

enum class SuggestionDeliveryLane(val priority: Int) {
    TARGETED(3),
    ORDINARY_JOURNEY(2),
    ORDINARY_NEXT_ACTION(1)
}

data class PendingSuggestionOffer(
    val decisionId: String,
    val contextGeneration: Long,
    val lane: SuggestionDeliveryLane,
    val targetActions: Set<AppActionId>,
    val earliestDisplayElapsedMs: Long,
    val deadlineElapsedMs: Long
)

internal enum class SuggestionDeliveryBlockReason {
    STALE_GENERATION,
    HOLDOUT,
    EXPIRED,
    SAFETY_GATE,
    ENTRY_UNAVAILABLE,
    EMPTY_TARGETS,
    DEBOUNCE,
    INTERVAL,
    OCCUPIED
}

internal data class SuggestionDeliveryAssessment(
    val canDisplay: Boolean,
    val retryAtElapsedMs: Long? = null,
    val blockReason: SuggestionDeliveryBlockReason? = null
)

internal object SuggestionPolicy {
    const val TARGETED_CHANGE_DEBOUNCE_MS = 250L
    const val TARGETED_MIN_INTERVAL_MS = 10_000L
    const val ORDINARY_MIN_INTERVAL_MS = 30_000L
    const val ORDINARY_NEXT_ACTION_MIN_CONFIDENCE = 0.40f
    const val ORDINARY_NEXT_ACTION_MIN_MARGIN = 0.08f
    const val OCCUPIED_RETRY_DELAY_MS = 250L

    fun remainingVisibilityFraction(
        shownAtElapsedMs: Long,
        expiresAtElapsedMs: Long,
        nowElapsedMs: Long
    ): Float {
        val lifetime = expiresAtElapsedMs - shownAtElapsedMs
        if (lifetime <= 0L) return 0f
        val remaining = expiresAtElapsedMs - nowElapsedMs
        return (remaining.toDouble() / lifetime.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun isDisplayIntervalElapsed(
        lastShownElapsedMs: Long,
        nowElapsedMs: Long,
        minimumIntervalMs: Long
    ): Boolean {
        require(minimumIntervalMs >= 0L)
        if (lastShownElapsedMs == 0L) return true
        return nowElapsedMs >= lastShownElapsedMs &&
            nowElapsedMs - lastShownElapsedMs >= minimumIntervalMs
    }

    fun minimumIntervalMillis(lane: SuggestionDeliveryLane): Long = when (lane) {
        SuggestionDeliveryLane.TARGETED -> TARGETED_MIN_INTERVAL_MS
        SuggestionDeliveryLane.ORDINARY_JOURNEY,
        SuggestionDeliveryLane.ORDINARY_NEXT_ACTION -> ORDINARY_MIN_INTERVAL_MS
    }

    fun earliestDisplayElapsedMs(
        lane: SuggestionDeliveryLane,
        nowElapsedMs: Long,
        lastTargetedShownElapsedMs: Long,
        lastOrdinaryShownElapsedMs: Long
    ): Long {
        val lastShown = when (lane) {
            SuggestionDeliveryLane.TARGETED -> lastTargetedShownElapsedMs
            SuggestionDeliveryLane.ORDINARY_JOURNEY,
            SuggestionDeliveryLane.ORDINARY_NEXT_ACTION -> lastOrdinaryShownElapsedMs
        }
        if (lastShown == 0L || nowElapsedMs < lastShown) return nowElapsedMs
        return maxOf(nowElapsedMs, lastShown + minimumIntervalMillis(lane))
    }

    fun assessDelivery(
        offer: PendingSuggestionOffer,
        currentGeneration: Long,
        nowElapsedMs: Long,
        lastTargetedShownElapsedMs: Long,
        lastOrdinaryShownElapsedMs: Long,
        currentLane: SuggestionDeliveryLane?,
        holdout: Boolean,
        safetyAllowed: Boolean,
        entryAvailable: Boolean
    ): SuggestionDeliveryAssessment {
        if (offer.contextGeneration != currentGeneration) {
            return SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.STALE_GENERATION)
        }
        if (nowElapsedMs >= offer.deadlineElapsedMs) {
            return SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.EXPIRED)
        }
        if (holdout) {
            return SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.HOLDOUT)
        }
        if (!safetyAllowed) {
            return SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.SAFETY_GATE)
        }
        if (!entryAvailable) {
            return SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.ENTRY_UNAVAILABLE)
        }
        if (offer.targetActions.isEmpty()) {
            return SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.EMPTY_TARGETS)
        }

        val laneIntervalAt = earliestDisplayElapsedMs(
            offer.lane,
            nowElapsedMs,
            lastTargetedShownElapsedMs,
            lastOrdinaryShownElapsedMs
        )
        val intervalAt = maxOf(offer.earliestDisplayElapsedMs, laneIntervalAt)
        val occupiedAt = currentLane?.takeIf { it.priority >= offer.lane.priority }
            ?.let { nowElapsedMs + OCCUPIED_RETRY_DELAY_MS }
            ?: nowElapsedMs
        val retryAt = maxOf(intervalAt, occupiedAt)
        if (retryAt > nowElapsedMs) {
            return if (retryAt < offer.deadlineElapsedMs) {
                SuggestionDeliveryAssessment(
                    canDisplay = false,
                    retryAtElapsedMs = retryAt,
                    blockReason = when {
                        occupiedAt >= intervalAt && occupiedAt > nowElapsedMs ->
                            SuggestionDeliveryBlockReason.OCCUPIED
                        offer.earliestDisplayElapsedMs > laneIntervalAt ->
                            SuggestionDeliveryBlockReason.DEBOUNCE
                        else -> SuggestionDeliveryBlockReason.INTERVAL
                    }
                )
            } else {
                SuggestionDeliveryAssessment(false, blockReason = SuggestionDeliveryBlockReason.EXPIRED)
            }
        }
        return SuggestionDeliveryAssessment(canDisplay = true)
    }

    fun canConfirmExposure(
        offer: PendingSuggestionOffer?,
        decisionId: String,
        contextGeneration: Long,
        currentGeneration: Long,
        enteredVisiblePopup: Boolean
    ): Boolean = enteredVisiblePopup && offer != null &&
        offer.decisionId == decisionId &&
        offer.contextGeneration == contextGeneration &&
        contextGeneration == currentGeneration

    fun rankedCandidates(
        prediction: NextActionProbabilityVector,
        organicActionIds: Set<String>,
        requireOrganicHistory: Boolean = true
    ): List<SuggestionCandidate> = prediction.rankedIndices().mapNotNull { index ->
        val probability = prediction.probabilities[index]
        if (probability <= 0f) return@mapNotNull null
        val outputId = prediction.outputIds[index]
        if (requireOrganicHistory && outputId !in organicActionIds) return@mapNotNull null
        val action = AppActionId.fromStableId(outputId) ?: return@mapNotNull null
        val spec = AppActionCatalog.spec(action)
        if (!spec.suggestible || spec.sideEffect == SideEffect.TRANSACTION) return@mapNotNull null
        SuggestionCandidate(action, probability)
    }

    fun assessOrdinaryNextAction(
        prediction: NextActionProbabilityVector,
        organicActionIds: Set<String>
    ): OrdinaryNextActionGateAssessment {
        val candidate = rankedCandidates(prediction, organicActionIds).firstOrNull()
            ?: return OrdinaryNextActionGateAssessment(
                candidate = null,
                candidateProbability = null,
                strongestCompetitorId = null,
                strongestCompetitorProbability = null,
                probabilityMargin = null,
                rejectionReason = OrdinaryNextActionGateReason.NO_ORGANICALLY_ELIGIBLE_ACTION
            )
        val candidateId = candidate.action.stableId
        val competitorIndex = prediction.outputIds.indices
            .asSequence()
            .filter { prediction.outputIds[it] != candidateId }
            .maxByOrNull { prediction.probabilities[it] }
        val competitorId = competitorIndex?.let(prediction.outputIds::get)
        val competitorProbability = competitorIndex?.let(prediction.probabilities::get)
        val margin = competitorProbability?.let { candidate.probability - it } ?: candidate.probability
        val reason = when {
            candidate.probability < ORDINARY_NEXT_ACTION_MIN_CONFIDENCE ->
                OrdinaryNextActionGateReason.BELOW_CONFIDENCE_THRESHOLD
            margin + GATE_EPSILON < ORDINARY_NEXT_ACTION_MIN_MARGIN && competitorProbability != null && competitorProbability > candidate.probability ->
                OrdinaryNextActionGateReason.NON_SUGGESTIBLE_OUTPUT_DOMINATES
            margin + GATE_EPSILON < ORDINARY_NEXT_ACTION_MIN_MARGIN ->
                OrdinaryNextActionGateReason.INSUFFICIENT_PROBABILITY_MARGIN
            else -> null
        }
        return OrdinaryNextActionGateAssessment(
            candidate = candidate.takeIf { reason == null },
            candidateProbability = candidate.probability,
            strongestCompetitorId = competitorId,
            strongestCompetitorProbability = competitorProbability,
            probabilityMargin = margin,
            rejectionReason = reason
        )
    }

    private const val GATE_EPSILON = 1e-6f
}
