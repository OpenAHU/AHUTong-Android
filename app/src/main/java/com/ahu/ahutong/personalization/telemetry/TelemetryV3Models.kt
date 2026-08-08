package com.ahu.ahutong.personalization.telemetry

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDate
import java.util.UUID

enum class TelemetryV3Task {
    NEXT_ACTION,
    JOURNEY_GOAL,
    PRESET_RANKING,
    CANDIDATE_SHADOW,
    DELIVERY
}

data class V3CalibrationBin(
    val lowerPermilleInclusive: Int,
    val upperPermilleExclusive: Int,
    val sampleCount: Int,
    val correctCount: Int
)

data class V3ModelMetricAggregate(
    val sampleCount: Int = 0,
    val top1Correct: Long = 0,
    val top3Hit: Long = 0,
    val reciprocalRankSum: Double = 0.0,
    val brierSum: Double = 0.0,
    val logLossSum: Double = 0.0,
    val calibration: List<V3CalibrationBin> = emptyCalibrationBins()
)

data class V3PairwiseAggregate(
    val firstWins: Int = 0,
    val secondWins: Int = 0,
    val ties: Int = 0
)

data class V3NamedCount(val name: String, val count: Int)

data class V3ClassificationAggregate(
    val nonNoneSampleCount: Int = 0,
    val statistical: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val tinyMlp: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val effective: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val recentBaseline: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val timeBaseline: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val tinyVsStat: V3PairwiseAggregate = V3PairwiseAggregate(),
    val promotionHoldout: V3PromotionHoldoutAggregate = V3PromotionHoldoutAggregate(),
    val journeyLengthBuckets: List<V3NamedCount> = emptyList(),
    val stageCounts: List<V3NamedCount> = emptyList(),
    val tierCounts: List<V3NamedCount> = emptyList(),
    val statInferenceNanosSum: Long = 0,
    val tinyInferenceNanosSum: Long = 0,
    val trainingNanosSum: Long = 0,
    val modelSizeBytesMax: Long = 0
)

data class V3PromotionHoldoutAggregate(
    val statistical: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val tinyMlp: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val effective: V3ModelMetricAggregate = V3ModelMetricAggregate(),
    val tinyVsStat: V3PairwiseAggregate = V3PairwiseAggregate()
)

data class V3BinaryScoreAggregate(
    val sampleCount: Int = 0,
    val positiveCount: Int = 0,
    val scoreSum: Double = 0.0,
    val brierSum: Double = 0.0,
    val logLossSum: Double = 0.0,
    val calibration: List<V3CalibrationBin> = emptyCalibrationBins()
)

data class V3RankingAggregate(
    val naturalSampleCount: Int = 0,
    val assistedSampleCount: Int = 0,
    val statistical: V3BinaryScoreAggregate = V3BinaryScoreAggregate(),
    val tinyMlp: V3BinaryScoreAggregate = V3BinaryScoreAggregate(),
    val recentBaseline: V3BinaryScoreAggregate = V3BinaryScoreAggregate(),
    val frequencyBaseline: V3BinaryScoreAggregate = V3BinaryScoreAggregate(),
    val tinyVsStat: V3PairwiseAggregate = V3PairwiseAggregate(),
    val stageCounts: List<V3NamedCount> = emptyList(),
    val healthCounts: List<V3NamedCount> = emptyList(),
    val lambdaBucketCounts: List<V3NamedCount> = emptyList(),
    val eceSum: Double = 0.0,
    val eceSampleCount: Int = 0,
    val exposedCount: Int = 0,
    val appliedCount: Int = 0,
    val queryConfirmedCount: Int = 0,
    val replacedCount: Int = 0,
    val removedCount: Int = 0,
    val expiredWithoutLabelCount: Int = 0,
    val assistedFeedbackWeightSum: Double = 0.0
)

data class V3CandidateShadowAggregate(
    val activeTop3Hit: Int = 0,
    val candidateTop3Hit: Int = 0,
    val activeMrrSum: Double = 0.0,
    val candidateMrrSum: Double = 0.0,
    val activeBrierSum: Double = 0.0,
    val candidateBrierSum: Double = 0.0,
    val activeLogLossSum: Double = 0.0,
    val candidateLogLossSum: Double = 0.0,
    val candidateVsActive: V3PairwiseAggregate = V3PairwiseAggregate(),
    val activeInferenceNanosSum: Long = 0,
    val candidateInferenceNanosSum: Long = 0
)

data class V3DeliveryLaneAggregate(
    val lane: String,
    val opportunities: Int = 0,
    val modelGatePassed: Int = 0,
    val enteredVisibleSurface: Int = 0,
    val clicked: Int = 0,
    val completed: Int = 0,
    val dismissed: Int = 0,
    val timedOut: Int = 0,
    val blocked: List<V3NamedCount> = emptyList(),
    val assistedRewardCount: Int = 0,
    val assistedRewardWeightSum: Double = 0.0,
    val latencyBuckets: List<V3NamedCount> = emptyList()
)

data class V3DeliveryAggregate(
    val lanes: List<V3DeliveryLaneAggregate> = emptyList()
)

data class StoredTelemetryV3Aggregate(
    val storageSchemaVersion: Int = TELEMETRY_V3_STORAGE_SCHEMA_VERSION,
    val classification: V3ClassificationAggregate? = null,
    val ranking: V3RankingAggregate? = null,
    val candidateShadow: V3CandidateShadowAggregate? = null,
    val delivery: V3DeliveryAggregate? = null
)

/**
 * Stable boundary for V3 aggregate persistence.
 *
 * V3 aggregates written by the broken minified 3.2.1 build have obfuscated property names and no
 * explicit storage marker. Reconstructing them would couple runtime code to one build's R8 mapping,
 * so callers treat them as invalid and suppress only the affected telemetry window. The learning
 * database and all natural training samples remain untouched.
 */
internal class TelemetryV3AggregateCodec(
    private val gson: Gson = Gson()
) {
    fun encode(value: StoredTelemetryV3Aggregate): String = gson.toJson(value)

    fun decode(json: String, expectedTask: TelemetryV3Task? = null): StoredTelemetryV3Aggregate? =
        runCatching {
            val root = JsonParser.parseString(json).asJsonObject
            require(root.get("storageSchemaVersion")?.asInt == TELEMETRY_V3_STORAGE_SCHEMA_VERSION)
            gson.fromJson(root, StoredTelemetryV3Aggregate::class.java).also { value ->
                require(value.storageSchemaVersion == TELEMETRY_V3_STORAGE_SCHEMA_VERSION)
                require(value.hasTypedPayload(expectedTask))
            }
        }.getOrNull()

    private fun StoredTelemetryV3Aggregate.hasTypedPayload(expectedTask: TelemetryV3Task?): Boolean {
        val populated = listOfNotNull(classification, ranking, candidateShadow, delivery)
        if (populated.size != 1) return false
        return runCatching {
            when (expectedTask) {
                TelemetryV3Task.NEXT_ACTION, TelemetryV3Task.JOURNEY_GOAL -> classification!!.touchTypedFields()
                TelemetryV3Task.PRESET_RANKING -> ranking!!.touchTypedFields()
                TelemetryV3Task.CANDIDATE_SHADOW -> candidateShadow!!.touchTypedFields()
                TelemetryV3Task.DELIVERY -> delivery!!.touchTypedFields()
                null -> {
                    classification?.touchTypedFields()
                    ranking?.touchTypedFields()
                    candidateShadow?.touchTypedFields()
                    delivery?.touchTypedFields()
                }
            }
        }.isSuccess
    }

    private fun V3ClassificationAggregate.touchTypedFields() {
        listOf(statistical, tinyMlp, effective, recentBaseline, timeBaseline).forEach { it.touchTypedFields() }
        promotionHoldout.statistical.touchTypedFields()
        promotionHoldout.tinyMlp.touchTypedFields()
        promotionHoldout.effective.touchTypedFields()
        promotionHoldout.tinyVsStat.touchTypedFields()
        tinyVsStat.touchTypedFields()
        journeyLengthBuckets.touchTypedFields()
        stageCounts.touchTypedFields()
        tierCounts.touchTypedFields()
    }

    private fun V3ModelMetricAggregate.touchTypedFields() {
        calibration.forEach { bin ->
            require(bin.lowerPermilleInclusive <= bin.upperPermilleExclusive)
            require(bin.sampleCount >= 0 && bin.correctCount >= 0)
        }
    }

    private fun V3RankingAggregate.touchTypedFields() {
        listOf(statistical, tinyMlp, recentBaseline, frequencyBaseline).forEach { it.touchTypedFields() }
        tinyVsStat.touchTypedFields()
        stageCounts.touchTypedFields()
        healthCounts.touchTypedFields()
        lambdaBucketCounts.touchTypedFields()
    }

    private fun V3BinaryScoreAggregate.touchTypedFields() {
        calibration.forEach { bin ->
            require(bin.lowerPermilleInclusive <= bin.upperPermilleExclusive)
            require(bin.sampleCount >= 0 && bin.correctCount >= 0)
        }
    }

    private fun V3CandidateShadowAggregate.touchTypedFields() {
        candidateVsActive.touchTypedFields()
    }

    private fun V3DeliveryAggregate.touchTypedFields() {
        lanes.forEach { lane ->
            require(lane.lane.isNotEmpty())
            lane.blocked.touchTypedFields()
            lane.latencyBuckets.touchTypedFields()
        }
    }

    private fun V3PairwiseAggregate.touchTypedFields() {
        require(firstWins >= 0 && secondWins >= 0 && ties >= 0)
    }

    private fun List<V3NamedCount>.touchTypedFields() {
        forEach { value ->
            require(value.name.isNotEmpty())
            require(value.count >= 0)
        }
    }
}

data class ModelQualityV3TaskReport(
    val reportId: String,
    val telemetryId: String,
    val modelGenerationId: String,
    val windowId: String,
    val revocationCapabilityHash: String,
    val task: String,
    val windowStartDay: String,
    val windowEndDay: String,
    val sampleCount: Int,
    val naturalHoldoutSampleCount: Int,
    val appVersionCode: Int,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val metricSchemaVersion: Int,
    val classification: V3ClassificationAggregate? = null,
    val ranking: V3RankingAggregate? = null,
    val candidateShadow: V3CandidateShadowAggregate? = null,
    val delivery: V3DeliveryAggregate? = null
)

data class ModelQualityV3BatchRequest(
    val schemaVersion: Int = 3,
    val batchId: String,
    val reports: List<ModelQualityV3TaskReport>
)

object TelemetryV3PayloadValidator {
    fun requireValid(report: ModelQualityV3TaskReport) {
        requireUuid(report.reportId)
        requireUuid(report.telemetryId)
        requireUuid(report.modelGenerationId)
        requireUuid(report.windowId)
        require(LOWER_SHA256.matches(report.revocationCapabilityHash))
        require(report.task in TelemetryV3Task.entries.map(TelemetryV3Task::name))
        require(report.sampleCount >= MIN_TASK_SAMPLES)
        require(report.naturalHoldoutSampleCount in 0..report.sampleCount)
        require(report.appVersionCode > 0)
        require(report.featureSchemaVersion > 0 && report.outputSchemaVersion > 0)
        require(report.metricSchemaVersion == METRIC_SCHEMA_VERSION)
        val start = runCatching { LocalDate.parse(report.windowStartDay) }.getOrNull()
        val end = runCatching { LocalDate.parse(report.windowEndDay) }.getOrNull()
        require(start != null && end != null && !start.isAfter(end))
        require(listOfNotNull(report.classification, report.ranking, report.candidateShadow, report.delivery).size == 1)
        report.classification?.let(::requireClassification)
        report.ranking?.let(::requireRanking)
        report.candidateShadow?.let(::requireCandidate)
        report.delivery?.let(::requireDelivery)
    }

    private fun requireClassification(value: V3ClassificationAggregate) {
        require(value.nonNoneSampleCount >= 0)
        listOf(value.statistical, value.tinyMlp, value.effective, value.recentBaseline, value.timeBaseline)
            .forEach(::requireMetric)
        listOf(
            value.promotionHoldout.statistical,
            value.promotionHoldout.tinyMlp,
            value.promotionHoldout.effective
        ).forEach(::requireMetric)
        requireNamedCounts(value.journeyLengthBuckets, JOURNEY_BUCKETS)
        requireNamedCounts(value.stageCounts, STAGES)
        requireNamedCounts(value.tierCounts, TIERS)
        require(value.statInferenceNanosSum >= 0 && value.tinyInferenceNanosSum >= 0)
        require(value.trainingNanosSum >= 0 && value.modelSizeBytesMax in 0..(512L * 1024L))
    }

    private fun requireMetric(value: V3ModelMetricAggregate) {
        require(value.sampleCount >= 0)
        require(value.top1Correct in 0..value.sampleCount.toLong())
        require(value.top3Hit in value.top1Correct..value.sampleCount.toLong())
        require(value.reciprocalRankSum.isFinite() && value.reciprocalRankSum in 0.0..value.sampleCount.toDouble())
        require(value.brierSum.isFinite() && value.brierSum >= 0.0)
        require(value.logLossSum.isFinite() && value.logLossSum >= 0.0)
        requireCalibration(value.calibration, value.sampleCount)
    }

    private fun requireRanking(value: V3RankingAggregate) {
        listOf(value.statistical, value.tinyMlp, value.recentBaseline, value.frequencyBaseline).forEach { metric ->
            require(metric.sampleCount >= 0 && metric.positiveCount in 0..metric.sampleCount)
            require(metric.scoreSum.isFinite() && metric.scoreSum in 0.0..metric.sampleCount.toDouble())
            require(metric.brierSum.isFinite() && metric.brierSum >= 0.0)
            require(metric.logLossSum.isFinite() && metric.logLossSum >= 0.0)
            requireCalibration(metric.calibration, metric.sampleCount)
        }
        requireNamedCounts(value.stageCounts, STAGES)
        requireNamedCounts(value.healthCounts, HEALTH_STATES)
        requireNamedCounts(value.lambdaBucketCounts, LAMBDA_BUCKETS)
        require(value.eceSum.isFinite() && value.eceSum >= 0.0 && value.eceSampleCount >= 0)
        require(
            listOf(
                value.exposedCount,
                value.appliedCount,
                value.queryConfirmedCount,
                value.replacedCount,
                value.removedCount,
                value.expiredWithoutLabelCount
            ).all { it >= 0 }
        )
        require(value.assistedFeedbackWeightSum.isFinite() && value.assistedFeedbackWeightSum >= 0.0)
    }

    private fun requireCandidate(value: V3CandidateShadowAggregate) {
        require(value.activeTop3Hit >= 0 && value.candidateTop3Hit >= 0)
        require(value.activeMrrSum.isFinite() && value.candidateMrrSum.isFinite())
        require(value.activeBrierSum.isFinite() && value.candidateBrierSum.isFinite())
        require(value.activeLogLossSum.isFinite() && value.candidateLogLossSum.isFinite())
        require(value.activeInferenceNanosSum >= 0 && value.candidateInferenceNanosSum >= 0)
    }

    private fun requireDelivery(value: V3DeliveryAggregate) {
        require(value.lanes.map(V3DeliveryLaneAggregate::lane).distinct().size == value.lanes.size)
        value.lanes.forEach { lane ->
            require(lane.lane in DELIVERY_LANES)
            require(
                listOf(
                    lane.opportunities,
                    lane.modelGatePassed,
                    lane.enteredVisibleSurface,
                    lane.clicked,
                    lane.completed,
                    lane.dismissed,
                    lane.timedOut,
                    lane.assistedRewardCount
                ).all { it >= 0 }
            )
            require(lane.assistedRewardWeightSum.isFinite() && lane.assistedRewardWeightSum >= 0.0)
            requireNamedCounts(lane.blocked, DELIVERY_BLOCK_REASONS)
            requireNamedCounts(lane.latencyBuckets, LATENCY_BUCKETS)
        }
    }

    private fun requireCalibration(values: List<V3CalibrationBin>, samples: Int) {
        require(values.size == CALIBRATION_BIN_COUNT)
        require(values.sumOf(V3CalibrationBin::sampleCount) == samples)
        values.forEachIndexed { index, bin ->
            require(bin.lowerPermilleInclusive == index * 100)
            require(bin.upperPermilleExclusive == (index + 1) * 100)
            require(bin.correctCount in 0..bin.sampleCount)
        }
    }

    private fun requireNamedCounts(values: List<V3NamedCount>, allowed: Set<String>) {
        require(values.map(V3NamedCount::name).distinct().size == values.size)
        require(values.all { it.name in allowed && it.count >= 0 })
    }

    private fun requireUuid(value: String) {
        require(runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false))
    }

    private val LOWER_SHA256 = Regex("[0-9a-f]{64}")
}

internal const val TELEMETRY_V3_MIN_TASK_SAMPLES = 64
internal const val TELEMETRY_V3_METRIC_SCHEMA_VERSION = 2
internal const val TELEMETRY_V3_STORAGE_SCHEMA_VERSION = 1
// The production openahu.org endpoint accepts schema v3 credentials and batches.
internal const val TELEMETRY_SERVER_SCHEMA_VERSION = 3
internal const val CALIBRATION_BIN_COUNT = 10

internal fun emptyCalibrationBins(): List<V3CalibrationBin> = List(CALIBRATION_BIN_COUNT) { index ->
    V3CalibrationBin(index * 100, (index + 1) * 100, 0, 0)
}

internal val JOURNEY_BUCKETS = setOf("1", "2", "3", "4", "5_PLUS")
internal val STAGES = setOf("SHADOW", "MIXED", "PRIMARY", "UNKNOWN")
internal val TIERS = setOf("STAT_ONLY", "MIXED_25", "MIXED_50", "MIXED_75", "TINY_PRIMARY", "UNKNOWN")
internal val HEALTH_STATES = setOf("HEALTHY", "LATCHED_STAT_ONLY", "UNKNOWN")
internal val LAMBDA_BUCKETS = setOf("0", "25", "50", "75", "100", "OTHER")
internal val DELIVERY_LANES = setOf("TARGETED", "ORDINARY_JOURNEY", "ORDINARY_NEXT_ACTION")
internal val DELIVERY_BLOCK_REASONS = setOf(
    "HOLDOUT",
    "MODEL_CONFIDENCE",
    "MODEL_MARGIN",
    "NON_SUGGESTIBLE_DOMINATES",
    "INTERVAL",
    "SAFETY",
    "ENTRY_UNAVAILABLE",
    "OCCUPIED",
    "EXPIRED",
    "STALE",
    "OTHER"
)
internal val LATENCY_BUCKETS = setOf("LT_1S", "1_TO_5S", "5_TO_15S", "15_TO_60S", "GT_60S")

private const val MIN_TASK_SAMPLES = TELEMETRY_V3_MIN_TASK_SAMPLES
private const val METRIC_SCHEMA_VERSION = TELEMETRY_V3_METRIC_SCHEMA_VERSION
