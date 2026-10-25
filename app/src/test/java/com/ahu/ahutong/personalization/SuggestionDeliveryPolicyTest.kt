package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.action.SideEffect
import com.ahu.ahutong.personalization.ui.PendingSuggestionOffer
import com.ahu.ahutong.personalization.ui.SuggestionDeliveryBlockReason
import com.ahu.ahutong.personalization.ui.SuggestionDeliveryLane
import com.ahu.ahutong.personalization.ui.SuggestionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SuggestionDeliveryPolicyTest {
    @Test
    fun targetedWeatherPreemptsPendingOrdinarySchedule() {
        val result = assess(
            offer = offer(SuggestionDeliveryLane.TARGETED, AppActionId.OPEN_HOME),
            currentLane = SuggestionDeliveryLane.ORDINARY_NEXT_ACTION
        )

        assertTrue(result.canDisplay)
    }

    @Test
    fun targetedWeatherPreemptsVisibleOrdinaryJourney() {
        val result = assess(
            offer = offer(SuggestionDeliveryLane.TARGETED, AppActionId.OPEN_HOME),
            currentLane = SuggestionDeliveryLane.ORDINARY_JOURNEY
        )

        assertTrue(result.canDisplay)
        assertTrue(SuggestionDeliveryLane.TARGETED.priority > SuggestionDeliveryLane.ORDINARY_JOURNEY.priority)
    }

    @Test
    fun targetedIntervalBlockSchedulesRetryInsideSixtySecondWindow() {
        val offer = offer(
            lane = SuggestionDeliveryLane.TARGETED,
            action = AppActionId.OPEN_HOME,
            earliestDisplayElapsedMs = 20_000L,
            deadlineElapsedMs = 70_000L
        )
        val result = assess(
            offer = offer,
            nowElapsedMs = 15_000L,
            lastTargetedShownElapsedMs = 10_000L
        )

        assertFalse(result.canDisplay)
        assertEquals(20_000L, result.retryAtElapsedMs)
        assertEquals(SuggestionDeliveryBlockReason.INTERVAL, result.blockReason)
    }

    @Test
    fun firstTargetedSuggestionUsesShortDebounceInsteadOfSemanticMergeWindow() {
        val committedAtElapsedMs = 10_000L
        val firstDisplayAt = committedAtElapsedMs + SuggestionPolicy.TARGETED_CHANGE_DEBOUNCE_MS
        val result = assess(
            offer = offer(
                lane = SuggestionDeliveryLane.TARGETED,
                action = AppActionId.OPEN_CMB_CARD_RECHARGE,
                earliestDisplayElapsedMs = firstDisplayAt
            ),
            nowElapsedMs = committedAtElapsedMs
        )

        assertEquals(250L, SuggestionPolicy.TARGETED_CHANGE_DEBOUNCE_MS)
        assertFalse(result.canDisplay)
        assertEquals(firstDisplayAt, result.retryAtElapsedMs)
        assertEquals(SuggestionDeliveryBlockReason.DEBOUNCE, result.blockReason)
    }

    @Test
    fun newerSettingGenerationInvalidatesOlderRetryAndWins() {
        val old = assess(
            offer = offer(SuggestionDeliveryLane.TARGETED, AppActionId.OPEN_HOME, generation = 3L),
            currentGeneration = 4L
        )
        val latest = assess(
            offer = offer(SuggestionDeliveryLane.TARGETED, AppActionId.OPEN_CARD_RECHARGE, generation = 4L),
            currentGeneration = 4L
        )

        assertEquals(SuggestionDeliveryBlockReason.STALE_GENERATION, old.blockReason)
        assertTrue(latest.canDisplay)
    }

    @Test
    fun holdoutAndSafetyGatesCannotBeBypassedByTargetedLane() {
        val targeted = offer(SuggestionDeliveryLane.TARGETED, AppActionId.OPEN_HOME)

        assertEquals(SuggestionDeliveryBlockReason.HOLDOUT, assess(targeted, holdout = true).blockReason)
        assertEquals(SuggestionDeliveryBlockReason.SAFETY_GATE, assess(targeted, safetyAllowed = false).blockReason)
    }

    @Test
    fun transactionActionsRemainIneligibleForTargetedDelivery() {
        val transaction = AppActionCatalog.spec(AppActionId.SUBMIT_CMB_CARD_RECHARGE)

        assertEquals(SideEffect.TRANSACTION, transaction.sideEffect)
        assertFalse(transaction.suggestible)
        assertEquals(
            SuggestionDeliveryBlockReason.ENTRY_UNAVAILABLE,
            assess(
                offer(SuggestionDeliveryLane.TARGETED, AppActionId.SUBMIT_CMB_CARD_RECHARGE),
                entryAvailable = false
            ).blockReason
        )
    }

    @Test
    fun unattachedSuggestionWindowCannotConfirmExposure() {
        val offer = offer(SuggestionDeliveryLane.TARGETED, AppActionId.OPEN_HOME)

        assertFalse(
            SuggestionPolicy.canConfirmExposure(
                offer,
                offer.decisionId,
                offer.contextGeneration,
                offer.contextGeneration,
                enteredVisiblePopup = false
            )
        )
        assertTrue(
            SuggestionPolicy.canConfirmExposure(
                offer,
                offer.decisionId,
                offer.contextGeneration,
                offer.contextGeneration,
                enteredVisiblePopup = true
            )
        )
    }

    @Test
    fun targetedAndOrdinaryLanesUseIndependentIntervals() {
        assertEquals(10_000L, SuggestionPolicy.minimumIntervalMillis(SuggestionDeliveryLane.TARGETED))
        assertEquals(30_000L, SuggestionPolicy.minimumIntervalMillis(SuggestionDeliveryLane.ORDINARY_JOURNEY))
        assertEquals(30_000L, SuggestionPolicy.minimumIntervalMillis(SuggestionDeliveryLane.ORDINARY_NEXT_ACTION))
    }

    private fun assess(
        offer: PendingSuggestionOffer,
        currentGeneration: Long = offer.contextGeneration,
        nowElapsedMs: Long = 10_000L,
        lastTargetedShownElapsedMs: Long = 0L,
        lastOrdinaryShownElapsedMs: Long = 0L,
        currentLane: SuggestionDeliveryLane? = null,
        holdout: Boolean = false,
        safetyAllowed: Boolean = true,
        entryAvailable: Boolean = true
    ) = SuggestionPolicy.assessDelivery(
        offer = offer,
        currentGeneration = currentGeneration,
        nowElapsedMs = nowElapsedMs,
        lastTargetedShownElapsedMs = lastTargetedShownElapsedMs,
        lastOrdinaryShownElapsedMs = lastOrdinaryShownElapsedMs,
        currentLane = currentLane,
        holdout = holdout,
        safetyAllowed = safetyAllowed,
        entryAvailable = entryAvailable
    )

    private fun offer(
        lane: SuggestionDeliveryLane,
        action: AppActionId,
        generation: Long = 1L,
        earliestDisplayElapsedMs: Long = 10_000L,
        deadlineElapsedMs: Long = 70_000L
    ) = PendingSuggestionOffer(
        decisionId = "decision-$generation-${action.stableId}",
        contextGeneration = generation,
        lane = lane,
        targetActions = setOf(action),
        earliestDisplayElapsedMs = earliestDisplayElapsedMs,
        deadlineElapsedMs = deadlineElapsedMs
    )}
