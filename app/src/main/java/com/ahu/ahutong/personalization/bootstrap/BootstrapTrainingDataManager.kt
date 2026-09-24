package com.ahu.ahutong.personalization.bootstrap

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ahu.ahutong.BuildConfig
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.context.V3ToV4FeatureAdapter
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BehaviorDatabase
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.BootstrapTrainingBatchEntity
import com.ahu.ahutong.personalization.storage.BootstrapTrainingConsentEntity
import com.ahu.ahutong.personalization.storage.BootstrapTrainingExampleEntity
import com.ahu.ahutong.personalization.storage.JourneyTrainingSampleEntity
import com.ahu.ahutong.personalization.storage.PresetTrainingSampleEntity
import com.ahu.ahutong.personalization.storage.transaction
import com.ahu.ahutong.personalization.training.OrganicTrainingSample
import com.ahu.ahutong.personalization.training.TrainingFeedbackPolicy
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BootstrapPresetCapture(
    val profileKey: String,
    val domainId: String,
    val rawOpportunityId: String,
    val candidateOrdinal: Int,
    val features: ByteArray,
    val label: Boolean,
    val occurredEpochDay: Long,
    val feedbackSource: String,
    val sampleWeight: Float,
    val naturalHoldoutEligible: Boolean,
    val historical: Boolean = false
)

@Singleton
class BootstrapTrainingDataManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: BehaviorDatabase,
    private val dao: BehaviorDao,
    private val secretStore: BootstrapTrainingSecretStore,
    private val lifecycleStore: BootstrapTrainingLifecycleStore
) {
    private val mutex = Mutex()
    private val random = SecureRandom()
    private val gson = Gson()
    private val _status = MutableStateFlow(BootstrapContributionStatus())
    val status: StateFlow<BootstrapContributionStatus> = _status.asStateFlow()
    @Volatile private var activeProfile: String? = null

    suspend fun reconcileProfile(profileKey: String, enabled: Boolean, includeHistorical: Boolean) {
        activeProfile = profileKey
        migrateLegacyDeletionTombstones()
        val recoveredDeletion = reconcileLifecycleOutbox()
        val existing = dao.bootstrapTrainingConsent(profileKey)
        if (recoveredDeletion) scheduleUpload(false, immediate = true)
        when {
            enabled && existing == null -> setConsent(profileKey, true, includeHistorical)
            enabled && existing?.state == "ACTIVE" -> {
                var activeConsent = existing
                if (activeConsent.includeHistorical && !activeConsent.historicalBackfillCompleted) {
                    activeConsent = mutex.withLock {
                        val latest = dao.bootstrapTrainingConsent(profileKey)
                            ?.takeIf { it.state == "ACTIVE" }
                            ?: return@withLock activeConsent
                        backfillHistorical(latest).also { completed ->
                            dao.upsertBootstrapTrainingConsent(completed)
                        }
                    }
                }
                refreshStatus(profileKey)
                schedulePeriodicUpload()
                scheduleUpload(activeConsent.includeHistorical)
            }
            !enabled && existing != null -> revoke(profileKey)
            else -> _status.value = BootstrapContributionStatus()
        }
    }

    suspend fun setConsent(profileKey: String, enabled: Boolean, includeHistorical: Boolean = false) {
        if (!enabled) {
            revoke(profileKey)
            return
        }
        mutex.withLock {
            if (dao.bootstrapTrainingConsent(profileKey)?.state == "ACTIVE") return@withLock
            val now = System.currentTimeMillis()
            val lifecycleId = UUID.randomUUID().toString()
            val capability = randomBytesBase64(32)
            val encrypted = secretStore.createAndEncrypt(lifecycleId, capability)
            var state = BootstrapTrainingConsentEntity(
                profileKey = profileKey,
                consentLifecycleId = lifecycleId,
                participantId = UUID.randomUUID().toString(),
                secretAlias = encrypted.alias,
                encryptedRevocationCapability = encrypted.ciphertext,
                consentSchemaVersion = BOOTSTRAP_CONSENT_SCHEMA_VERSION,
                includeHistorical = includeHistorical,
                historicalBackfillCompleted = !includeHistorical,
                nextSequenceNo = 1L,
                contributedExampleCount = 0,
                lastUploadAtEpochMs = null,
                state = "ACTIVE",
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
            lifecycleStore.persistActive(state)
            runCatching { dao.upsertBootstrapTrainingConsent(state) }
                .onFailure {
                    lifecycleStore.markDeletion(state, now)
                    throw it
                }
            if (includeHistorical) {
                state = backfillHistorical(state)
                dao.upsertBootstrapTrainingConsent(state)
            }
        }
        refreshStatus(profileKey)
        schedulePeriodicUpload()
        scheduleUpload(includeHistorical)
    }

    suspend fun revoke(profileKey: String) {
        val alias = mutex.withLock {
            val consent = dao.bootstrapTrainingConsent(profileKey) ?: return@withLock null
            val now = System.currentTimeMillis()
            lifecycleStore.markDeletion(consent, now)
            database.transaction {
                dao.deleteBootstrapTrainingProfileState(profileKey)
            }
            consent.secretAlias
        }
        if (alias != null) scheduleUpload(false, immediate = true)
        if (activeProfile == profileKey) _status.value = BootstrapContributionStatus()
    }

    suspend fun captureNextAction(sample: OrganicTrainingSample) {
        if (sample.deliveryLane == "TARGETED") return
        val weight = TrainingFeedbackPolicy.sampleWeight(sample.labelSource)
        if (weight <= 0f) return
        append(
            profileKey = sample.input.profileKey,
            task = BootstrapTrainingTask.NEXT_ACTION,
            completeness = if (sample.availabilityMask == null) {
                BootstrapExampleCompleteness.LEGACY_PARTIAL
            } else {
                BootstrapExampleCompleteness.COMPLETE
            },
            featureSchemaVersion = sample.input.featureSchemaVersion,
            outputSchemaVersion = sample.input.outputSchemaVersion,
            actionCatalogVersion = sample.input.actionCatalogVersion,
            features = sample.input.features.toBytes(),
            availabilityMask = sample.availabilityMask,
            targetLabel = sample.targetOutputId,
            feedbackSource = sample.labelSource,
            sampleWeight = weight,
            deliveryLane = sample.deliveryLane,
            naturalHoldoutEligible = sample.naturalHoldoutEligible,
            occurredEpochDay = sample.input.snapshot.epochDay
        )
    }

    suspend fun captureJourney(sample: JourneyTrainingSampleEntity) {
        append(
            profileKey = sample.profileKey,
            task = BootstrapTrainingTask.JOURNEY_GOAL,
            completeness = BootstrapExampleCompleteness.COMPLETE,
            featureSchemaVersion = sample.featureSchemaVersion,
            outputSchemaVersion = sample.journeyOutputSchemaVersion,
            actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
            features = sample.features,
            availabilityMask = null,
            targetLabel = sample.targetActionId,
            feedbackSource = sample.labelSource,
            sampleWeight = 1f,
            journeyLengthBucket = journeyLengthBucket(sample.journeyLength),
            naturalHoldoutEligible = true,
            occurredEpochDay = sample.occurredEpochDay
        )
    }

    suspend fun capturePreset(sample: PresetTrainingSampleEntity, candidateOrdinal: Int) {
        capturePresetGroup(
            listOf(
                BootstrapPresetCapture(
                    profileKey = sample.profileKey,
                    domainId = sample.domainId,
                    rawOpportunityId = sample.opportunityId,
                    candidateOrdinal = candidateOrdinal,
                    features = sample.features,
                    label = sample.label,
                    occurredEpochDay = sample.occurredEpochDay,
                    feedbackSource = sample.feedbackSource,
                    sampleWeight = sample.sampleWeight,
                    naturalHoldoutEligible = sample.naturalHoldoutEligible
                )
            )
        )
    }

    suspend fun capturePresetGroup(values: List<BootstrapPresetCapture>) {
        if (values.isEmpty()) return
        require(values.map(BootstrapPresetCapture::profileKey).distinct().size == 1)
        require(values.map(BootstrapPresetCapture::domainId).distinct().size == 1)
        require(values.map(BootstrapPresetCapture::rawOpportunityId).distinct().size == 1)
        require(values.map(BootstrapPresetCapture::candidateOrdinal).distinct().size == values.size)
        if (values.all { it.sampleWeight >= 0.999f }) require(values.count(BootstrapPresetCapture::label) <= 1)
        val profileKey = values.first().profileKey
        var captured = false
        mutex.withLock {
            val consent = dao.bootstrapTrainingConsent(profileKey)?.takeIf { it.state == "ACTIVE" }
                ?: return@withLock
            val capability = secretStore.decrypt(consent.secretAlias, consent.encryptedRevocationCapability)
                ?: return@withLock
            val opportunityGroupId = hmacSha256(capability, values.first().rawOpportunityId)
            val now = System.currentTimeMillis()
            val entities = values.sortedBy(BootstrapPresetCapture::candidateOrdinal).mapIndexed { index, value ->
                BootstrapTrainingExampleEntity(
                    exampleId = UUID.randomUUID().toString(),
                    profileKey = profileKey,
                    consentLifecycleId = consent.consentLifecycleId,
                    participantId = consent.participantId,
                    sequenceNo = consent.nextSequenceNo + index,
                    task = BootstrapTrainingTask.PRESET_RANKING.name,
                    completeness = BootstrapExampleCompleteness.COMPLETE.name,
                    featureSchemaVersion = PRESET_FEATURE_SCHEMA_VERSION,
                    outputSchemaVersion = PRESET_OUTPUT_SCHEMA_VERSION,
                    actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
                    features = value.features.copyOf(),
                    availabilityMask = null,
                    targetLabel = if (value.label) "1" else "0",
                    feedbackSource = value.feedbackSource,
                    sampleWeight = value.sampleWeight,
                    deliveryLane = null,
                    domainId = value.domainId,
                    opportunityGroupId = opportunityGroupId,
                    candidateOrdinal = value.candidateOrdinal,
                    journeyLengthBucket = null,
                    naturalHoldoutEligible = value.naturalHoldoutEligible,
                    occurredEpochDay = value.occurredEpochDay,
                    historical = value.historical,
                    state = "PENDING",
                    batchId = null,
                    createdAtEpochMs = now
                ).also { BootstrapTrainingPayloadValidator.requireValidExample(it.toPayload()) }
            }
            database.transaction {
                entities.forEach { check(dao.insertBootstrapTrainingExample(it) != -1L) }
                dao.upsertBootstrapTrainingConsent(
                    consent.copy(
                        nextSequenceNo = consent.nextSequenceNo + entities.size,
                        updatedAtEpochMs = now
                    )
                )
            }
            enforceTaskCap(profileKey, BootstrapTrainingTask.PRESET_RANKING)
            captured = true
        }
        if (!captured) return
        if (activeProfile == profileKey) refreshStatus(profileKey)
        scheduleUpload(values.any(BootstrapPresetCapture::historical))
    }

    suspend fun prepareDueBatches() {
        migrateLegacyDeletionTombstones()
        cleanupExpiredQueueRows()
        dao.activeBootstrapTrainingConsents().forEach { consent ->
            mutex.withLock { prepareOneBatch(consent) }
        }
    }

    suspend fun refreshStatus(profileKey: String? = activeProfile) {
        val resolvedProfile = profileKey ?: return
        val consent = dao.bootstrapTrainingConsent(resolvedProfile)
        _status.value = if (consent?.state == "ACTIVE") {
            BootstrapContributionStatus(
                enabled = true,
                pendingExamples = dao.pendingBootstrapTrainingExampleCount(resolvedProfile),
                contributedExamples = consent.contributedExampleCount,
                lastUploadAtEpochMs = consent.lastUploadAtEpochMs,
                includeHistorical = consent.includeHistorical
            )
        } else {
            BootstrapContributionStatus()
        }
    }

    private suspend fun append(
        profileKey: String,
        task: BootstrapTrainingTask,
        completeness: BootstrapExampleCompleteness,
        featureSchemaVersion: Int,
        outputSchemaVersion: Int,
        actionCatalogVersion: Int,
        features: ByteArray,
        availabilityMask: ByteArray?,
        targetLabel: String,
        feedbackSource: String,
        sampleWeight: Float,
        deliveryLane: String? = null,
        domainId: String? = null,
        opportunityGroupId: String? = null,
        rawOpportunityId: String? = null,
        candidateOrdinal: Int? = null,
        journeyLengthBucket: Int? = null,
        naturalHoldoutEligible: Boolean,
        occurredEpochDay: Long,
        historical: Boolean = false
    ) {
        mutex.withLock {
            val consent = dao.bootstrapTrainingConsent(profileKey)?.takeIf { it.state == "ACTIVE" } ?: return
            val now = System.currentTimeMillis()
            val resolvedOpportunityGroupId = if (task == BootstrapTrainingTask.PRESET_RANKING && rawOpportunityId != null) {
                val capability = secretStore.decrypt(
                    consent.secretAlias,
                    consent.encryptedRevocationCapability
                ) ?: return@withLock
                hmacSha256(capability, rawOpportunityId)
            } else {
                opportunityGroupId
            }
            val value = BootstrapTrainingExampleEntity(
                exampleId = UUID.randomUUID().toString(),
                profileKey = profileKey,
                consentLifecycleId = consent.consentLifecycleId,
                participantId = consent.participantId,
                sequenceNo = consent.nextSequenceNo,
                task = task.name,
                completeness = completeness.name,
                featureSchemaVersion = featureSchemaVersion,
                outputSchemaVersion = outputSchemaVersion,
                actionCatalogVersion = actionCatalogVersion,
                features = features.copyOf(),
                availabilityMask = availabilityMask?.copyOf(),
                targetLabel = targetLabel,
                feedbackSource = feedbackSource,
                sampleWeight = sampleWeight,
                deliveryLane = deliveryLane,
                domainId = domainId,
                opportunityGroupId = resolvedOpportunityGroupId,
                candidateOrdinal = candidateOrdinal,
                journeyLengthBucket = journeyLengthBucket,
                naturalHoldoutEligible = naturalHoldoutEligible,
                occurredEpochDay = occurredEpochDay,
                historical = historical,
                state = "PENDING",
                batchId = null,
                createdAtEpochMs = now
            )
            val payload = value.toPayload()
            BootstrapTrainingPayloadValidator.requireValidExample(payload)
            val inserted = database.transaction {
                if (dao.insertBootstrapTrainingExample(value) == -1L) return@transaction false
                dao.upsertBootstrapTrainingConsent(
                    consent.copy(nextSequenceNo = consent.nextSequenceNo + 1, updatedAtEpochMs = now)
                )
                true
            }
            if (inserted) enforceTaskCap(profileKey, task)
        }
        if (activeProfile == profileKey) refreshStatus(profileKey)
        scheduleUpload(historical)
    }

    private suspend fun backfillHistorical(initial: BootstrapTrainingConsentEntity): BootstrapTrainingConsentEntity {
        var state = initial
        val minimumDay = LocalDate.now(ZoneOffset.UTC).minusDays(29).toEpochDay()
        dao.recentTrainingSamples(initial.profileKey, NEXT_ACTION_LIMIT)
            .asReversed()
            .filter { it.occurredEpochDay >= minimumDay }
            .filter { it.deliveryLane == "ORDINARY_NEXT_ACTION" || it.deliveryLane == "ORDINARY_JOURNEY" }
            .forEach { sample ->
                val adapted = V3ToV4FeatureAdapter.adapt(BinaryCodec.floats(sample.features), sample.featureSchemaVersion)
                state = insertHistorical(
                    state,
                    BootstrapTrainingTask.NEXT_ACTION,
                    featureSchemaVersion = com.ahu.ahutong.personalization.context.FeatureExtractor.FEATURE_SCHEMA_VERSION,
                    outputSchemaVersion = sample.outputSchemaVersion,
                    actionCatalogVersion = sample.actionCatalogVersion,
                    features = BinaryCodec.floats(adapted),
                    targetLabel = sample.targetActionId,
                    feedbackSource = sample.labelSource,
                    sampleWeight = TrainingFeedbackPolicy.sampleWeight(sample.labelSource),
                    naturalHoldoutEligible = false,
                    occurredEpochDay = sample.occurredEpochDay,
                    historicalSourceKey = "next:${sample.sampleId}"
                )
            }
        dao.recentJourneyTrainingSamples(initial.profileKey, JOURNEY_LIMIT)
            .asReversed()
            .filter { it.occurredEpochDay >= minimumDay }
            .forEach { sample ->
                state = insertHistorical(
                    state,
                    BootstrapTrainingTask.JOURNEY_GOAL,
                    featureSchemaVersion = sample.featureSchemaVersion,
                    outputSchemaVersion = sample.journeyOutputSchemaVersion,
                    actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
                    features = sample.features,
                    targetLabel = sample.targetActionId,
                    feedbackSource = sample.labelSource,
                    sampleWeight = 1f,
                    naturalHoldoutEligible = false,
                    occurredEpochDay = sample.occurredEpochDay,
                    journeyLengthBucket = journeyLengthBucket(sample.journeyLength),
                    historicalSourceKey = "journey:${sample.sampleId}"
                )
            }
        val queriedPresetRows = dao.recentPresetTrainingSamples(
            initial.profileKey,
            PRESET_LIMIT + PRESET_GROUP_MAX_SIZE
        )
        val completePresetRows = if (queriedPresetRows.size == PRESET_LIMIT + PRESET_GROUP_MAX_SIZE) {
            val boundaryOpportunity = queriedPresetRows.last().opportunityId
            queriedPresetRows.dropLastWhile { it.opportunityId == boundaryOpportunity }
        } else {
            queriedPresetRows
        }
        val presetRows = buildList {
            completePresetRows
                .filter { it.occurredEpochDay >= minimumDay }
                .groupBy(PresetTrainingSampleEntity::opportunityId)
                .values
                .forEach { group -> if (group.size <= PRESET_LIMIT - size) addAll(group) }
        }.groupBy(PresetTrainingSampleEntity::opportunityId)
        val capability = secretStore.decrypt(state.secretAlias, state.encryptedRevocationCapability)
            ?: return state
        presetRows.values.forEach { group ->
            state = insertHistoricalPresetGroup(state, group, capability)
        }
        return state.copy(
            historicalBackfillCompleted = true,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private suspend fun insertHistorical(
        consent: BootstrapTrainingConsentEntity,
        task: BootstrapTrainingTask,
        featureSchemaVersion: Int,
        outputSchemaVersion: Int,
        actionCatalogVersion: Int,
        features: ByteArray,
        targetLabel: String,
        feedbackSource: String,
        sampleWeight: Float,
        naturalHoldoutEligible: Boolean,
        occurredEpochDay: Long,
        journeyLengthBucket: Int? = null,
        domainId: String? = null,
        opportunityGroupId: String? = null,
        candidateOrdinal: Int? = null,
        historicalSourceKey: String
    ): BootstrapTrainingConsentEntity {
        if (sampleWeight <= 0f) return consent
        return database.transaction {
            val current = dao.bootstrapTrainingConsent(consent.profileKey)
                ?.takeIf { it.state == "ACTIVE" && it.consentLifecycleId == consent.consentLifecycleId }
                ?: return@transaction consent
            val now = System.currentTimeMillis()
            val entity = BootstrapTrainingExampleEntity(
                exampleId = UUID.nameUUIDFromBytes(
                    "bootstrap-history-v1|${current.consentLifecycleId}|${task.name}|$historicalSourceKey"
                        .toByteArray(Charsets.UTF_8)
                ).toString(),
                profileKey = current.profileKey,
                consentLifecycleId = current.consentLifecycleId,
                participantId = current.participantId,
                sequenceNo = current.nextSequenceNo,
                task = task.name,
                completeness = if (task == BootstrapTrainingTask.NEXT_ACTION) {
                    BootstrapExampleCompleteness.LEGACY_PARTIAL.name
                } else {
                    BootstrapExampleCompleteness.COMPLETE.name
                },
                featureSchemaVersion = featureSchemaVersion,
                outputSchemaVersion = outputSchemaVersion,
                actionCatalogVersion = actionCatalogVersion,
                features = features.copyOf(),
                availabilityMask = null,
                targetLabel = targetLabel,
                feedbackSource = feedbackSource,
                sampleWeight = sampleWeight,
                deliveryLane = if (task == BootstrapTrainingTask.NEXT_ACTION) "ORDINARY_NEXT_ACTION" else null,
                domainId = domainId,
                opportunityGroupId = opportunityGroupId,
                candidateOrdinal = candidateOrdinal,
                journeyLengthBucket = journeyLengthBucket,
                naturalHoldoutEligible = naturalHoldoutEligible,
                occurredEpochDay = occurredEpochDay,
                historical = true,
                state = "PENDING",
                batchId = null,
                createdAtEpochMs = now
            )
            if (runCatching {
                    BootstrapTrainingPayloadValidator.requireValidExample(entity.toPayload())
                }.isFailure
            ) return@transaction current
            if (dao.insertBootstrapTrainingExample(entity) == -1L) return@transaction current
            current.copy(nextSequenceNo = current.nextSequenceNo + 1, updatedAtEpochMs = now).also {
                dao.upsertBootstrapTrainingConsent(it)
            }
        }
    }

    private suspend fun insertHistoricalPresetGroup(
        consent: BootstrapTrainingConsentEntity,
        samples: List<PresetTrainingSampleEntity>,
        capability: String
    ): BootstrapTrainingConsentEntity {
        if (samples.isEmpty()) return consent
        val sorted = samples.sortedBy(PresetTrainingSampleEntity::rowId)
        val exampleIds = sorted.map { sample ->
            UUID.nameUUIDFromBytes(
                (
                    "bootstrap-history-v1|${consent.consentLifecycleId}|${BootstrapTrainingTask.PRESET_RANKING.name}|" +
                        "preset:${sample.opportunityId}:${sample.candidateId}"
                ).toByteArray(Charsets.UTF_8)
            ).toString()
        }
        return database.transaction {
            val current = dao.bootstrapTrainingConsent(consent.profileKey)
                ?.takeIf { it.state == "ACTIVE" && it.consentLifecycleId == consent.consentLifecycleId }
                ?: return@transaction consent
            if (dao.bootstrapTrainingExampleCountByIds(current.profileKey, exampleIds) != 0) {
                return@transaction current
            }
            val now = System.currentTimeMillis()
            val opportunityGroupId = hmacSha256(capability, sorted.first().opportunityId)
            val entities = sorted.mapIndexed { index, sample ->
                BootstrapTrainingExampleEntity(
                    exampleId = exampleIds[index],
                    profileKey = current.profileKey,
                    consentLifecycleId = current.consentLifecycleId,
                    participantId = current.participantId,
                    sequenceNo = current.nextSequenceNo + index,
                    task = BootstrapTrainingTask.PRESET_RANKING.name,
                    completeness = BootstrapExampleCompleteness.COMPLETE.name,
                    featureSchemaVersion = PRESET_FEATURE_SCHEMA_VERSION,
                    outputSchemaVersion = PRESET_OUTPUT_SCHEMA_VERSION,
                    actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
                    features = sample.features.copyOf(),
                    availabilityMask = null,
                    targetLabel = if (sample.label) "1" else "0",
                    feedbackSource = sample.feedbackSource,
                    sampleWeight = sample.sampleWeight,
                    deliveryLane = null,
                    domainId = sample.domainId,
                    opportunityGroupId = opportunityGroupId,
                    candidateOrdinal = sample.candidateOrdinal ?: index,
                    journeyLengthBucket = null,
                    naturalHoldoutEligible = sample.naturalHoldoutEligible,
                    occurredEpochDay = sample.occurredEpochDay,
                    historical = true,
                    state = "PENDING",
                    batchId = null,
                    createdAtEpochMs = now
                )
            }
            if (
                entities.map(BootstrapTrainingExampleEntity::domainId).distinct().size != 1 ||
                entities.map(BootstrapTrainingExampleEntity::candidateOrdinal).distinct().size != entities.size ||
                (entities.all { it.sampleWeight >= 0.999f } && entities.count { it.targetLabel == "1" } > 1)
            ) return@transaction current
            if (entities.any { entity ->
                    runCatching { BootstrapTrainingPayloadValidator.requireValidExample(entity.toPayload()) }.isFailure
                }
            ) return@transaction current
            entities.forEach { check(dao.insertBootstrapTrainingExample(it) != -1L) }
            current.copy(
                nextSequenceNo = current.nextSequenceNo + entities.size,
                updatedAtEpochMs = now
            ).also { dao.upsertBootstrapTrainingConsent(it) }
        }
    }

    internal suspend fun migrateLegacyDeletionTombstones() {
        dao.bootstrapTrainingDeletionTombstones().forEach { tombstone ->
            lifecycleStore.importLegacy(tombstone)
            dao.deleteBootstrapTrainingDeletionTombstone(tombstone.deletionId)
        }
    }

    internal suspend fun reconcileLifecycleOutbox(): Boolean = lifecycleStore.reconcileRoomLifecycles(
        dao.activeBootstrapTrainingConsents().mapTo(mutableSetOf()) { it.consentLifecycleId },
        System.currentTimeMillis()
    )

    private suspend fun cleanupExpiredQueueRows() {
        val now = System.currentTimeMillis()
        dao.deleteExpiredQuarantinedBootstrapTrainingExamples(now - QUARANTINE_RETENTION_MS)
        val staleBatchIds = dao.staleBootstrapTrainingBatchIds(now - BATCH_RETENTION_MS)
        if (staleBatchIds.isNotEmpty()) {
            database.transaction {
                dao.deleteBootstrapTrainingExamplesForBatches(staleBatchIds)
                dao.deleteBootstrapTrainingBatches(staleBatchIds)
            }
        }
    }

    private suspend fun prepareOneBatch(consent: BootstrapTrainingConsentEntity) {
        if (
            dao.activeBootstrapTrainingBatchCount(consent.profileKey) >= MAX_ACTIVE_BATCHES_PER_PROFILE ||
            dao.activeBootstrapTrainingBatchBytes(consent.profileKey) >= MAX_ACTIVE_BATCH_BYTES_PER_PROFILE
        ) return
        val pending = dao.pendingBootstrapTrainingExamples(consent.profileKey, MAX_PENDING_SCAN)
            .filter { it.consentLifecycleId == consent.consentLifecycleId }
        if (pending.isEmpty()) return
        val candidateGroups = pending.groupBy {
            listOf(
                it.task,
                it.featureSchemaVersion.toString(),
                it.outputSchemaVersion.toString(),
                it.actionCatalogVersion.toString()
            ).joinToString("|")
        }.values.sortedBy { rows -> rows.minOf(BootstrapTrainingExampleEntity::sequenceNo) }
        var task: String? = null
        var selected = emptyList<BootstrapTrainingExampleEntity>()
        for (rows in candidateGroups) {
            val candidateTask = rows.first().task
            if (candidateTask == BootstrapTrainingTask.PRESET_RANKING.name) {
                quarantineInvalidPresetGroups(rows)
            }
            val currentRows = dao.pendingBootstrapTrainingExamples(consent.profileKey, MAX_PENDING_SCAN)
                .filter {
                    it.consentLifecycleId == consent.consentLifecycleId &&
                        it.task == candidateTask &&
                        it.featureSchemaVersion == rows.first().featureSchemaVersion &&
                        it.outputSchemaVersion == rows.first().outputSchemaVersion &&
                        it.actionCatalogVersion == rows.first().actionCatalogVersion
                }
            val candidate = BootstrapBatchSelectionPolicy.select(candidateTask, currentRows)
            if (candidate.isNotEmpty()) {
                task = candidateTask
                selected = candidate
                break
            }
        }
        val selectedTask = task ?: return
        val capability = runCatching {
            secretStore.decrypt(consent.secretAlias, consent.encryptedRevocationCapability)
        }.getOrNull() ?: return
        var batchRows = selected
        var request: BootstrapTrainingBatchRequest
        var body: ByteArray
        do {
            request = BootstrapTrainingBatchRequest(
                batchId = UUID.randomUUID().toString(),
                participantId = consent.participantId,
                consentLifecycleId = consent.consentLifecycleId,
                consentSchemaVersion = consent.consentSchemaVersion,
                revocationCapabilityHash = sha256(capability.toByteArray(Charsets.UTF_8)),
                appVersionCode = BuildConfig.VERSION_CODE,
                examples = batchRows.map(BootstrapTrainingExampleEntity::toPayload)
            )
            if (runCatching { BootstrapTrainingPayloadValidator.requireValid(request) }.isFailure) {
                dao.quarantineBootstrapTrainingExamples(batchRows.map(BootstrapTrainingExampleEntity::rowId))
                return
            }
            body = gson.toJson(request).toByteArray(Charsets.UTF_8)
            if (body.size > MAX_BODY_BYTES) batchRows = BootstrapBatchSelectionPolicy.reduce(selectedTask, batchRows)
        } while (body.size > MAX_BODY_BYTES && batchRows.isNotEmpty())
        if (batchRows.isEmpty()) return
        val now = System.currentTimeMillis()
        val batch = BootstrapTrainingBatchEntity(
            batchId = request.batchId,
            profileKey = consent.profileKey,
            consentLifecycleId = consent.consentLifecycleId,
            participantId = consent.participantId,
            protocolVersion = BOOTSTRAP_PROTOCOL_VERSION,
            body = body,
            bodySha256 = sha256(body),
            exampleCount = batchRows.size,
            containsHistorical = batchRows.any { it.historical },
            state = "READY",
            attemptCount = 0,
            nextAttemptAtEpochMs = now,
            lastErrorCode = null,
            createdAtEpochMs = now,
            acknowledgedAtEpochMs = null
        )
        database.transaction {
            dao.insertBootstrapTrainingBatch(batch)
            check(dao.markBootstrapTrainingExamplesBatched(batchRows.map { it.rowId }, batch.batchId) == batchRows.size)
        }
    }

    private suspend fun quarantineInvalidPresetGroups(rows: List<BootstrapTrainingExampleEntity>) {
        val invalid = BootstrapBatchSelectionPolicy.invalidPresetRowIds(rows)
        if (invalid.isNotEmpty()) dao.quarantineBootstrapTrainingExamples(invalid)
    }

    private suspend fun enforceTaskCap(profileKey: String, task: BootstrapTrainingTask) {
        val limit = when (task) {
            BootstrapTrainingTask.NEXT_ACTION -> NEXT_ACTION_LIMIT
            BootstrapTrainingTask.JOURNEY_GOAL -> JOURNEY_LIMIT
            BootstrapTrainingTask.PRESET_RANKING -> PRESET_LIMIT
        }
        val count = dao.pendingBootstrapTrainingTaskCount(profileKey, task.name)
        if (count <= limit) return
        if (task != BootstrapTrainingTask.PRESET_RANKING) {
            dao.evictOldestPendingBootstrapTrainingExamples(profileKey, task.name, count - limit)
            return
        }
        var toRemove = count - limit
        val groups = dao.pendingBootstrapTrainingExamples(profileKey, MAX_PENDING_SCAN)
            .filter { it.task == BootstrapTrainingTask.PRESET_RANKING.name }
            .groupBy(BootstrapTrainingExampleEntity::opportunityGroupId)
            .values
            .sortedBy { group -> group.minOf(BootstrapTrainingExampleEntity::sequenceNo) }
        val rowIds = buildList {
            for (group in groups) {
                if (toRemove <= 0) break
                addAll(group.map(BootstrapTrainingExampleEntity::rowId))
                toRemove -= group.size
            }
        }
        if (rowIds.isNotEmpty()) dao.deletePendingBootstrapTrainingExamples(rowIds)
    }

    private fun schedulePeriodicUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BootstrapTrainingDataWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleUpload(historical: Boolean, immediate: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (historical) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(historical)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<BootstrapTrainingDataWorker>()
            .setConstraints(constraints)
            .setInitialDelay(if (immediate) 0 else 1, if (immediate) TimeUnit.MILLISECONDS else TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            when {
                immediate -> DELETION_WORK_NAME
                historical -> HISTORICAL_WORK_NAME
                else -> REGULAR_WORK_NAME
            },
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    internal fun scheduleRetryAt(nextAttemptAtEpochMs: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val delayMs = (nextAttemptAtEpochMs - System.currentTimeMillis()).coerceAtLeast(1L)
        val request = OneTimeWorkRequestBuilder<BootstrapTrainingDataWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun randomBytesBase64(size: Int): String = ByteArray(size)
        .also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun hmacSha256(secret: String, value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }

    private fun journeyLengthBucket(length: Int): Int = when {
        length <= 0 -> 0
        length == 1 -> 1
        length == 2 -> 2
        length <= 4 -> 3
        else -> 4
    }

    private companion object {
        const val NEXT_ACTION_LIMIT = 2_048
        const val JOURNEY_LIMIT = 1_024
        const val PRESET_LIMIT = 2_048
        const val PRESET_GROUP_MAX_SIZE = 4
        const val MAX_PENDING_SCAN = 4_096
        const val MAX_BODY_BYTES = 512 * 1024
        const val MAX_ACTIVE_BATCHES_PER_PROFILE = 3
        const val MAX_ACTIVE_BATCH_BYTES_PER_PROFILE = 2L * 1024L * 1024L
        const val QUARANTINE_RETENTION_MS = 7L * 24L * 60L * 60L * 1_000L
        const val BATCH_RETENTION_MS = 30L * 24L * 60L * 60L * 1_000L
        const val PRESET_FEATURE_SCHEMA_VERSION = 1
        const val PRESET_OUTPUT_SCHEMA_VERSION = 1
        const val PERIODIC_WORK_NAME = "bootstrap_training_periodic"
        const val REGULAR_WORK_NAME = "bootstrap_training_regular"
        const val HISTORICAL_WORK_NAME = "bootstrap_training_historical"
        const val DELETION_WORK_NAME = "bootstrap_training_deletion"
        const val RETRY_WORK_NAME = "bootstrap_training_retry"
    }
}
