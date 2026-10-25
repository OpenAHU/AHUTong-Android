package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.ActionFamily
import com.ahu.ahutong.personalization.action.AppActionId
import com.ahu.ahutong.personalization.storage.TrainingSampleEntity
import com.ahu.ahutong.personalization.training.TrainingFeedbackPolicy
import com.ahu.ahutong.personalization.training.TrainingReplayPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingFeedbackPolicyTest {
    @Test
    fun acceptedSuggestionIsPositiveButLowerWeightThanNaturalBehavior() {
        assertEquals(1f, TrainingFeedbackPolicy.sampleWeight(TrainingFeedbackPolicy.ORGANIC_ACTION))
        assertEquals(0.25f, TrainingFeedbackPolicy.SUGGESTION_POSITIVE_WEIGHT)
        assertEquals(
            TrainingFeedbackPolicy.SUGGESTION_POSITIVE_WEIGHT,
            TrainingFeedbackPolicy.sampleWeight(TrainingFeedbackPolicy.SUGGESTION_ACCEPTED)
        )
        assertEquals(0f, TrainingFeedbackPolicy.sampleWeight("SUGGESTION_DISMISSED"))
    }

    @Test
    fun naturalRowsRemainPrimaryAndWeakRowsCannotFillNaturalShortfall() {
        val natural = (1L..20L).map { sample(it, TrainingFeedbackPolicy.ORGANIC_ACTION) }
        val weak = (21L..60L).map { sample(it, TrainingFeedbackPolicy.SUGGESTION_ACCEPTED) }
        val selected = TrainingReplayPolicy.select(natural + weak, size = 16, maximumWeakRows = 4)

        assertEquals(16, selected.size)
        assertEquals(12, selected.count { TrainingFeedbackPolicy.isNatural(it.labelSource) })
        assertEquals(4, selected.count { TrainingFeedbackPolicy.isAcceptedSuggestion(it.labelSource) })
        assertTrue(
            TrainingReplayPolicy.select(
                natural.take(11) + weak,
                size = 16,
                maximumWeakRows = 4
            ).isEmpty()
        )
    }

    private fun sample(rowId: Long, source: String) = TrainingSampleEntity(
        rowId = rowId,
        sampleId = "sample-$rowId",
        profileKey = "profile",
        decisionId = "decision-$rowId",
        featureSchemaVersion = 4,
        outputSchemaVersion = 1,
        actionCatalogVersion = 1,
        features = byteArrayOf(),
        targetIndex = 0,
        targetActionId = AppActionId.OPEN_HOME.stableId,
        actionFamily = ActionFamily.NAVIGATION.name,
        occurredEpochDay = 20_000,
        replayPriority = 1f,
        trainingCount = 0,
        labelSource = source
    )}
