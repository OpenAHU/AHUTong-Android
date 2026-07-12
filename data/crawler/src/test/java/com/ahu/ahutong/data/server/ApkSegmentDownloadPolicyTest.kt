package com.ahu.ahutong.data.server

import com.ahu.ahutong.data.server.ApkSegmentDownloadPolicy.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApkSegmentDownloadPolicyTest {

    @Test
    fun createInitialRangesSplitsTotalBytesWithoutGaps() {
        val ranges = ApkSegmentDownloadPolicy.createInitialRanges(totalBytes = 100L)

        assertEquals(8, ranges.size)
        assertEquals(0L, ranges.first().start)
        assertEquals(99L, ranges.last().end)
        assertEquals(100L, ranges.sumOf { it.length })

        ranges.zipWithNext().forEach { (left, right) ->
            assertEquals(left.end + 1L, right.start)
        }
        assertTrue(ApkSegmentDownloadPolicy.rangesCoverFile(ranges, totalBytes = 100L))
    }

    @Test
    fun createInitialRangesUsesFileSizeWhenFileIsSmallerThanConcurrency() {
        val ranges = ApkSegmentDownloadPolicy.createInitialRanges(totalBytes = 3L)

        assertEquals(3, ranges.size)
        assertEquals(listOf(0L, 1L, 2L), ranges.map { it.start })
        assertEquals(listOf(0L, 1L, 2L), ranges.map { it.end })
    }

    @Test
    fun normalizeForResumeOnlyLeavesIncompleteRangesPending() {
        val ranges = listOf(
            ApkSegmentDownloadPolicy.DownloadRange(
                id = "0",
                start = 0L,
                end = 9L,
                downloaded = 10L,
                status = Status.RUNNING
            ),
            ApkSegmentDownloadPolicy.DownloadRange(
                id = "1",
                start = 10L,
                end = 19L,
                downloaded = 4L,
                status = Status.RUNNING
            )
        )

        val resumed = ApkSegmentDownloadPolicy.normalizeForResume(ranges)

        assertEquals(Status.COMPLETED, resumed[0].status)
        assertEquals(Status.PENDING, resumed[1].status)
        assertEquals(14L, resumed[1].nextStart)
        assertEquals(listOf("1"), ApkSegmentDownloadPolicy.pendingRanges(resumed).map { it.id })
    }

    @Test
    fun metadataCompatibilityRejectsAnyChangedIdentityField() {
        val metadata = metadata()

        assertTrue(
            ApkSegmentDownloadPolicy.isResumeCompatible(
                metadata = metadata,
                versionCode = 2,
                url = "https://openahu.org/download/app.apk",
                sha256 = SHA256,
                totalBytes = 100L,
                etag = "\"abc\"",
                lastModified = "Wed, 08 Jul 2026 15:02:52 GMT"
            )
        )

        assertFalse(metadata.isCompatible(versionCode = 3))
        assertFalse(metadata.isCompatible(url = "https://openahu.org/download/other.apk"))
        assertFalse(metadata.isCompatible(sha256 = SHA256.replace('a', 'b')))
        assertFalse(metadata.isCompatible(totalBytes = 101L))
        assertFalse(metadata.isCompatible(etag = "\"def\""))
        assertFalse(metadata.isCompatible(lastModified = "Thu, 09 Jul 2026 15:02:52 GMT"))
    }

    @Test
    fun splitRemainingRangeOnlySplitsUnwrittenBytes() {
        val range = ApkSegmentDownloadPolicy.DownloadRange(
            id = "7",
            start = 0L,
            end = ApkSegmentDownloadPolicy.MIN_CHUNK_BYTES * 4L - 1L,
            downloaded = ApkSegmentDownloadPolicy.MIN_CHUNK_BYTES,
            status = Status.RUNNING
        )

        val split = ApkSegmentDownloadPolicy.splitRemainingRange(
            range = range,
            firstPendingId = "8",
            secondPendingId = "9"
        )

        assertEquals(3, split.size)
        assertEquals(Status.COMPLETED, split[0].status)
        assertEquals(0L, split[0].start)
        assertEquals(range.downloaded - 1L, split[0].end)
        assertEquals(range.downloaded, split[1].start)
        assertEquals(split[1].end + 1L, split[2].start)
        assertEquals(range.end, split[2].end)
        assertTrue(ApkSegmentDownloadPolicy.rangesCoverFile(split, range.length))
    }

    @Test
    fun stealTailRangeShrinksRunningRangeAndCreatesPendingTail() {
        val range = ApkSegmentDownloadPolicy.DownloadRange(
            id = "7",
            start = 0L,
            end = ApkSegmentDownloadPolicy.TAIL_RESCUE_MIN_CHUNK_BYTES * 4L - 1L,
            downloaded = ApkSegmentDownloadPolicy.TAIL_RESCUE_MIN_CHUNK_BYTES,
            status = Status.RUNNING
        )

        val (parent, helper) = ApkSegmentDownloadPolicy.stealTailRange(
            range = range,
            helperId = "8"
        ) ?: error("expected tail rescue split")

        assertEquals(Status.RUNNING, parent.status)
        assertEquals(Status.PENDING, helper.status)
        assertEquals(range.downloaded, parent.downloaded)
        assertEquals(parent.end + 1L, helper.start)
        assertEquals(range.end, helper.end)
        assertTrue(parent.nextStart <= parent.end)
        assertTrue(helper.length >= ApkSegmentDownloadPolicy.TAIL_RESCUE_MIN_CHUNK_BYTES)
    }

    @Test
    fun stealTailRangeRejectsSmallRemainingRange() {
        val range = ApkSegmentDownloadPolicy.DownloadRange(
            id = "7",
            start = 0L,
            end = ApkSegmentDownloadPolicy.TAIL_RESCUE_MIN_CHUNK_BYTES * 2L - 1L,
            downloaded = 1L,
            status = Status.RUNNING
        )

        assertFalse(ApkSegmentDownloadPolicy.canStealTail(range))
        assertEquals(null, ApkSegmentDownloadPolicy.stealTailRange(range, helperId = "8"))
    }

    @Test
    fun rollbackSplitChildrenKeepsWrittenBytesAndMergesUnwrittenBytes() {
        val pendingIds = mutableListOf("20").iterator()
        val rolledBack = ApkSegmentDownloadPolicy.rollbackSplitChildren(
            childRanges = listOf(
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "8",
                    start = 1000L,
                    end = 5000L,
                    downloaded = 1000L,
                    status = Status.RUNNING
                ),
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "9",
                    start = 5001L,
                    end = 9000L,
                    downloaded = 0L,
                    status = Status.RUNNING
                )
            ),
            nextPendingId = { pendingIds.next() }
        )

        assertEquals(2, rolledBack.size)
        assertEquals(Status.COMPLETED, rolledBack[0].status)
        assertEquals(1000L, rolledBack[0].start)
        assertEquals(1999L, rolledBack[0].end)
        assertEquals(1000L, rolledBack[0].downloaded)
        assertEquals(Status.PENDING, rolledBack[1].status)
        assertEquals(2000L, rolledBack[1].start)
        assertEquals(9000L, rolledBack[1].end)
        assertEquals(0L, rolledBack[1].downloaded)
    }

    @Test
    fun compactRangesMergesAdjacentCompletedRanges() {
        val compacted = ApkSegmentDownloadPolicy.compactRanges(
            listOf(
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "1",
                    start = 0L,
                    end = 9L,
                    downloaded = 10L,
                    status = Status.COMPLETED
                ),
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "2",
                    start = 10L,
                    end = 19L,
                    downloaded = 10L,
                    status = Status.COMPLETED
                )
            )
        )

        assertEquals(1, compacted.size)
        assertEquals(0L, compacted[0].start)
        assertEquals(19L, compacted[0].end)
        assertEquals(20L, compacted[0].downloaded)
        assertEquals(Status.COMPLETED, compacted[0].status)
    }

    @Test
    fun compactRangesKeepsAdjacentCompletedRangesWhenRejected() {
        val compacted = ApkSegmentDownloadPolicy.compactRanges(
            ranges = listOf(
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "1",
                    start = 0L,
                    end = 9L,
                    downloaded = 10L,
                    status = Status.COMPLETED
                ),
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "2",
                    start = 10L,
                    end = 19L,
                    downloaded = 10L,
                    status = Status.COMPLETED
                )
            ),
            canMergeCompleted = { _, _ -> false }
        )

        assertEquals(2, compacted.size)
        assertEquals(listOf("1", "2"), compacted.map { it.id })
    }

    @Test
    fun compactRangesMergesAdjacentUnwrittenPendingRangesWhenAllowed() {
        val compacted = ApkSegmentDownloadPolicy.compactRanges(
            listOf(
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "1",
                    start = 0L,
                    end = 9L,
                    status = Status.PENDING,
                    generation = 3
                ),
                ApkSegmentDownloadPolicy.DownloadRange(
                    id = "2",
                    start = 10L,
                    end = 19L,
                    status = Status.PENDING,
                    generation = 3
                )
            )
        )

        assertEquals(1, compacted.size)
        assertEquals(0L, compacted[0].start)
        assertEquals(19L, compacted[0].end)
        assertEquals(0L, compacted[0].downloaded)
        assertEquals(Status.PENDING, compacted[0].status)
    }

    @Test
    fun completedRangesReportFullProgress() {
        val ranges = ApkSegmentDownloadPolicy.createInitialRanges(totalBytes = 100L)
            .map { it.copy(downloaded = it.length, status = Status.COMPLETED) }

        assertEquals(1f, ApkSegmentDownloadPolicy.progress(ranges, totalBytes = 100L))
        assertTrue(ApkSegmentDownloadPolicy.pendingRanges(ranges).isEmpty())
    }

    private fun metadata() = ApkSegmentDownloadPolicy.Metadata(
        versionCode = 2,
        url = "https://openahu.org/download/app.apk",
        sha256 = SHA256,
        totalBytes = 100L,
        etag = "\"abc\"",
        lastModified = "Wed, 08 Jul 2026 15:02:52 GMT",
        ranges = ApkSegmentDownloadPolicy.createInitialRanges(totalBytes = 100L)
    )

    private fun ApkSegmentDownloadPolicy.Metadata.isCompatible(
        versionCode: Int = this.versionCode,
        url: String = this.url,
        sha256: String = this.sha256,
        totalBytes: Long = this.totalBytes,
        etag: String? = this.etag,
        lastModified: String? = this.lastModified
    ): Boolean {
        return ApkSegmentDownloadPolicy.isResumeCompatible(
            metadata = this,
            versionCode = versionCode,
            url = url,
            sha256 = sha256,
            totalBytes = totalBytes,
            etag = etag,
            lastModified = lastModified
        )
    }

    private companion object {
        const val SHA256 = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    }
}
