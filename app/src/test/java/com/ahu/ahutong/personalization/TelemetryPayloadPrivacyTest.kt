package com.ahu.ahutong.personalization

import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.model.TRAINING_CONFIG_VERSION
import com.ahu.ahutong.personalization.telemetry.ActionMetricSums
import com.ahu.ahutong.personalization.telemetry.ModelAggregate
import com.ahu.ahutong.personalization.telemetry.ModelMetricSums
import com.ahu.ahutong.personalization.telemetry.ModelQualityBatchRequest
import com.ahu.ahutong.personalization.telemetry.ModelQualityEvaluationReport
import com.ahu.ahutong.personalization.telemetry.PairwiseAggregate
import com.ahu.ahutong.personalization.telemetry.TelemetryPayloadValidator
import com.ahu.ahutong.personalization.telemetry.DELIVERY_BLOCK_REASONS
import com.ahu.ahutong.personalization.telemetry.ModelQualityV3BatchRequest
import com.ahu.ahutong.personalization.telemetry.ModelQualityV3TaskReport
import com.ahu.ahutong.personalization.telemetry.TelemetryV3PayloadValidator
import com.ahu.ahutong.personalization.telemetry.TELEMETRY_SERVER_SCHEMA_VERSION
import com.ahu.ahutong.personalization.telemetry.V3DeliveryAggregate
import com.ahu.ahutong.personalization.telemetry.V3DeliveryLaneAggregate
import com.ahu.ahutong.personalization.telemetry.V3NamedCount
import com.ahu.ahutong.personalization.telemetry.StoredActionMetric
import com.ahu.ahutong.personalization.telemetry.sanitizeStoredActionMetrics
import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryPayloadPrivacyTest {

    @Test
    fun currentClientUsesV3TelemetryContract() {
        assertEquals(3, TELEMETRY_SERVER_SCHEMA_VERSION)
    }

    @Test
    fun reportContainsOnlyAggregatesAndRandomIdentifiers() {
        val json = Gson().toJson(ModelQualityBatchRequest(batchId = "random-batch", reports = listOf(validReport())))
        listOf(
            "studentId", "account", "phone", "androidId", "imei", "paymentId", "weights",
            "optimizer", "featureVector", "probabilities", "decisionId", "eventId"
        ).forEach { forbidden -> assertFalse(json.contains(forbidden, ignoreCase = true), forbidden) }
        assertTrue(json.contains("eligibleSampleCount"))
        assertTrue(json.contains("reciprocalRankSum"))
        assertTrue(json.contains("pairedSampleCount"))
        assertTrue(json.contains("windowStartDay"))
    }

    @Test
    fun validAggregatePassesStrictClientValidation() {
        TelemetryPayloadValidator.requireValid(validReport())
    }

    @Test
    fun v3ReportContainsOnlyTaskLevelAggregates() {
        val report = validV3Report()
        TelemetryV3PayloadValidator.requireValid(report)
        val json = Gson().toJson(ModelQualityV3BatchRequest(batchId = "random-v3-batch", reports = listOf(report)))
        listOf(
            "studentId", "account", "phone", "androidId", "imei", "route", "journeySequence",
            "semanticId", "settingValue", "candidateId", "fingerprint", "featureVector",
            "probabilities", "decisionId", "checkpointId"
        ).forEach { forbidden -> assertFalse(json.contains(forbidden, ignoreCase = true), forbidden) }
        assertTrue(json.contains("enteredVisibleSurface"))
        assertTrue(json.contains("naturalHoldoutSampleCount"))
    }

    @Test
    fun v3ReportRejectsSparseTaskWindowAndUnknownBlockReason() {
        assertFailsWith<IllegalArgumentException> {
            TelemetryV3PayloadValidator.requireValid(validV3Report().copy(sampleCount = 63))
        }
        val invalidLane = requireNotNull(validV3Report().delivery).lanes.single().copy(
            blocked = listOf(V3NamedCount("RAW_ROUTE_NAME", 1))
        )
        assertTrue("RAW_ROUTE_NAME" !in DELIVERY_BLOCK_REASONS)
        assertFailsWith<IllegalArgumentException> {
            TelemetryV3PayloadValidator.requireValid(
                validV3Report().copy(delivery = V3DeliveryAggregate(listOf(invalidLane)))
            )
        }
    }

    @Test
    fun sparsePerActionMetricIsRejected() {
        val invalid = validReport().copy(
            perAction = listOf(validAction().copy(eligibleSampleCount = 29, pairedSampleCount = 29))
        )
        assertFailsWith<IllegalArgumentException> { TelemetryPayloadValidator.requireValid(invalid) }
    }

    @Test
    fun inconsistentPairedCountsAreRejected() {
        val invalid = validReport().copy(pairwise = PairwiseAggregate(30, 20, 10, 61))
        assertFailsWith<IllegalArgumentException> { TelemetryPayloadValidator.requireValid(invalid) }
    }

    @Test
    fun invalidDateAndModelVersionAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            TelemetryPayloadValidator.requireValid(validReport().copy(windowStartDay = "not-a-day"))
        }
        assertFailsWith<IllegalArgumentException> {
            TelemetryPayloadValidator.requireValid(
                validReport().copy(statistical = validReport().statistical.copy(modelVersion = 0))
            )
        }
    }

    @Test
    fun erasedOrCorruptStoredMetricTypesAreDiscardedBeforeUse() {
        val valid = StoredActionMetric(
            actionId = AppActionCatalog.outputIds.first(),
            eligibleSampleCount = 1,
            pairedSampleCount = 1,
            statTop1Correct = 1,
            statTop3Hit = 1,
            statReciprocalRankSum = 1.0,
            statBrierSum = 0.1,
            statLogLossSum = 0.2,
            tinyTop1Correct = 1,
            tinyTop3Hit = 1,
            tinyReciprocalRankSum = 1.0,
            tinyBrierSum = 0.1,
            tinyLogLossSum = 0.2,
            tinyWins = 0,
            statWins = 0,
            ties = 1
        )
        val erasedGsonElement = linkedMapOf<String, Any>("actionId" to valid.actionId)

        assertTrue(sanitizeStoredActionMetrics(listOf(erasedGsonElement)).isEmpty())
        assertTrue(sanitizeStoredActionMetrics(listOf(valid)).single() == valid)
    }

    private fun validReport(): ModelQualityEvaluationReport = ModelQualityEvaluationReport(
        reportId = "00000000-0000-4000-8000-000000000001",
        telemetryId = "00000000-0000-4000-8000-000000000002",
        modelGenerationId = "00000000-0000-4000-8000-000000000003",
        windowId = "00000000-0000-4000-8000-000000000004",
        revocationCapabilityHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        windowStartDay = "2026-07-01",
        windowEndDay = "2026-07-31",
        statLearnedDays = 30,
        tinyLearnedDays = 24,
        eligibleSampleCount = 64,
        organicNonNoneSampleCount = 56,
        statistical = ModelAggregate(1, 30, 50, 38.0, 42.0, 55.0),
        tinyMlp = ModelAggregate(1, 34, 53, 41.0, 38.0, 50.0),
        pairwise = PairwiseAggregate(tinyWins = 30, statWins = 20, ties = 14, pairedSampleCount = 64),
        appVersionCode = 100,
        metricSchemaVersion = 1,
        featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
        outputSchemaVersion = AppActionCatalog.OUTPUT_SCHEMA_VERSION,
        actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
        trainingConfigVersion = TRAINING_CONFIG_VERSION,
        perAction = listOf(validAction()),
        statInferenceNanosSum = 64_000,
        tinyInferenceNanosSum = 96_000,
        trainingNanosSum = 500_000,
        modelSizeBytesMax = 50_000
    )

    private fun validAction(): ActionMetricSums {
        val statistical = ModelMetricSums(14, 24, 18.0, 21.0, 27.0)
        val tiny = ModelMetricSums(16, 25, 20.0, 19.0, 25.0)
        return ActionMetricSums(
            actionId = "VIEW_SCHEDULE",
            eligibleSampleCount = 32,
            pairedSampleCount = 32,
            statistical = statistical,
            tinyMlp = tiny,
            pairwise = PairwiseAggregate(15, 10, 7, 32)
        )
    }

    private fun validV3Report() = ModelQualityV3TaskReport(
        reportId = "00000000-0000-4000-8000-000000000011",
        telemetryId = "00000000-0000-4000-8000-000000000012",
        modelGenerationId = "00000000-0000-4000-8000-000000000013",
        windowId = "00000000-0000-4000-8000-000000000014",
        revocationCapabilityHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        task = "DELIVERY",
        windowStartDay = "2026-08-01",
        windowEndDay = "2026-08-05",
        sampleCount = 64,
        naturalHoldoutSampleCount = 0,
        appVersionCode = 100,
        featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
        outputSchemaVersion = AppActionCatalog.OUTPUT_SCHEMA_VERSION,
        metricSchemaVersion = 2,
        delivery = V3DeliveryAggregate(
            lanes = listOf(
                V3DeliveryLaneAggregate(
                    lane = "ORDINARY_NEXT_ACTION",
                    opportunities = 20,
                    modelGatePassed = 12,
                    enteredVisibleSurface = 8,
                    clicked = 4,
                    completed = 3,
                    dismissed = 2,
                    timedOut = 2,
                    blocked = listOf(V3NamedCount("MODEL_CONFIDENCE", 8)),
                    assistedRewardCount = 3,
                    assistedRewardWeightSum = 0.75
                )
            )
        )
    )
}
