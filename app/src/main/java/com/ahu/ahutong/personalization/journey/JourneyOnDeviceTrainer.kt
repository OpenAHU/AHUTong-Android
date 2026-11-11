package com.ahu.ahutong.personalization.journey

import android.util.Log
import com.ahu.ahutong.personalization.inference.AdamWState
import com.ahu.ahutong.personalization.bootstrap.BootstrapTrainingDataManager
import com.ahu.ahutong.personalization.inference.TinyMlpBackprop
import com.ahu.ahutong.personalization.model.ModelTask
import com.ahu.ahutong.personalization.storage.BehaviorDao
import com.ahu.ahutong.personalization.storage.BinaryCodec
import com.ahu.ahutong.personalization.storage.JourneyTrainingSampleEntity
import com.ahu.ahutong.personalization.storage.TaskTrainingBatchJournalEntity
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

data class JourneyTrainingSliceResult(
    val trained: Boolean,
    val profileKey: String?,
    val batches: Int,
    val samples: Int,
    val elapsedNanos: Long,
    val reason: String
)

internal object JourneyTrainingLabelPolicy {
    const val ORGANIC_JOURNEY = "ORGANIC_JOURNEY"
    const val INTERVENTION_FREE_TIMEOUT = "INTERVENTION_FREE_TIMEOUT"
    const val INTERVENTION_FREE_MAX_STEPS = "INTERVENTION_FREE_MAX_STEPS"

    val supportedSources = setOf(
        ORGANIC_JOURNEY,
        INTERVENTION_FREE_TIMEOUT,
        INTERVENTION_FREE_MAX_STEPS
    )

    fun accepts(labelSource: String): Boolean = labelSource in supportedSources
}

@Singleton
class JourneyOnDeviceTrainer @Inject constructor(
    private val dao: BehaviorDao,
    private val store: JourneyModelStateStore,
    private val bootstrapTrainingDataManager: BootstrapTrainingDataManager? = null
) {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "journey-tiny-trainer").apply { priority = Thread.MIN_PRIORITY }
    }.asCoroutineDispatcher()
    private val pendingProfiles = ConcurrentHashMap.newKeySet<String>()
    private val cancelledGenerations = ConcurrentHashMap<String, Long>()

    suspend fun enqueue(sample: JourneyTrainingSampleEntity) {
        if (!JourneyTrainingLabelPolicy.accepts(sample.labelSource)) {
            // Personalization is ancillary. A malformed training label must never terminate the
            // user-facing flow (for example, login navigation) from a background coroutine.
            Log.w(TAG, "Ignoring unsupported journey training label: ${sample.labelSource}")
            return
        }
        val inserted = dao.insertJourneyTrainingSample(sample)
        if (inserted != -1L) runCatching {
            bootstrapTrainingDataManager?.captureJourney(sample)
        }
        pendingProfiles += sample.profileKey
    }

    fun resumeProfile(profileKey: String) { pendingProfiles += profileKey }

    suspend fun cancelProfile(profileKey: String) {
        cancelledGenerations.merge(profileKey, 1L, Long::plus)
        pendingProfiles.remove(profileKey)
        withContext(dispatcher) { Unit }
    }

    suspend fun runIdleSlice(budgetMillis: Long): JourneyTrainingSliceResult = withContext(dispatcher) {
        val profileKey = pendingProfiles.firstOrNull()
            ?: return@withContext JourneyTrainingSliceResult(false, null, 0, 0, 0, "NO_PENDING_PROFILE")
        val generation = cancelledGenerations[profileKey] ?: 0L
        val total = dao.journeyTrainingSampleCount(profileKey)
        val nonNone = dao.journeyNonNoneSampleCount(profileKey)
        val families = dao.journeyTargetFamilyCount(profileKey)
        if (total < MIN_SAMPLES || nonNone < MIN_NON_NONE || families < MIN_FAMILIES) {
            return@withContext JourneyTrainingSliceResult(false, profileKey, 0, total, 0, "MINIMUM_SAMPLE_GATE")
        }
        val started = System.nanoTime()
        val deadline = started + budgetMillis.coerceIn(1, MAX_SLICE_MS) * 1_000_000L
        var batches = 0
        if (recoverPrepared(profileKey)) batches++
        while (batches < MAX_BATCHES && System.nanoTime() < deadline &&
            (cancelledGenerations[profileKey] ?: 0L) == generation
        ) {
            val selected = balancedBatch(dao.recentJourneyTrainingSamples(profileKey, 512), BATCH_SIZE)
            if (selected.size < BATCH_SIZE) break
            val state = store.state(profileKey)
            val parameters = state.training.parameters.deepCopy(state.optimizer.step)
            val optimizer = state.optimizer.deepCopy()
            TinyMlpBackprop.trainBatch(
                parameters,
                optimizer,
                selected.map { BinaryCodec.floats(it.features) },
                selected.map(JourneyTrainingSampleEntity::targetIndex).toIntArray()
            )
            val rowIds = selected.map(JourneyTrainingSampleEntity::rowId)
            val batchId = batchId(profileKey, state.training.trainingRevision, rowIds)
            check(dao.insertTaskTrainingJournal(
                TaskTrainingBatchJournalEntity(
                    batchId,
                    profileKey,
                    ModelTask.JOURNEY_GOAL.name,
                    state.training.trainingRevision,
                    rowIds.joinToString(","),
                    "PREPARED",
                    System.currentTimeMillis(),
                    null
                )
            ) != -1L)
            store.commitTrainingBatch(profileKey, state.training.trainingRevision, batchId, parameters, optimizer)
            check(dao.commitTaskTrainingJournal(batchId, System.currentTimeMillis()) == 1)
            dao.incrementJourneyTrainingCounts(rowIds)
            batches++
        }
        if (batches > 0) {
            store.maybeCreateCandidate(profileKey, total)
            pendingProfiles.remove(profileKey)
        }
        JourneyTrainingSliceResult(
            batches > 0,
            profileKey,
            batches,
            total,
            System.nanoTime() - started,
            if (batches > 0) "COMMITTED" else "BUDGET_OR_BATCH_GATE"
        )
    }

    private suspend fun recoverPrepared(profileKey: String): Boolean {
        val journal = dao.preparedTaskTrainingJournal(profileKey, ModelTask.JOURNEY_GOAL.name) ?: return false
        val rowIds = journal.selectedRowIds.split(',').mapNotNull(String::toLongOrNull)
        val state = store.state(profileKey)
        if (state.lastAppliedBatchId == journal.batchId) {
            check(dao.commitTaskTrainingJournal(journal.batchId, System.currentTimeMillis()) == 1)
            dao.incrementJourneyTrainingCounts(rowIds)
            return true
        }
        if (state.training.trainingRevision != journal.expectedTrainingRevision || rowIds.isEmpty()) {
            dao.abandonTaskTrainingJournal(journal.batchId, System.currentTimeMillis())
            return false
        }
        val byId = dao.recentJourneyTrainingSamples(profileKey, 2_048).associateBy(JourneyTrainingSampleEntity::rowId)
        val samples = rowIds.mapNotNull(byId::get)
        if (samples.size != rowIds.size) {
            dao.abandonTaskTrainingJournal(journal.batchId, System.currentTimeMillis())
            return false
        }
        val parameters = state.training.parameters.deepCopy(state.optimizer.step)
        val optimizer = state.optimizer.deepCopy()
        TinyMlpBackprop.trainBatch(
            parameters,
            optimizer,
            samples.map { BinaryCodec.floats(it.features) },
            samples.map(JourneyTrainingSampleEntity::targetIndex).toIntArray()
        )
        store.commitTrainingBatch(profileKey, state.training.trainingRevision, journal.batchId, parameters, optimizer)
        check(dao.commitTaskTrainingJournal(journal.batchId, System.currentTimeMillis()) == 1)
        dao.incrementJourneyTrainingCounts(rowIds)
        return true
    }

    private fun balancedBatch(values: List<JourneyTrainingSampleEntity>, size: Int): List<JourneyTrainingSampleEntity> {
        val none = values.filter { it.targetActionId == JourneyGoalCatalog.NONE_OUTPUT_ID }
            .sortedWith(compareBy<JourneyTrainingSampleEntity> { it.trainingCount }.thenByDescending { it.replayPriority })
        val groups = values.filter { it.targetActionId != JourneyGoalCatalog.NONE_OUTPUT_ID }
            .groupBy(JourneyTrainingSampleEntity::targetActionId)
            .values.map { ArrayDeque(it.sortedBy { sample -> sample.trainingCount }) }
        val result = mutableListOf<JourneyTrainingSampleEntity>()
        val nonNoneTarget = size - min(size / 2, none.size)
        while (result.size < nonNoneTarget && groups.any { it.isNotEmpty() }) {
            groups.forEach { if (result.size < nonNoneTarget && it.isNotEmpty()) result += it.removeFirst() }
        }
        result += none.take(size - result.size)
        return result.distinctBy(JourneyTrainingSampleEntity::rowId).take(size)
    }

    private fun batchId(profileKey: String, revision: Long, rowIds: List<Long>): String =
        MessageDigest.getInstance("SHA-256")
            .digest("journey|$profileKey|$revision|${rowIds.joinToString(",")}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun AdamWState.deepCopy() = AdamWState(firstMoments.map(FloatArray::copyOf), secondMoments.map(FloatArray::copyOf), step)

    private companion object {
        const val TAG = "JourneyTrainer"
        const val MIN_SAMPLES = 128
        const val MIN_NON_NONE = 64
        const val MIN_FAMILIES = 3
        const val BATCH_SIZE = 16
        const val MAX_BATCHES = 3
        const val MAX_SLICE_MS = 50L
    }
}
