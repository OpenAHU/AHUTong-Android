package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.inference.NextActionProbabilityVector
import com.ahu.ahutong.personalization.ui.OrdinaryNextActionGateReason
import com.ahu.ahutong.personalization.ui.SuggestionPolicy
import java.io.File
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
    fun ordinaryNextActionRejectsConfidenceBelowFortyPercent() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.3999f,
                AppActionId.VIEW_GRADES to 0.30f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.BELOW_CONFIDENCE_THRESHOLD, assessment.rejectionReason)
    }

    @Test
    fun ordinaryNextActionConfidenceBoundaryIsInclusive() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.40f,
                AppActionId.VIEW_GRADES to 0.30f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertTrue(assessment.accepted)
        assertEquals(AppActionId.VIEW_SCHEDULE, assessment.candidate?.action)
    }

    @Test
    fun ordinaryNextActionRejectsAnAmbiguousTopCandidate() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.46f,
                AppActionId.VIEW_GRADES to 0.40f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.INSUFFICIENT_PROBABILITY_MARGIN, assessment.rejectionReason)
    }

    @Test
    fun ordinaryNextActionRejectsFallbackWhenUnsafeOutputDominates() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.SUBMIT_CARD_RECHARGE to 0.45f,
                AppActionId.VIEW_SCHEDULE to 0.40f
            ),
            setOf(AppActionId.SUBMIT_CARD_RECHARGE.stableId, AppActionId.VIEW_SCHEDULE.stableId)
        )

        assertFalse(assessment.accepted)
        assertEquals(OrdinaryNextActionGateReason.NON_SUGGESTIBLE_OUTPUT_DOMINATES, assessment.rejectionReason)
        assertEquals(AppActionId.SUBMIT_CARD_RECHARGE.stableId, assessment.strongestCompetitorId)
    }

    @Test
    fun ordinaryNextActionAcceptsConfidentClearlyLeadingCandidate() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.50f,
                AppActionId.VIEW_GRADES to 0.40f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertTrue(assessment.accepted)
        assertEquals(AppActionId.VIEW_SCHEDULE, assessment.candidate?.action)
        assertEquals(0.10f, requireNotNull(assessment.probabilityMargin), 0.0001f)
    }

    @Test
    fun ordinaryNextActionMarginBoundaryIsInclusive() {
        val assessment = SuggestionPolicy.assessOrdinaryNextAction(
            vector(
                AppActionId.VIEW_SCHEDULE to 0.54f,
                AppActionId.VIEW_GRADES to 0.46f
            ),
            setOf(AppActionId.VIEW_SCHEDULE.stableId, AppActionId.VIEW_GRADES.stableId)
        )

        assertTrue(assessment.accepted)
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

    @Test
    fun suggestionClickIsOwnedByParentAndDismissHasNoPenalty() {
        val sourceRoot = File(repositoryRoot(), "app/src/main/java")
        val host = File(
            sourceRoot,
            "com/ahu/ahutong/personalization/ui/SmartSuggestionHost.kt"
        ).readText()
        val main = File(sourceRoot, "com/ahu/ahutong/ui/screen/Main.kt").readText()
        val runtime = File(
            sourceRoot,
            "com/ahu/ahutong/personalization/runtime/PredictionRuntime.kt"
        ).readText()
        val prefetch = File(
            sourceRoot,
            "com/ahu/ahutong/personalization/prefetch/PrefetchCoordinator.kt"
        ).readText()
        val preferences = File(
            sourceRoot,
            "com/ahu/ahutong/data/dao/PreferencesManager.kt"
        ).readText()

        assertTrue(host.contains("val animationScope = rememberCoroutineScope()"))
        assertTrue(host.contains("onClick = { onSuggestionClick(suggestion) }"))
        assertTrue(host.contains("highlightRadiusMultiplier = 0.9f"))
        assertTrue(host.contains("fixedHighlightPressProgress = 0.8f"))
        assertTrue(host.contains("drawGlobalPressOverlay = false"))
        assertTrue(host.contains("holdHighlightPositionOnRelease = true"))
        assertTrue(host.contains("positionReturnDampingRatio = 0.75f"))
        assertTrue(host.contains("releaseAnimationDurationMillis = 120"))
        assertTrue(host.contains("onPressStart ="))
        assertTrue(host.contains("animationScope.launch { lifetimeOpacity.snapTo(1f) }"))
        assertTrue(host.contains("onPressEnd ="))
        assertTrue(
            Regex("if \\(suggestion\\.visibilityPaused\\) \\{\\s*lifetimeOpacity\\.snapTo\\(1f\\)\\s*return@LaunchedEffect\\s*}")
                .containsMatchIn(host)
        )
        assertTrue(host.contains(".then(interactiveHighlight.modifier)"))
        assertTrue(host.contains(".then(interactiveHighlight.gestureModifier)"))
        assertTrue(host.contains("interactiveHighlight.pressProgress"))
        assertTrue(host.contains("runtime.pauseSuggestionVisibility(suggestion.executionId)"))
        assertTrue(host.contains("runtime.restartSuggestionVisibility(suggestion.executionId)"))
        assertTrue(host.contains("if (suggestion.visibilityPaused)"))
        assertFalse(host.contains("MutableInteractionSource"))
        assertFalse(host.contains("PressInteraction"))
        assertTrue(host.contains(".clip(suggestionShape)"))
        assertFalse(host.contains("interaction.pressPosition"))
        assertFalse(host.contains("drawCircle("))
        assertFalse(host.contains("waveVisible"))
        assertTrue(host.contains("lifetimeOpacity.animateTo"))
        assertTrue(host.contains("Modifier.drawBackdrop"))
        assertTrue(host.contains("vibrancy()"))
        assertTrue(host.contains("blur(8f.dp.toPx())"))
        assertTrue(host.contains("lens(24f.dp.toPx(), 24f.dp.toPx())"))
        assertTrue(host.contains("opacity(lifetimeOpacity.value)"))
        assertTrue(host.contains("val suggestionShape = ContinuousCapsule"))
        assertTrue(host.contains("Dialog("))
        assertTrue(host.contains("DialogProperties("))
        assertTrue(host.contains("WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE"))
        assertTrue(host.contains("WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL"))
        assertTrue(host.contains("WindowManager.LayoutParams.WRAP_CONTENT"))
        assertTrue(host.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)"))
        assertTrue(host.contains("dismissOnClickOutside = false"))
        assertFalse(host.contains("Popup("))
        assertFalse(host.contains("PopupProperties("))
        assertTrue(host.contains("onGloballyPositioned"))
        assertTrue(host.contains("withFrameNanos"))
        assertTrue(host.contains("dialogView.isAttachedToWindow"))
        assertTrue(host.contains("dialogView.windowVisibility == View.VISIBLE"))
        assertTrue(host.contains("runtime.confirmSuggestionVisible(suggestion.executionId)"))
        assertTrue(host.contains("MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertFalse(host.contains("isSystemInDarkTheme"))
        assertFalse(host.contains("Color(0xFFFAFAFA)"))
        assertFalse(host.contains("Color(0xFF121212)"))
        assertFalse(host.contains("layerBlock = { alpha = lifetimeOpacity.value }"))
        assertFalse(host.contains("graphicsLayer { alpha = lifetimeOpacity.value }"))
        assertFalse(host.contains("Surface("))
        assertFalse(host.contains("LocalIndication"))
        assertFalse(host.contains("DoNotDisturbOn"))
        assertFalse(host.contains("suppressSuggestedActionByUser"))
        assertTrue(
            Regex("onSuggestionClick\\s*=\\s*\\{ suggestion ->[\\s\\S]*?scope\\.launch[\\s\\S]*?acceptSuggestion")
                .containsMatchIn(main)
        )
        val dismissBody = Regex("fun dismissSuggestionByUser\\(\\) \\{([\\s\\S]*?)\\n    }")
            .find(runtime)
            ?.groupValues
            ?.get(1)
            .orEmpty()
        assertTrue(dismissBody.contains("cancelSuggestionDeliveryState"))
        assertTrue(
            Regex("acceptSuggestion[\\s\\S]*?recordActionIntent\\([\\s\\S]*?deferNextOpportunity = true")
                .containsMatchIn(runtime)
        )
        assertTrue(runtime.contains("SuggestionPolicy.TARGETED_CHANGE_DEBOUNCE_MS"))
        assertTrue(runtime.contains("SuggestionDeliveryLane.TARGETED"))
        val showBody = Regex("suspend fun showSuggestion[\\s\\S]*?\\n    }\\n\\n    suspend fun confirmSuggestionVisible")
            .find(runtime)
            ?.value
            .orEmpty()
        assertFalse(showBody.contains("consumeIntervention"))
        assertTrue(
            Regex("confirmSuggestionVisible[\\s\\S]*?consumeIntervention[\\s\\S]*?lastOrdinarySuggestionElapsedMs = shownAtElapsedMs")
                .containsMatchIn(runtime)
        )
        assertTrue(runtime.contains("fun setInlineSensitiveUiVisible(visible: Boolean)"))
        assertTrue(runtime.contains("!foreground || !interactive || _suggestionOverlayBlocked.value"))
        assertTrue(main.contains("suggestionOverlayBlocked"))
        assertTrue(host.contains("runtime.setSuggestionHostBlocked(blocked)"))
        assertFalse(main.contains("isReLoginShown || sensitiveUiVisible || imeVisible"))
        assertTrue(runtime.contains("fun pauseSuggestionVisibility(executionId: String)"))
        assertTrue(runtime.contains("fun restartSuggestionVisibility(executionId: String)"))
        assertTrue(runtime.contains("suggestionVisibilityGeneration.incrementAndGet()"))
        assertTrue(runtime.contains("shownAtElapsedMs = restartedAtElapsedMs"))
        assertTrue(runtime.contains("prefetchCoordinator.prefetchSuggestedAction"))
        assertTrue(prefetch.contains("suspend fun prefetchSuggestedAction"))
        assertTrue(prefetch.contains("FileUtils.saveResponseBodyToFile(context, body, \"xiaoli.jpg\")"))
        assertTrue(prefetch.contains("AHUCache.saveLostFoundList(1, response.data.list)"))
        assertFalse(preferences.contains("SUGGESTION_ACTION_SUPPRESSIONS"))
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

    private fun repositoryRoot(): File {
        val userDirectory = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDirectory)) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }
}
