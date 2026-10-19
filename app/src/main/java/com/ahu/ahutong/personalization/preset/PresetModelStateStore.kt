package com.ahu.ahutong.personalization.preset

import android.content.Context
import android.util.AtomicFile
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

data class PresetCheckpoint(val id: String, val checksum: String, val revision: Long, val parameters: TinyMlpParameters)
data class PresetModelState(
    val profileKey: String,
    val active: PresetCheckpoint,
    val candidate: PresetCheckpoint?,
    val training: PresetCheckpoint,
    val lastGood: PresetCheckpoint,
    val optimizer: AdamWState,
    val lastBatchId: String?,
    val updatedAtEpochMs: Long
)

@Singleton
class PresetModelStateStore @Inject constructor(@ApplicationContext context: Context) {
    private val directory by lazy { File(context.noBackupFilesDir, "behavior_models").apply { mkdirs() } }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val random = SecureRandom()

    suspend fun state(profileKey: String): PresetModelState = locks.getOrPut(profileKey) { Mutex() }.withModelIoLock {
        (read(profileKey) ?: create(profileKey).also { write(profileKey, it) }).deepCopy()
    }

    suspend fun commit(
        profileKey: String,
        expectedRevision: Long,
        batchId: String,
        parameters: TinyMlpParameters,
        optimizer: AdamWState
    ): PresetModelState = locks.getOrPut(profileKey) { Mutex() }.withModelIoLock {
        val current = read(profileKey) ?: create(profileKey)
        if (current.lastBatchId == batchId) return@withModelIoLock current.deepCopy()
        check(current.training.revision == expectedRevision)
        validate(parameters, optimizer)
        val training = checkpoint(parameters.deepCopy(optimizer.step), expectedRevision + 1)
        current.copy(
            training = training,
            candidate = if (current.candidate == null && (expectedRevision + 1) % CANDIDATE_INTERVAL == 0L) {
                training.deepCopy()
            } else {
                current.candidate
            },
            optimizer = optimizer.deepCopy(),
            lastBatchId = batchId,
            updatedAtEpochMs = System.currentTimeMillis()
        ).also { write(profileKey, it) }.deepCopy()
    }

    suspend fun activateCandidate(profileKey: String): PresetModelState = locks.getOrPut(profileKey) { Mutex() }.withModelIoLock {
        val current = requireNotNull(read(profileKey))
        val candidate = requireNotNull(current.candidate)
        current.copy(active = candidate.deepCopy(), lastGood = candidate.deepCopy(), candidate = null, updatedAtEpochMs = System.currentTimeMillis())
            .also { write(profileKey, it) }.deepCopy()
    }

    suspend fun discardCandidate(profileKey: String) = locks.getOrPut(profileKey) { Mutex() }.withModelIoLock {
        read(profileKey)?.copy(candidate = null, updatedAtEpochMs = System.currentTimeMillis())?.let { write(profileKey, it) }
    }

    suspend fun reset(profileKey: String) = locks.getOrPut(profileKey) { Mutex() }.withModelIoLock { AtomicFile(file(profileKey)).delete() }

    private fun create(profileKey: String): PresetModelState {
        val parameters = TinyMlpParameters.initialize(INPUT_SIZE, 8, 4, 2, random.nextLong())
        val checkpoint = checkpoint(parameters, 0)
        return PresetModelState(profileKey, checkpoint.deepCopy(), null, checkpoint.deepCopy(), checkpoint.deepCopy(), AdamWState.create(parameters), null, System.currentTimeMillis())
    }

    private fun checkpoint(parameters: TinyMlpParameters, revision: Long) =
        PresetCheckpoint(UUID.randomUUID().toString(), checksum(parameters), revision, parameters.deepCopy())

    private fun validate(parameters: TinyMlpParameters, optimizer: AdamWState?) {
        require(parameters.inputSize == INPUT_SIZE && parameters.hidden1Size == 8 && parameters.hidden2Size == 4 && parameters.outputSize == 2)
        require(parameters.allArrays().all { it.all(Float::isFinite) })
        optimizer?.let { value ->
            require(value.firstMoments.zip(parameters.allArrays()).all { (a, b) -> a.size == b.size })
            require(value.secondMoments.zip(parameters.allArrays()).all { (a, b) -> a.size == b.size })
        }
    }

    private fun write(profileKey: String, state: PresetModelState) {
        val payload = encode(state)
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val atomic = AtomicFile(file(profileKey))
        val fileOutput = atomic.startWrite()
        try {
            DataOutputStream(fileOutput).also { output ->
                output.writeInt(MAGIC)
                output.writeInt(payload.size)
                output.write(payload)
                output.write(digest)
                output.flush()
            }
            atomic.finishWrite(fileOutput)
        } catch (error: Throwable) {
            atomic.failWrite(fileOutput)
            throw error
        }
    }

    private fun read(profileKey: String): PresetModelState? {
        val target = file(profileKey)
        if (!target.exists()) return null
        return runCatching {
            val payload = DataInputStream(AtomicFile(target).openRead()).use { input ->
                require(input.readInt() == MAGIC)
                val length = input.readInt()
                require(length in 1..MAX_BYTES)
                val bytes = ByteArray(length).also(input::readFully)
                val digest = ByteArray(32).also(input::readFully)
                require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(bytes)))
                bytes
            }
            decode(payload).also { state ->
                require(state.profileKey == profileKey)
                listOfNotNull(state.active, state.candidate, state.training, state.lastGood).forEach {
                    require(it.checksum == checksum(it.parameters))
                }
                validate(state.training.parameters, state.optimizer)
            }
        }.getOrElse { target.delete(); null }
    }

    private fun encode(state: PresetModelState): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(state.profileKey)
            writeCheckpoint(output, state.active)
            output.writeBoolean(state.candidate != null)
            state.candidate?.let { writeCheckpoint(output, it) }
            writeCheckpoint(output, state.training)
            writeCheckpoint(output, state.lastGood)
            output.writeLong(state.optimizer.step)
            writeArrays(output, state.optimizer.firstMoments)
            writeArrays(output, state.optimizer.secondMoments)
            output.writeBoolean(state.lastBatchId != null)
            state.lastBatchId?.let(output::writeUTF)
            output.writeLong(state.updatedAtEpochMs)
        }
        bytes.toByteArray()
    }

    private fun decode(payload: ByteArray): PresetModelState = DataInputStream(ByteArrayInputStream(payload)).use { input ->
        val profileKey = input.readUTF()
        val active = readCheckpoint(input)
        val candidate = if (input.readBoolean()) readCheckpoint(input) else null
        val training = readCheckpoint(input)
        val lastGood = readCheckpoint(input)
        val step = input.readLong()
        val first = readArrays(input)
        val second = readArrays(input)
        val lastBatch = if (input.readBoolean()) input.readUTF() else null
        PresetModelState(profileKey, active, candidate, training, lastGood, AdamWState(first, second, step), lastBatch, input.readLong())
    }

    private fun writeCheckpoint(output: DataOutputStream, value: PresetCheckpoint) {
        output.writeUTF(value.id); output.writeUTF(value.checksum); output.writeLong(value.revision); writeParameters(output, value.parameters)
    }
    private fun readCheckpoint(input: DataInputStream) = PresetCheckpoint(input.readUTF(), input.readUTF(), input.readLong(), readParameters(input))
    private fun writeParameters(output: DataOutputStream, value: TinyMlpParameters) {
        output.writeInt(value.inputSize); output.writeInt(value.hidden1Size); output.writeInt(value.hidden2Size); output.writeInt(value.outputSize); output.writeLong(value.trainingSteps); writeArrays(output, value.allArrays())
    }
    private fun readParameters(input: DataInputStream): TinyMlpParameters {
        val inputSize = input.readInt(); val hidden1 = input.readInt(); val hidden2 = input.readInt(); val outputSize = input.readInt(); val steps = input.readLong(); val arrays = readArrays(input)
        require(arrays.size == 6)
        return TinyMlpParameters(inputSize, hidden1, hidden2, outputSize, arrays[0], arrays[1], arrays[2], arrays[3], arrays[4], arrays[5], steps)
    }
    private fun writeArrays(output: DataOutputStream, arrays: List<FloatArray>) { output.writeInt(arrays.size); arrays.forEach { output.writeInt(it.size); it.forEach(output::writeFloat) } }
    private fun readArrays(input: DataInputStream): List<FloatArray> = List(input.readInt().also { require(it in 1..16) }) { FloatArray(input.readInt().also { require(it in 1..100_000) }) { input.readFloat() } }
    private fun checksum(parameters: TinyMlpParameters): String = ByteArrayOutputStream().use { bytes -> DataOutputStream(bytes).use { writeParameters(it, parameters) }; MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) } }
    private fun file(profileKey: String): File { require(profileKey.matches(Regex("[a-f0-9]{32}"))); return File(directory, "$profileKey.preset.model") }
    private fun PresetCheckpoint.deepCopy() = copy(parameters = parameters.deepCopy())
    private fun PresetModelState.deepCopy() = copy(active = active.deepCopy(), candidate = candidate?.deepCopy(), training = training.deepCopy(), lastGood = lastGood.deepCopy(), optimizer = optimizer.deepCopy())
    private fun AdamWState.deepCopy() = AdamWState(firstMoments.map(FloatArray::copyOf), secondMoments.map(FloatArray::copyOf), step)

    companion object { const val INPUT_SIZE = 16; private const val MAGIC = 0x50525354; private const val MAX_BYTES = 128 * 1024; private const val CANDIDATE_INTERVAL = 32L }
}
