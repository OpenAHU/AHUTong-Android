package com.ahu.ahutong.personalization.model

import android.content.Context
import android.util.AtomicFile
import com.ahu.ahutong.personalization.action.AppActionCatalog
import com.ahu.ahutong.personalization.context.FeatureExtractor
import com.ahu.ahutong.personalization.inference.AdamWState
import com.ahu.ahutong.personalization.inference.TinyMlpParameters
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import com.ahu.ahutong.personalization.model.withModelIoLock

data class ModelCheckpoint(
    val checkpointId: String,
    val checksum: String,
    val trainingRevision: Long,
    val parameters: TinyMlpParameters
)

data class StoredModelState(
    val profileKey: String,
    val modelGenerationId: String,
    val tinyModelVersion: Int,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val actionCatalogVersion: Int,
    val trainingConfigVersion: Int,
    val active: ModelCheckpoint,
    val candidate: ModelCheckpoint?,
    val training: ModelCheckpoint,
    val lastGoodActive: ModelCheckpoint,
    val optimizer: AdamWState,
    val lastAppliedBatchId: String?,
    val candidateSourceSampleCount: Int?,
    val candidateCreatedAtEpochMs: Long?,
    val lastConsumedCandidateSampleCount: Int,
    val lastConsumedCandidateRevision: Long,
    val updatedAtEpochMs: Long
)

internal const val TRAINING_CONFIG_VERSION = 1

@Singleton
class ModelStateStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val directory by lazy { File(context.noBackupFilesDir, "behavior_models").apply { mkdirs() } }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val secureRandom = SecureRandom()

    suspend fun loadOrCreate(profileKey: String): StoredModelState = lock(profileKey).withModelIoLock {
        read(profileKey) ?: create(profileKey).also { write(profileKey, it) }
    }

    suspend fun activeCheckpoint(profileKey: String): ModelCheckpoint =
        loadOrCreate(profileKey).active.deepCopy()

    suspend fun state(profileKey: String): StoredModelState = loadOrCreate(profileKey).deepCopy()

    suspend fun commitTrainingBatch(
        profileKey: String,
        expectedRevision: Long,
        batchId: String,
        parameters: TinyMlpParameters,
        optimizer: AdamWState
    ): StoredModelState = lock(profileKey).withModelIoLock {
        val current = read(profileKey) ?: create(profileKey)
        if (current.lastAppliedBatchId == batchId) return@withModelIoLock current.deepCopy()
        check(current.training.trainingRevision == expectedRevision) { "stale training revision" }
        validate(parameters, optimizer)
        val nextRevision = expectedRevision + 1
        val trained = checkpoint(parameters.deepCopy(optimizer.step), nextRevision)
        val updated = current.copy(
            training = trained,
            optimizer = optimizer.deepCopy(),
            lastAppliedBatchId = batchId,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        write(profileKey, updated)
        updated.deepCopy()
    }

    suspend fun createCandidate(
        profileKey: String,
        expectedTrainingRevision: Long,
        sourceSampleCount: Int
    ): StoredModelState =
        lock(profileKey).withModelIoLock {
            val current = requireNotNull(read(profileKey))
            check(current.candidate == null) { "candidate already exists" }
            check(current.training.trainingRevision == expectedTrainingRevision) { "stale training revision" }
            check(expectedTrainingRevision - current.lastConsumedCandidateRevision >= MIN_CANDIDATE_NEW_REVISIONS) {
                "candidate requires new training revisions"
            }
            check(sourceSampleCount - current.lastConsumedCandidateSampleCount >= MIN_CANDIDATE_NEW_SAMPLES) {
                "candidate requires new organic samples"
            }
            val updated = current.copy(
                candidate = checkpoint(current.training.parameters.deepCopy(), expectedTrainingRevision),
                candidateSourceSampleCount = sourceSampleCount,
                candidateCreatedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
            write(profileKey, updated)
            updated.deepCopy()
        }

    suspend fun activateCandidate(profileKey: String, candidateId: String): StoredModelState =
        lock(profileKey).withModelIoLock {
            val current = requireNotNull(read(profileKey))
            val candidate = requireNotNull(current.candidate).also {
                check(it.checkpointId == candidateId)
            }
            val updated = current.copy(
                active = candidate.deepCopy(),
                lastGoodActive = candidate.deepCopy(),
                candidate = null,
                lastConsumedCandidateSampleCount = current.candidateSourceSampleCount
                    ?: current.lastConsumedCandidateSampleCount,
                lastConsumedCandidateRevision = candidate.trainingRevision,
                candidateSourceSampleCount = null,
                candidateCreatedAtEpochMs = null,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            write(profileKey, updated)
            updated.deepCopy()
        }

    suspend fun discardCandidate(profileKey: String) = lock(profileKey).withModelIoLock {
        read(profileKey)?.let { current ->
            val candidate = current.candidate
            write(
                profileKey,
                current.copy(
                    candidate = null,
                    lastConsumedCandidateSampleCount = current.candidateSourceSampleCount
                        ?: current.lastConsumedCandidateSampleCount,
                    lastConsumedCandidateRevision = candidate?.trainingRevision
                        ?: current.lastConsumedCandidateRevision,
                    candidateSourceSampleCount = null,
                    candidateCreatedAtEpochMs = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun recoverLastGood(profileKey: String): StoredModelState = lock(profileKey).withModelIoLock {
        val current = requireNotNull(read(profileKey))
        val rejectedCandidate = current.candidate
        val updated = current.copy(
            active = current.lastGoodActive.deepCopy(),
            candidate = null,
            lastConsumedCandidateSampleCount = current.candidateSourceSampleCount
                ?: current.lastConsumedCandidateSampleCount,
            lastConsumedCandidateRevision = rejectedCandidate?.trainingRevision
                ?: current.lastConsumedCandidateRevision,
            candidateSourceSampleCount = null,
            candidateCreatedAtEpochMs = null,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        write(profileKey, updated)
        updated.deepCopy()
    }

    suspend fun reset(profileKey: String): Unit = lock(profileKey).withModelIoLock {
        val target = file(profileKey)
        AtomicFile(target).delete()
        AtomicFile(migrationJournal(profileKey)).delete()
        directory.listFiles { value -> value.name.startsWith("${target.name}.corrupt.") }
            ?.forEach(File::delete)
        Unit
    }

    suspend fun modelSizeBytes(profileKey: String): Long = lock(profileKey).withModelIoLock {
        file(profileKey).takeIf(File::exists)?.length() ?: 0L
    }

    private fun create(profileKey: String): StoredModelState {
        val seed = secureRandom.nextLong()
        val params = TinyMlpParameters.initialize(seed = seed)
        val initial = checkpoint(params, 0)
        return StoredModelState(
            profileKey = profileKey,
            modelGenerationId = UUID.randomUUID().toString(),
            tinyModelVersion = TinyMlpParameters.MODEL_VERSION,
            featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
            outputSchemaVersion = AppActionCatalog.OUTPUT_SCHEMA_VERSION,
            actionCatalogVersion = AppActionCatalog.ACTION_CATALOG_VERSION,
            trainingConfigVersion = TRAINING_CONFIG_VERSION,
            active = initial.deepCopy(),
            candidate = null,
            training = initial.deepCopy(),
            lastGoodActive = initial.deepCopy(),
            optimizer = AdamWState.create(params),
            lastAppliedBatchId = null,
            candidateSourceSampleCount = null,
            candidateCreatedAtEpochMs = null,
            lastConsumedCandidateSampleCount = 0,
            lastConsumedCandidateRevision = 0,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun checkpoint(parameters: TinyMlpParameters, revision: Long): ModelCheckpoint {
        validate(parameters, null)
        val checksum = checksum(parameters)
        return ModelCheckpoint(UUID.randomUUID().toString(), checksum, revision, parameters.deepCopy())
    }

    private fun validate(parameters: TinyMlpParameters, optimizer: AdamWState?) {
        require(parameters.inputSize == FeatureExtractor.INPUT_DIMENSION)
        require(parameters.hidden1Size == 32 && parameters.hidden2Size == 16)
        require(parameters.outputSize == AppActionCatalog.outputIds.size)
        require(parameters.allArrays().all { values -> values.all { it.isFinite() } })
        optimizer?.let {
            require(it.firstMoments.size == parameters.allArrays().size)
            require(it.secondMoments.size == parameters.allArrays().size)
            require(it.firstMoments.zip(parameters.allArrays()).all { (moments, values) -> moments.size == values.size && moments.all { value -> value.isFinite() } })
            require(it.secondMoments.zip(parameters.allArrays()).all { (moments, values) -> moments.size == values.size && moments.all { value -> value.isFinite() } })
        }
    }

    private fun write(profileKey: String, state: StoredModelState) {
        require(state.profileKey == profileKey)
        val payload = encode(state)
        require(payload.size <= MAX_STATE_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val atomic = AtomicFile(file(profileKey))
        val output = atomic.startWrite()
        try {
            DataOutputStream(output).also {
                it.writeInt(FILE_MAGIC)
                it.writeInt(SERIALIZATION_VERSION)
                it.writeInt(payload.size)
                it.write(payload)
                it.writeInt(digest.size)
                it.write(digest)
                it.flush()
            }
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun read(profileKey: String): StoredModelState? {
        val target = file(profileKey)
        if (!target.exists()) return null
        return runCatching {
            val payload = readPayload(target)
            val decoded = decode(payload)
            val migrated = migrateIfSupported(decoded)
            if (migrated !== decoded) {
                writeMigrationJournal(profileKey, payload)
                write(profileKey, migrated)
                migrationJournal(profileKey).delete()
            }
            validateStoredState(profileKey, migrated)
        }.getOrElse {
            recoverMigrationJournal(profileKey) ?: run {
                quarantineCorrupt(target)
                null
            }
        }
    }

    private fun readPayload(target: File): ByteArray = DataInputStream(AtomicFile(target).openRead()).use { input ->
        require(input.readInt() == FILE_MAGIC)
        require(input.readInt() == SERIALIZATION_VERSION)
        val length = input.readInt()
        require(length in 1..MAX_STATE_BYTES)
        val payload = ByteArray(length).also(input::readFully)
        val digestLength = input.readInt()
        require(digestLength == 32)
        val expected = ByteArray(digestLength).also(input::readFully)
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        require(MessageDigest.isEqual(expected, actual))
        payload
    }

    private fun validateStoredState(profileKey: String, state: StoredModelState): StoredModelState = state.also {
        require(it.profileKey == profileKey)
        require(it.tinyModelVersion == TinyMlpParameters.MODEL_VERSION)
        require(it.featureSchemaVersion == FeatureExtractor.FEATURE_SCHEMA_VERSION)
        require(it.outputSchemaVersion == AppActionCatalog.OUTPUT_SCHEMA_VERSION)
        require(it.actionCatalogVersion == AppActionCatalog.ACTION_CATALOG_VERSION)
        require(it.trainingConfigVersion == TRAINING_CONFIG_VERSION)
        require(it.active.checksum == checksum(it.active.parameters))
        require(it.training.checksum == checksum(it.training.parameters))
        require(it.lastGoodActive.checksum == checksum(it.lastGoodActive.parameters))
        it.candidate?.let { candidate -> require(candidate.checksum == checksum(candidate.parameters)) }
        validate(it.active.parameters, null)
        validate(it.training.parameters, it.optimizer)
        validate(it.lastGoodActive.parameters, null)
    }

    private fun migrateIfSupported(state: StoredModelState): StoredModelState {
        if (state.featureSchemaVersion == FeatureExtractor.FEATURE_SCHEMA_VERSION) return state
        require(state.featureSchemaVersion == LEGACY_FEATURE_SCHEMA_VERSION)
        require(state.outputSchemaVersion == AppActionCatalog.OUTPUT_SCHEMA_VERSION)
        require(state.actionCatalogVersion == AppActionCatalog.ACTION_CATALOG_VERSION)
        require(state.trainingConfigVersion == TRAINING_CONFIG_VERSION)
        val migratedActive = migrateCheckpoint(state.active)
        val migratedCandidate = state.candidate?.let(::migrateCheckpoint)
        val migratedTraining = migrateCheckpoint(state.training)
        val migratedLastGood = migrateCheckpoint(state.lastGoodActive)
        return state.copy(
            featureSchemaVersion = FeatureExtractor.FEATURE_SCHEMA_VERSION,
            active = migratedActive,
            candidate = migratedCandidate,
            training = migratedTraining,
            lastGoodActive = migratedLastGood,
            optimizer = NextActionSchemaMigrator.migrateOptimizer(state.optimizer, state.training.parameters),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun migrateCheckpoint(checkpoint: ModelCheckpoint): ModelCheckpoint {
        val parameters = NextActionSchemaMigrator.migrateParameters(checkpoint.parameters)
        return checkpoint.copy(checksum = checksum(parameters), parameters = parameters)
    }

    private fun writeMigrationJournal(profileKey: String, legacyPayload: ByteArray) {
        val atomic = AtomicFile(migrationJournal(profileKey))
        val output = atomic.startWrite()
        try {
            DataOutputStream(output).also { stream ->
                stream.writeInt(MIGRATION_JOURNAL_MAGIC)
                stream.writeInt(legacyPayload.size)
                stream.write(legacyPayload)
                stream.write(MessageDigest.getInstance("SHA-256").digest(legacyPayload))
                stream.flush()
            }
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun recoverMigrationJournal(profileKey: String): StoredModelState? {
        val journal = migrationJournal(profileKey)
        if (!journal.exists()) return null
        return runCatching {
            val payload = DataInputStream(AtomicFile(journal).openRead()).use { input ->
                require(input.readInt() == MIGRATION_JOURNAL_MAGIC)
                val length = input.readInt()
                require(length in 1..MAX_STATE_BYTES)
                val bytes = ByteArray(length).also(input::readFully)
                val expected = ByteArray(32).also(input::readFully)
                require(MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(bytes)))
                bytes
            }
            val migrated = migrateIfSupported(decode(payload))
            validateStoredState(profileKey, migrated)
            write(profileKey, migrated)
            journal.delete()
            migrated
        }.getOrNull()
    }

    private fun encode(state: StoredModelState): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(state.profileKey)
            output.writeUTF(state.modelGenerationId)
            output.writeInt(state.tinyModelVersion)
            output.writeInt(state.featureSchemaVersion)
            output.writeInt(state.outputSchemaVersion)
            output.writeInt(state.actionCatalogVersion)
            output.writeInt(state.trainingConfigVersion)
            writeCheckpoint(output, state.active)
            output.writeBoolean(state.candidate != null)
            state.candidate?.let { writeCheckpoint(output, it) }
            writeCheckpoint(output, state.training)
            writeCheckpoint(output, state.lastGoodActive)
            output.writeLong(state.optimizer.step)
            writeArrays(output, state.optimizer.firstMoments)
            writeArrays(output, state.optimizer.secondMoments)
            output.writeBoolean(state.lastAppliedBatchId != null)
            state.lastAppliedBatchId?.let(output::writeUTF)
            output.writeBoolean(state.candidateSourceSampleCount != null)
            state.candidateSourceSampleCount?.let(output::writeInt)
            output.writeBoolean(state.candidateCreatedAtEpochMs != null)
            state.candidateCreatedAtEpochMs?.let(output::writeLong)
            output.writeInt(state.lastConsumedCandidateSampleCount)
            output.writeLong(state.lastConsumedCandidateRevision)
            output.writeLong(state.updatedAtEpochMs)
        }
        bytes.toByteArray()
    }

    private fun decode(payload: ByteArray): StoredModelState = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        val profileKey = input.readUTF()
        val generation = input.readUTF()
        val tinyModelVersion = input.readInt()
        val featureSchemaVersion = input.readInt()
        val outputSchemaVersion = input.readInt()
        val actionCatalogVersion = input.readInt()
        val trainingConfigVersion = input.readInt()
        val active = readCheckpoint(input)
        val candidate = if (input.readBoolean()) readCheckpoint(input) else null
        val training = readCheckpoint(input)
        val lastGood = readCheckpoint(input)
        val step = input.readLong()
        val first = readArrays(input)
        val second = readArrays(input)
        val lastBatch = if (input.readBoolean()) input.readUTF() else null
        val candidateSourceSampleCount = if (input.readBoolean()) input.readInt() else null
        val candidateCreatedAtEpochMs = if (input.readBoolean()) input.readLong() else null
        val lastConsumedCandidateSampleCount = input.readInt()
        val lastConsumedCandidateRevision = input.readLong()
        val updated = input.readLong()
        StoredModelState(
            profileKey,
            generation,
            tinyModelVersion,
            featureSchemaVersion,
            outputSchemaVersion,
            actionCatalogVersion,
            trainingConfigVersion,
            active,
            candidate,
            training,
            lastGood,
            AdamWState(first, second, step),
            lastBatch,
            candidateSourceSampleCount,
            candidateCreatedAtEpochMs,
            lastConsumedCandidateSampleCount,
            lastConsumedCandidateRevision,
            updated
        )
    }

    private fun writeCheckpoint(output: DataOutputStream, checkpoint: ModelCheckpoint) {
        output.writeUTF(checkpoint.checkpointId)
        output.writeUTF(checkpoint.checksum)
        output.writeLong(checkpoint.trainingRevision)
        writeParameters(output, checkpoint.parameters)
    }

    private fun readCheckpoint(input: DataInputStream): ModelCheckpoint = ModelCheckpoint(
        input.readUTF(), input.readUTF(), input.readLong(), readParameters(input)
    )

    private fun writeParameters(output: DataOutputStream, value: TinyMlpParameters) {
        output.writeInt(value.inputSize)
        output.writeInt(value.hidden1Size)
        output.writeInt(value.hidden2Size)
        output.writeInt(value.outputSize)
        output.writeLong(value.trainingSteps)
        writeArrays(output, value.allArrays())
    }

    private fun readParameters(input: DataInputStream): TinyMlpParameters {
        val inputSize = input.readInt()
        val hidden1 = input.readInt()
        val hidden2 = input.readInt()
        val outputSize = input.readInt()
        val steps = input.readLong()
        val arrays = readArrays(input)
        require(arrays.size == 6)
        return TinyMlpParameters(inputSize, hidden1, hidden2, outputSize, arrays[0], arrays[1], arrays[2], arrays[3], arrays[4], arrays[5], steps)
    }

    private fun writeArrays(output: DataOutputStream, arrays: List<FloatArray>) {
        output.writeInt(arrays.size)
        arrays.forEach { values ->
            output.writeInt(values.size)
            values.forEach(output::writeFloat)
        }
    }

    private fun readArrays(input: DataInputStream): List<FloatArray> {
        val count = input.readInt()
        require(count in 1..16)
        return List(count) {
            val size = input.readInt()
            require(size in 1..100_000)
            FloatArray(size) { input.readFloat() }
        }
    }

    private fun checksum(parameters: TinyMlpParameters): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { writeParameters(it, parameters) }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun quarantineCorrupt(target: File) {
        val quarantine = File(directory, "${target.name}.corrupt.${System.currentTimeMillis()}")
        if (!target.renameTo(quarantine)) target.delete()
        directory.listFiles { file -> file.name.startsWith("${target.name}.corrupt.") }
            ?.sortedByDescending(File::lastModified)
            ?.drop(1)
            ?.forEach(File::delete)
    }

    private fun file(profileKey: String): File {
        require(profileKey.matches(Regex("[a-f0-9]{32}")))
        return File(directory, "$profileKey.model")
    }

    private fun migrationJournal(profileKey: String): File = File(directory, "$profileKey.v3-v4.journal")

    private fun lock(profileKey: String): Mutex = locks.getOrPut(profileKey) { Mutex() }

    private fun StoredModelState.deepCopy() = copy(
        active = active.deepCopy(),
        candidate = candidate?.deepCopy(),
        training = training.deepCopy(),
        lastGoodActive = lastGoodActive.deepCopy(),
        optimizer = optimizer.deepCopy()
    )

    private fun ModelCheckpoint.deepCopy() = copy(parameters = parameters.deepCopy())

    private fun AdamWState.deepCopy() = AdamWState(
        firstMoments.map(FloatArray::copyOf),
        secondMoments.map(FloatArray::copyOf),
        step
    )

    private companion object {
        const val FILE_MAGIC = 0x4148554D
        const val SERIALIZATION_VERSION = 4
        const val LEGACY_FEATURE_SCHEMA_VERSION = 3
        const val MIGRATION_JOURNAL_MAGIC = 0x56335434
        const val MIN_CANDIDATE_NEW_REVISIONS = 32L
        const val MIN_CANDIDATE_NEW_SAMPLES = 64
        const val MAX_STATE_BYTES = 512 * 1024
    }
}
