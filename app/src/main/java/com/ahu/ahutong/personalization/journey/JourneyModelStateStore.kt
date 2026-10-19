package com.ahu.ahutong.personalization.journey

import android.content.Context
import android.util.AtomicFile
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

data class JourneyModelCheckpoint(
    val checkpointId: String,
    val checksum: String,
    val trainingRevision: Long,
    val parameters: TinyMlpParameters
)

data class JourneyStoredModelState(
    val profileKey: String,
    val modelGenerationId: String,
    val featureSchemaVersion: Int,
    val outputSchemaVersion: Int,
    val active: JourneyModelCheckpoint,
    val candidate: JourneyModelCheckpoint?,
    val training: JourneyModelCheckpoint,
    val lastGoodActive: JourneyModelCheckpoint,
    val optimizer: AdamWState,
    val lastAppliedBatchId: String?,
    val lastConsumedCandidateSampleCount: Int,
    val lastConsumedCandidateRevision: Long,
    val candidateSourceSampleCount: Int?,
    val updatedAtEpochMs: Long
)

@Singleton
class JourneyModelStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory by lazy { File(context.noBackupFilesDir, "behavior_models").apply { mkdirs() } }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val random = SecureRandom()

    suspend fun loadOrCreate(profileKey: String): JourneyStoredModelState = lock(profileKey).withModelIoLock {
        read(profileKey) ?: create(profileKey).also { write(profileKey, it) }
    }

    suspend fun state(profileKey: String): JourneyStoredModelState = loadOrCreate(profileKey).deepCopy()

    suspend fun commitTrainingBatch(
        profileKey: String,
        expectedRevision: Long,
        batchId: String,
        parameters: TinyMlpParameters,
        optimizer: AdamWState
    ): JourneyStoredModelState = lock(profileKey).withModelIoLock {
        val current = read(profileKey) ?: create(profileKey)
        if (current.lastAppliedBatchId == batchId) return@withModelIoLock current.deepCopy()
        check(current.training.trainingRevision == expectedRevision)
        validate(parameters, optimizer)
        val trained = checkpoint(parameters.deepCopy(optimizer.step), expectedRevision + 1)
        current.copy(
            training = trained,
            optimizer = optimizer.deepCopy(),
            lastAppliedBatchId = batchId,
            updatedAtEpochMs = System.currentTimeMillis()
        ).also { write(profileKey, it) }.deepCopy()
    }

    suspend fun maybeCreateCandidate(profileKey: String, sourceSampleCount: Int): JourneyStoredModelState =
        lock(profileKey).withModelIoLock {
            val current = read(profileKey) ?: create(profileKey)
            if (current.candidate != null ||
                current.training.trainingRevision - current.lastConsumedCandidateRevision < 32 ||
                sourceSampleCount - current.lastConsumedCandidateSampleCount < 64
            ) return@withModelIoLock current.deepCopy()
            current.copy(
                candidate = checkpoint(current.training.parameters.deepCopy(), current.training.trainingRevision),
                candidateSourceSampleCount = sourceSampleCount,
                updatedAtEpochMs = System.currentTimeMillis()
            ).also { write(profileKey, it) }.deepCopy()
        }

    suspend fun activateCandidate(profileKey: String, checkpointId: String): JourneyStoredModelState =
        lock(profileKey).withModelIoLock {
            val current = requireNotNull(read(profileKey))
            val candidate = requireNotNull(current.candidate).also { check(it.checkpointId == checkpointId) }
            current.copy(
                active = candidate.deepCopy(),
                lastGoodActive = candidate.deepCopy(),
                candidate = null,
                lastConsumedCandidateSampleCount = current.candidateSourceSampleCount ?: current.lastConsumedCandidateSampleCount,
                lastConsumedCandidateRevision = candidate.trainingRevision,
                candidateSourceSampleCount = null,
                updatedAtEpochMs = System.currentTimeMillis()
            ).also { write(profileKey, it) }.deepCopy()
        }

    suspend fun discardCandidate(profileKey: String) = lock(profileKey).withModelIoLock {
        val current = read(profileKey) ?: return@withModelIoLock
        current.copy(
            candidate = null,
            lastConsumedCandidateSampleCount = current.candidateSourceSampleCount ?: current.lastConsumedCandidateSampleCount,
            lastConsumedCandidateRevision = current.candidate?.trainingRevision ?: current.lastConsumedCandidateRevision,
            candidateSourceSampleCount = null,
            updatedAtEpochMs = System.currentTimeMillis()
        ).also { write(profileKey, it) }
    }

    suspend fun reset(profileKey: String) = lock(profileKey).withModelIoLock { AtomicFile(file(profileKey)).delete() }

    suspend fun modelSizeBytes(profileKey: String): Long = lock(profileKey).withModelIoLock {
        file(profileKey).takeIf(File::exists)?.length() ?: 0L
    }

    private fun create(profileKey: String): JourneyStoredModelState {
        val parameters = TinyMlpParameters.initialize(
            inputSize = FeatureExtractor.INPUT_DIMENSION,
            outputSize = JourneyGoalCatalog.outputIds.size,
            seed = random.nextLong()
        )
        val initial = checkpoint(parameters, 0)
        return JourneyStoredModelState(
            profileKey,
            UUID.randomUUID().toString(),
            FeatureExtractor.FEATURE_SCHEMA_VERSION,
            JourneyGoalCatalog.OUTPUT_SCHEMA_VERSION,
            initial.deepCopy(),
            null,
            initial.deepCopy(),
            initial.deepCopy(),
            AdamWState.create(parameters),
            null,
            0,
            0,
            null,
            System.currentTimeMillis()
        )
    }

    private fun checkpoint(parameters: TinyMlpParameters, revision: Long): JourneyModelCheckpoint =
        JourneyModelCheckpoint(UUID.randomUUID().toString(), checksum(parameters), revision, parameters.deepCopy())

    private fun validate(parameters: TinyMlpParameters, optimizer: AdamWState?) {
        require(parameters.inputSize == FeatureExtractor.INPUT_DIMENSION)
        require(parameters.hidden1Size == 32 && parameters.hidden2Size == 16)
        require(parameters.outputSize == JourneyGoalCatalog.outputIds.size)
        require(parameters.allArrays().all { it.all(Float::isFinite) })
        optimizer?.let { state ->
            require(state.firstMoments.zip(parameters.allArrays()).all { (a, b) -> a.size == b.size && a.all(Float::isFinite) })
            require(state.secondMoments.zip(parameters.allArrays()).all { (a, b) -> a.size == b.size && a.all(Float::isFinite) })
        }
    }

    private fun write(profileKey: String, state: JourneyStoredModelState) {
        val payload = encode(state)
        require(payload.size <= MAX_BYTES)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val atomic = AtomicFile(file(profileKey))
        val stream = atomic.startWrite()
        try {
            DataOutputStream(stream).also { output ->
                output.writeInt(FILE_MAGIC)
                output.writeInt(SERIALIZATION_VERSION)
                output.writeInt(payload.size)
                output.write(payload)
                output.write(digest)
                output.flush()
            }
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    private fun read(profileKey: String): JourneyStoredModelState? {
        val target = file(profileKey)
        if (!target.exists()) return null
        return runCatching {
            val payload = DataInputStream(AtomicFile(target).openRead()).use { input ->
                require(input.readInt() == FILE_MAGIC)
                require(input.readInt() == SERIALIZATION_VERSION)
                val length = input.readInt()
                require(length in 1..MAX_BYTES)
                val bytes = ByteArray(length).also(input::readFully)
                val digest = ByteArray(32).also(input::readFully)
                require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(bytes)))
                bytes
            }
            decode(payload).also { state ->
                require(state.profileKey == profileKey)
                require(state.featureSchemaVersion == FeatureExtractor.FEATURE_SCHEMA_VERSION)
                require(state.outputSchemaVersion == JourneyGoalCatalog.OUTPUT_SCHEMA_VERSION)
                listOf(state.active, state.training, state.lastGoodActive).forEach { checkpoint ->
                    require(checkpoint.checksum == checksum(checkpoint.parameters))
                    validate(checkpoint.parameters, null)
                }
                state.candidate?.let { require(it.checksum == checksum(it.parameters)) }
                validate(state.training.parameters, state.optimizer)
            }
        }.getOrElse {
            val quarantine = File(directory, "${target.name}.corrupt.${System.currentTimeMillis()}")
            if (!target.renameTo(quarantine)) target.delete()
            null
        }
    }

    private fun encode(state: JourneyStoredModelState): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(state.profileKey)
            output.writeUTF(state.modelGenerationId)
            output.writeInt(state.featureSchemaVersion)
            output.writeInt(state.outputSchemaVersion)
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
            output.writeInt(state.lastConsumedCandidateSampleCount)
            output.writeLong(state.lastConsumedCandidateRevision)
            output.writeBoolean(state.candidateSourceSampleCount != null)
            state.candidateSourceSampleCount?.let(output::writeInt)
            output.writeLong(state.updatedAtEpochMs)
        }
        bytes.toByteArray()
    }

    private fun decode(payload: ByteArray): JourneyStoredModelState = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        val profileKey = input.readUTF()
        val generation = input.readUTF()
        val featureSchema = input.readInt()
        val outputSchema = input.readInt()
        val active = readCheckpoint(input)
        val candidate = if (input.readBoolean()) readCheckpoint(input) else null
        val training = readCheckpoint(input)
        val lastGood = readCheckpoint(input)
        val optimizerStep = input.readLong()
        val firstMoments = readArrays(input)
        val secondMoments = readArrays(input)
        val lastBatch = if (input.readBoolean()) input.readUTF() else null
        val lastConsumedSampleCount = input.readInt()
        val lastConsumedRevision = input.readLong()
        val candidateSourceSampleCount = if (input.readBoolean()) input.readInt() else null
        val updatedAt = input.readLong()
        JourneyStoredModelState(
            profileKey,
            generation,
            featureSchema,
            outputSchema,
            active,
            candidate,
            training,
            lastGood,
            AdamWState(firstMoments, secondMoments, optimizerStep),
            lastBatch,
            lastConsumedSampleCount,
            lastConsumedRevision,
            candidateSourceSampleCount,
            updatedAt
        )
    }

    private fun writeCheckpoint(output: DataOutputStream, value: JourneyModelCheckpoint) {
        output.writeUTF(value.checkpointId)
        output.writeUTF(value.checksum)
        output.writeLong(value.trainingRevision)
        writeParameters(output, value.parameters)
    }

    private fun readCheckpoint(input: DataInputStream): JourneyModelCheckpoint =
        JourneyModelCheckpoint(input.readUTF(), input.readUTF(), input.readLong(), readParameters(input))

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

    private fun checksum(parameters: TinyMlpParameters): String = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { writeParameters(it, parameters) }
        MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun file(profileKey: String): File {
        require(profileKey.matches(Regex("[a-f0-9]{32}")))
        return File(directory, "$profileKey.journey.model")
    }

    private fun lock(profileKey: String): Mutex = locks.getOrPut(profileKey) { Mutex() }
    private fun JourneyModelCheckpoint.deepCopy() = copy(parameters = parameters.deepCopy())
    private fun JourneyStoredModelState.deepCopy() = copy(
        active = active.deepCopy(),
        candidate = candidate?.deepCopy(),
        training = training.deepCopy(),
        lastGoodActive = lastGoodActive.deepCopy(),
        optimizer = optimizer.deepCopy()
    )
    private fun AdamWState.deepCopy() = AdamWState(firstMoments.map(FloatArray::copyOf), secondMoments.map(FloatArray::copyOf), step)

    private companion object {
        const val FILE_MAGIC = 0x4A4E594D
        const val SERIALIZATION_VERSION = 1
        const val MAX_BYTES = 512 * 1024
    }
}
