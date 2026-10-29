package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector
import com.ahu.ahutong.personalization.ui.OrdinaryNextActionGateReason
import com.ahu.ahutong.personalization.ui.SuggestionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionPolicyTest {
    @Test
    fun zeroOrganicSamplesNeverProduceRandomSuggestion() {
        assertTrue(SuggestionPolicy.rankedCandidates(vector(), emptySet()).isEmpty())
    }

    @Test
    fun targetedCandidatesCanBeRankedBeforeOrganicHistoryExists() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(AppActionId.OPEN_HOME to 0.02f),
            organicActionIds = emptySet(),
            requireOrganicHistory = false
        )

        assertEquals(AppActionId.OPEN_HOME, candidates.single().action)
        assertEquals(0.02f, candidates.single().probability)
    }

    @Test
    fun firstOrganicSampleIsEligibleWithoutProbabilityOrMarginGate() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(AppActionId.VIEW_SCHEDULE to 0.01f),
            setOf(AppActionId.VIEW_SCHEDULE.stableId)
        )

        assertEquals(AppActionId.VIEW_SCHEDULE, candidates.single().action)
        assertEquals(0.01f, candidates.single().probability)
    }

    @Test
    fun ranksOnlyPreviouslyUsedSuggestibleNonTransactionActions() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(
                AppActionId.REFRESH_PAYMENT_QR to 0.40f,
                AppActionId.SUBMIT_CARD_RECHARGE to 0.30f,
                AppActionId.VIEW_GRADES to 0.02f,
                AppActionId.VIEW_SCHEDULE to 0.01f
            ),
            setOf(
                AppActionId.REFRESH_PAYMENT_QR.stableId,
                AppActionId.SUBMIT_CARD_RECHARGE.stableId,
                AppActionId.VIEW_GRADES.stableId
            )
        )

        assertEquals(listOf(AppActionId.VIEW_GRADES), candidates.map { it.action })
    }

    @Test
    fun zeroAvailabilityProbabilityRemainsIneligible() {
        val candidates = SuggestionPolicy.rankedCandidates(
            vector(),
            setOf(AppActionId.VIEW_SCHEDULE.stableId)
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun ordinaryNextActionRejectsConfidenceBelowFiftyPercent() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.49f,
                AppActionId.VIEW_GRADES to 0.25f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.BELOW_CONFIDENCE_THRESHOLD, assessment.rejectionReason)
    }

    @Test
    fun ordinaryNextActionRejectsFormerlyAcceptedFortyPercentPrediction() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.40f,
                AppActionId.VIEW_GRADES to 0.30f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.BELOW_CONFIDENCE_THRESHOLD, assessment.rejectionReason)
    }

    @Test
    fun ordinaryNextActionRejectsAnAmbiguousTopCandidate() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.50f,
                AppActionId.VIEW_GRADES to 0.40f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.INSUFFICIENT_PROBABILITY_MARGIN, assessment.rejectionReason)
    }

    @Test
    fun ordinaryNextActionRejectsLowConfidenceFallbackWhenUnsafeOutputDominates() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.SUBMIT_CARD_RECHARGE to 0.45f,
                AppActionId.VIEW_SCHEDULE to 0.35f
            ),
            setOf(AppActionId.SUBMIT_CARD_RECHARGE.stableId, AppActionId.VIEW_SCHEDULE.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.BELOW_CONFIDENCE_THRESHOLD, assessment.rejectionReason)
        assertEquals(AppActionId.SUBMIT_CARD_RECHARGE.stableId, assessment.strongestCompetitorId)
    }

    @Test
    fun ordinaryNextActionAcceptsConfidentClearlyLeadingCandidate() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.55f,
                AppActionId.VIEW_GRADES to 0.35f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertTrue(assessment.accepted)
        assertEquals(AppActionId.VIEW_SCHEDULE, assessment.candidate?.action)
        assertEquals(0.20f, requireNotNull(assessment.probabilityMargin), 0.0001f)
    }

    @Test
    fun ordinaryNextActionMarginBoundaryIsInclusive() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.50f,
                AppActionId.VIEW_GRADES to 0.35f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertTrue(assessment.accepted)
    }

    @Test
    fun ordinaryNextActionRejectsMarginJustBelowFifteenPoints() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.50f,
                AppActionId.VIEW_GRADES to 0.351f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.INSUFFICIENT_PROBABILITY_MARGIN, assessment.rejectionReason)
    }

    @Test
    fun suggestionDisplayIntervalIsInclusiveAndHandlesClockRegression() {
        assertTrue(SuggestionPolicy.isDisplayIntervalElapsed(0L, 1L, 30_000L))
        assertFalse(SuggestionPolicy.isDisplayIntervalElapsed(10_000L, 39_999L, 30_000L))
        assertTrue(SuggestionPolicy.isDisplayIntervalElapsed(10_000L, 40_000L, 30_000L))
        assertFalse(SuggestionPolicy.isDisplayIntervalElapsed(40_000L, 10_000L, 30_000L))
    }

    @Test
    fun suggestionVisibilityTracksItsRemainingLifetime() {
        assertEquals(1f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 13_000L, 1_000L))
        assertEquals(0.5f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 13_000L, 7_000L))
        assertEquals(0f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 13_000L, 13_000L))
        assertEquals(0f, SuggestionPolicy.remainingVisibilityFraction(1_000L, 1_000L, 1_000L))
    }

    private fun vector(vararg actionProbabilities: Pair<AppActionId, Float>): NextActionProbabilityVector {
        val values = FloatArray(AppActionCatalog.outputIds.size)
        actionProbabilities.forEach { (action, probability) ->
            values[AppActionCatalog.outputIndex.getValue(action.stableId)] = probability
        }
        values[AppActionCatalog.outputIndex.getValue(AppActionCatalog.NONE_OUTPUT_ID)] =
            1f - actionProbabilities.sumOf { it.second.toDouble() }.toFloat()
        return NextActionProbabilityVector(AppActionCatalog.outputIds, values, modelVersion = 1)
    }
}
