package com.ahu.ahutong.data.server

object ApkSegmentDownloadPolicy {
    const val INITIAL_CONCURRENCY = 8
    const val MAX_CONCURRENCY = 12
    const val MIN_CHUNK_BYTES = 256 * 1024L
    const val TAIL_RESCUE_MIN_CHUNK_BYTES = 128 * 1024L
    const val LATE_TAIL_RESCUE_MIN_CHUNK_BYTES = 64 * 1024L

    enum class Status {
        PENDING,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    data class DownloadRange(
        val id: String,
        val start: Long,
        val end: Long,
        val downloaded: Long = 0L,
        val status: Status = Status.PENDING,
        val generation: Int = 0,
        val lastSpeedBytesPerSec: Long = 0L,
        val updatedAt: Long = 0L
    ) {
        val length: Long
            get() = end - start + 1L

        val completedBytes: Long
            get() = downloaded.coerceIn(0L, length)

        val nextStart: Long
            get() = start + completedBytes

        val remaining: Long
            get() = (length - completedBytes).coerceAtLeast(0L)

        val isComplete: Boolean
            get() = completedBytes >= length
    }

    data class Metadata(
        val versionCode: Int,
        val url: String,
        val sha256: String,
        val totalBytes: Long,
        val etag: String?,
        val lastModified: String?,
        val minChunkBytes: Long = MIN_CHUNK_BYTES,
        val maxConcurrency: Int = MAX_CONCURRENCY,
        val ranges: List<DownloadRange>
    )

    fun createInitialRanges(
        totalBytes: Long,
        initialConcurrency: Int = INITIAL_CONCURRENCY,
        now: Long = 0L
    ): List<DownloadRange> {
        if (totalBytes <= 0L || initialConcurrency <= 0) return emptyList()

        val count = minOf(initialConcurrency.toLong(), totalBytes).toInt()
        val baseSize = totalBytes / count
        val remainder = totalBytes % count
        var nextStart = 0L

        return (0 until count).map { index ->
            val size = baseSize + if (index < remainder) 1L else 0L
            val start = nextStart
            val end = start + size - 1L
            nextStart = end + 1L
            DownloadRange(
                id = index.toString(),
                start = start,
                end = end,
                updatedAt = now
            )
        }
    }

    fun normalizeForResume(
        ranges: List<DownloadRange>,
        now: Long = 0L
    ): List<DownloadRange> {
        return ranges.sortedBy { it.start }.map { range ->
            val downloaded = range.completedBytes
            val status = if (downloaded >= range.length) {
                Status.COMPLETED
            } else {
                Status.PENDING
            }
            range.copy(
                downloaded = downloaded,
                status = status,
                lastSpeedBytesPerSec = 0L,
                updatedAt = now
            )
        }
    }

    fun isResumeCompatible(
        metadata: Metadata,
        versionCode: Int,
        url: String,
        sha256: String,
        totalBytes: Long,
        etag: String?,
        lastModified: String?
    ): Boolean {
        return metadata.versionCode == versionCode &&
            metadata.url == url &&
            metadata.sha256.equals(sha256, ignoreCase = true) &&
            metadata.totalBytes == totalBytes &&
            metadata.etag == etag &&
            metadata.lastModified == lastModified &&
            rangesCoverFile(metadata.ranges, totalBytes)
    }

    fun rangesCoverFile(ranges: List<DownloadRange>, totalBytes: Long): Boolean {
        if (totalBytes <= 0L || ranges.isEmpty()) return false

        var expectedStart = 0L
        ranges.sortedBy { it.start }.forEach { range ->
            if (range.start != expectedStart) return false
            if (range.end < range.start) return false
            if (range.downloaded < 0L || range.downloaded > range.length) return false
            if (range.status == Status.COMPLETED && range.downloaded != range.length) return false
            expectedStart = range.end + 1L
        }
        return expectedStart == totalBytes
    }

    fun pendingRanges(ranges: List<DownloadRange>): List<DownloadRange> {
        return ranges.filterNot { it.isComplete }
    }

    fun progress(ranges: List<DownloadRange>, totalBytes: Long): Float? {
        if (totalBytes <= 0L) return null
        val completed = ranges.sumOf { it.completedBytes }
        return (completed.toDouble() / totalBytes.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    fun canSplit(
        range: DownloadRange,
        minChunkBytes: Long = MIN_CHUNK_BYTES
    ): Boolean {
        return !range.isComplete && range.remaining > minChunkBytes * 2L
    }

    fun splitRemainingRange(
        range: DownloadRange,
        firstPendingId: String,
        secondPendingId: String,
        now: Long = 0L
    ): List<DownloadRange> {
        if (!canSplit(range)) return listOf(range)

        val nextStart = range.nextStart
        val mid = nextStart + range.remaining / 2L - 1L
        val generation = range.generation + 1
        val result = mutableListOf<DownloadRange>()

        if (range.completedBytes > 0L) {
            result += range.copy(
                end = nextStart - 1L,
                downloaded = range.completedBytes,
                status = Status.COMPLETED,
                lastSpeedBytesPerSec = 0L,
                updatedAt = now
            )
        }

        result += DownloadRange(
            id = firstPendingId,
            start = nextStart,
            end = mid,
            status = Status.PENDING,
            generation = generation,
            updatedAt = now
        )
        result += DownloadRange(
            id = secondPendingId,
            start = mid + 1L,
            end = range.end,
            status = Status.PENDING,
            generation = generation,
            updatedAt = now
        )

        return result
    }

    fun canStealTail(
        range: DownloadRange,
        minChunkBytes: Long = TAIL_RESCUE_MIN_CHUNK_BYTES
    ): Boolean {
        return range.status == Status.RUNNING &&
            !range.isComplete &&
            range.remaining >= minChunkBytes * 2L
    }

    fun stealTailRange(
        range: DownloadRange,
        helperId: String,
        minChunkBytes: Long = TAIL_RESCUE_MIN_CHUNK_BYTES,
        now: Long = 0L
    ): Pair<DownloadRange, DownloadRange>? {
        if (!canStealTail(range, minChunkBytes)) return null

        val remaining = range.remaining
        val tailLength = maxOf(minChunkBytes, remaining / 2L)
            .coerceAtMost(remaining - minChunkBytes)
        val tailStart = range.end - tailLength + 1L
        if (tailStart <= range.nextStart) return null

        val parent = range.copy(
            end = tailStart - 1L,
            downloaded = range.completedBytes,
            updatedAt = now
        )
        val helper = DownloadRange(
            id = helperId,
            start = tailStart,
            end = range.end,
            status = Status.PENDING,
            generation = range.generation + 1,
            updatedAt = now
        )
        return parent to helper
    }

    fun rollbackSplitChildren(
        childRanges: List<DownloadRange>,
        nextPendingId: () -> String,
        now: Long = 0L
    ): List<DownloadRange> {
        if (childRanges.isEmpty()) return emptyList()

        val pendingIntervals = mutableListOf<Pair<Long, Long>>()
        val result = mutableListOf<DownloadRange>()
        val generation = childRanges.maxOf { it.generation } + 1

        childRanges.sortedBy { it.start }.forEach { range ->
            val completedBytes = range.completedBytes
            if (completedBytes > 0L) {
                val completedEnd = range.start + completedBytes - 1L
                result += range.copy(
                    end = completedEnd,
                    downloaded = completedBytes,
                    status = Status.COMPLETED,
                    lastSpeedBytesPerSec = 0L,
                    updatedAt = now
                )
            }
            if (completedBytes < range.length) {
                pendingIntervals += range.start + completedBytes to range.end
            }
        }

        mergeIntervals(pendingIntervals).forEach { (start, end) ->
            result += DownloadRange(
                id = nextPendingId(),
                start = start,
                end = end,
                status = Status.PENDING,
                generation = generation,
                updatedAt = now
            )
        }

        return result.sortedBy { it.start }
    }

    fun compactRanges(
        ranges: List<DownloadRange>,
        now: Long = 0L,
        canMergeCompleted: (DownloadRange, DownloadRange) -> Boolean = { _, _ -> true },
        canMergePending: (DownloadRange, DownloadRange) -> Boolean = { left, right ->
            left.generation == right.generation
        }
    ): List<DownloadRange> {
        if (ranges.size <= 1) return ranges

        val result = mutableListOf<DownloadRange>()
        ranges.sortedBy { it.start }.forEach { range ->
            val previous = result.lastOrNull()
            if (previous != null && canMergeAdjacent(previous, range, canMergeCompleted, canMergePending)) {
                result[result.lastIndex] = mergeAdjacent(previous, range, now)
            } else {
                result += range
            }
        }

        return result
    }

    fun totalDownloaded(ranges: List<DownloadRange>): Long {
        return ranges.sumOf { it.completedBytes }
    }

    private fun canMergeAdjacent(
        left: DownloadRange,
        right: DownloadRange,
        canMergeCompleted: (DownloadRange, DownloadRange) -> Boolean,
        canMergePending: (DownloadRange, DownloadRange) -> Boolean
    ): Boolean {
        if (left.end + 1L != right.start) return false
        if (left.status == Status.COMPLETED && right.status == Status.COMPLETED) {
            return left.isComplete && right.isComplete && canMergeCompleted(left, right)
        }
        return left.status == Status.PENDING &&
            right.status == Status.PENDING &&
            left.downloaded == 0L &&
            right.downloaded == 0L &&
            canMergePending(left, right)
    }

    private fun mergeAdjacent(
        left: DownloadRange,
        right: DownloadRange,
        now: Long
    ): DownloadRange {
        val length = right.end - left.start + 1L
        return left.copy(
            end = right.end,
            downloaded = if (left.status == Status.COMPLETED) length else 0L,
            status = left.status,
            generation = maxOf(left.generation, right.generation),
            lastSpeedBytesPerSec = 0L,
            updatedAt = maxOf(now, left.updatedAt, right.updatedAt)
        )
    }

    private fun mergeIntervals(intervals: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        if (intervals.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<Long, Long>>()
        var currentStart = intervals.minOf { it.first }
        var currentEnd = intervals.filter { it.first == currentStart }.maxOf { it.second }

        intervals.sortedWith(compareBy<Pair<Long, Long>> { it.first }.thenBy { it.second })
            .forEach { (start, end) ->
                if (start <= currentEnd + 1L) {
                    currentEnd = maxOf(currentEnd, end)
                } else {
                    result += currentStart to currentEnd
                    currentStart = start
                    currentEnd = end
                }
            }

        result += currentStart to currentEnd
        return result
    }
}
