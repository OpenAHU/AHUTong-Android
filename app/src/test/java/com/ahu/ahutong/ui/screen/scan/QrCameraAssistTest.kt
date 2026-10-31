package com.ahu.ahutong.ui.screen.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrCameraAssistTest {
    private val small = QrPotentialTarget(QrPoint(0.5f, 0.5f), 0.12f)
    private val zoom = ScannerZoomState(1f, 1f, 8f)

    @Test
    fun smallTargetMustBeStableThenZoomsGraduallyWithinDeviceAndAutomaticLimits() {
        val assist = QrCameraAssist()
        assertNull(assist.update(0, zoom, small).zoomRatio)
        val first = assist.update(50_000_000L, zoom, small)
        assertEquals(1.5f, first.zoomRatio)
        assertEquals(small.center, first.meteringPoint)
        assertNull(assist.update(100_000_000L, zoom.copy(ratio = 1.5f), small).zoomRatio)
        var ratio = first.zoomRatio!!
        repeat(8) { step ->
            val adjustment = assist.update(800_000_000L * (step + 1), zoom.copy(ratio = ratio), small)
            adjustment.zoomRatio?.let { requested ->
                assertTrue(requested <= ratio * 1.5f + 0.001f)
                assertTrue(requested <= 4f)
                ratio = requested
            }
        }
        assertEquals(4f, ratio)

        val limited = QrCameraAssist()
        limited.update(0, zoom.copy(maxRatio = 1.25f), small)
        assertEquals(1.25f, limited.update(50_000_000L, zoom.copy(maxRatio = 1.25f), small).zoomRatio)
    }

    @Test
    fun largeOrEdgeTargetsAreMeteredWithoutBeingCroppedByAutomaticZoom() {
        for (target in listOf(small.copy(size = 0.5f), small.copy(center = QrPoint(0.88f, 0.5f)))) {
            val assist = QrCameraAssist()
            assist.update(0, zoom, target)
            val adjustment = assist.update(50_000_000L, zoom, target)
            assertNull(adjustment.zoomRatio)
            assertEquals(target.center, adjustment.meteringPoint)
        }
    }

    @Test
    fun movingOrInvalidCandidatesNeverDriveTheZoomMotor() {
        val assist = QrCameraAssist()
        for (index in 0..10) {
            val moving = small.copy(center = QrPoint(if (index % 2 == 0) 0.3f else 0.7f, 0.5f))
            assertNull(assist.update(index * 50_000_000L, zoom, moving).zoomRatio)
        }
        for (invalid in listOf(small.copy(size = Float.NaN), small.copy(size = -1f),
            small.copy(center = QrPoint(Float.NaN, 0.5f)))) {
            assertEquals(QrCameraAdjustment(), assist.update(1_000_000_000L, zoom, invalid))
        }
        assertEquals(QrCameraAdjustment(), assist.update(1_000_000_000L, zoom.copy(maxRatio = Float.NaN), small))
    }

    @Test
    fun zoomLeavesAllSymbolEdgesAndQuietZoneInsideLandscapeAndPortraitFrames() {
        for (aspect in listOf(0.75f, 1f, 4f / 3)) {
            for (center in listOf(0.25f, 0.5f, 0.75f)) {
                val target = small.copy(center = QrPoint(center, 0.5f), size = 0.22f)
                val assist = QrCameraAssist()
                assist.update(0, zoom, target, aspect)
                val ratio = assist.update(50_000_000L, zoom, target, aspect).zoomRatio ?: continue
                val halfWidth = target.size * minOf(1f, 1f / aspect) * 0.65f
                val halfHeight = target.size * minOf(1f, aspect) * 0.65f
                assertTrue((kotlin.math.abs(center - 0.5f) + halfWidth) * ratio <= 0.461f)
                assertTrue(halfHeight * ratio <= 0.461f)
            }
        }
    }

    @Test
    fun anUnappliedRequestCannotAccumulateFurtherZoom() {
        val assist = QrCameraAssist()
        assist.update(0, zoom, small)
        assertEquals(1.5f, assist.update(50_000_000L, zoom, small).zoomRatio)
        assertNull(assist.update(1_000_000_000L, zoom, small).zoomRatio)
    }

    @Test
    fun aLostTargetRestoresOnlyAutomaticallyAddedZoom() {
        val assist = QrCameraAssist()
        assist.update(0, zoom, small)
        assist.update(50_000_000L, zoom, small)
        val enlarged = zoom.copy(ratio = 1.5f)
        assertNull(assist.update(2_000_000_000L, enlarged, null).zoomRatio)
        val restored = assist.update(3_000_000_000L, enlarged, null).zoomRatio
        assertNotNull(restored)
        assertTrue(restored in 1f..1.5f)
        assertEquals(1f, assist.update(4_000_000_000L, zoom.copy(ratio = restored), null).zoomRatio)
    }

    @Test
    fun manualInteractionPausesAssistanceAndOwnsTheNewBaseline() {
        val assist = QrCameraAssist()
        assist.update(0, zoom, small)
        assist.update(50_000_000L, zoom, small)
        assist.onManualInteraction(100_000_000L, 2f)
        assertEquals(QrCameraAdjustment(), assist.update(3_000_000_000L, zoom.copy(ratio = 2f), small))
        assertNull(assist.update(4_000_000_000L, zoom.copy(ratio = 2f), null).zoomRatio)
        assist.update(4_100_000_000L, zoom.copy(ratio = 2f), small)
        assertEquals(3f, assist.update(4_200_000_000L, zoom.copy(ratio = 2f), small).zoomRatio)
        val restored = assist.update(7_000_000_000L, zoom.copy(ratio = 3f), null).zoomRatio!!
        assertTrue(restored >= 2f)
    }

    @Test
    fun successfullyDecodedFramesNeverTriggerZoomOrMetering() {
        val assist = QrCameraAssist()
        assist.update(0, zoom, small)
        assertEquals(QrCameraAdjustment(), assist.update(50_000_000L, zoom, small, decoded = true))
    }

    @Test
    fun absentFinderPatternsRetryCentreMeteringWithoutChangingZoom() {
        val assist = QrCameraAssist()
        assertEquals(QrCameraAdjustment(meteringPoint = QrPoint(0.5f, 0.5f)),
            assist.update(0, zoom, null))
        assertEquals(QrCameraAdjustment(), assist.update(1_000_000_000L, zoom, null))
        assertEquals(QrCameraAdjustment(meteringPoint = QrPoint(0.5f, 0.5f)),
            assist.update(1_500_000_000L, zoom, null))
        assist.onManualInteraction(2_000_000_000L, 1f)
        assertEquals(QrCameraAdjustment(), assist.update(3_000_000_000L, zoom, null))
    }

    @Test
    fun closingRestoresAutomaticZoomIncludingUnappliedAndSuccessiveRequests() {
        val assist = QrCameraAssist()
        assertNull(assist.restorationRatio(zoom))
        assist.update(0, zoom, small)
        assist.update(50_000_000L, zoom, small)
        // Sending the baseline again also cancels the pending 1.5x CameraX request.
        assertEquals(1f, assist.restorationRatio(zoom))
        assertEquals(1f, assist.restorationRatio(zoom.copy(ratio = 1.5f)))
        assertEquals(2.25f, assist.update(800_000_000L, zoom.copy(ratio = 1.5f), small).zoomRatio)
        assertEquals(1f, assist.restorationRatio(zoom.copy(ratio = 1.5f)))
        assertEquals(1f, assist.restorationRatio(zoom.copy(ratio = 2.25f)))
        assertNull(assist.restorationRatio(zoom.copy(ratio = 3f)))
        assertNull(assist.restorationRatio(zoom.copy(ratio = Float.NaN)))
    }

    @Test
    fun closingPreservesTheUsersZoomAfterManualInteraction() {
        val assist = QrCameraAssist()
        assist.update(0, zoom, small)
        assist.update(50_000_000L, zoom, small)
        assist.onManualInteraction(100_000_000L, 2f)
        assertNull(assist.restorationRatio(zoom.copy(ratio = 2f)))
        assertNull(assist.restorationRatio(zoom.copy(ratio = 1.5f)))
        val manual = zoom.copy(ratio = 2f)
        assist.update(4_000_000_000L, manual, small)
        assist.update(4_100_000_000L, manual, small)
        assertEquals(2f, assist.restorationRatio(manual.copy(ratio = 3f)))
    }

    @Test
    fun extremeLightingNeedsStableEvidenceAndUsesBoundedAbsoluteExposure() {
        val assist = QrExposureAssist()
        val dark = QrLuminanceStats(18f, 0.8f, 0f)
        assertNull(assist.update(0, dark, 0, -3, 3, 1f / 3))
        assertEquals(2, assist.update(50_000_000L, dark, 0, -3, 3, 1f / 3))
        assertNull(assist.update(200_000_000L, dark, 2, -3, 3, 1f / 3))
        assertNull(assist.update(2_000_000_000L, dark, 2, -3, 3, 1f / 3))
        val bright = QrLuminanceStats(248f, 0f, 0.8f)
        assertNull(assist.update(3_000_000_000L, bright, 2, -1, 3, 1f / 3))
        assertEquals(-1, assist.update(3_100_000_000L, bright, 2, -1, 3, 1f / 3))
    }

    @Test
    fun recoveredLightingRestoresThePreviousExposureAndHysteresisDoesNotOscillate() {
        val assist = QrExposureAssist()
        val dark = QrLuminanceStats(18f, 0.8f, 0f)
        assist.update(0, dark, 1, -3, 3, 1f / 3)
        assertEquals(3, assist.update(50_000_000L, dark, 1, -3, 3, 1f / 3))
        assertNull(assist.update(2_000_000_000L, QrLuminanceStats(60f, 0.1f, 0f), 3, -3, 3, 1f / 3))
        val normal = QrLuminanceStats(120f, 0.01f, 0.01f)
        assertNull(assist.update(2_100_000_000L, normal, 3, -3, 3, 1f / 3))
        assertEquals(1, assist.update(2_200_000_000L, normal, 3, -3, 3, 1f / 3))
    }

    @Test
    fun unsupportedOrInvalidExposureAndManualInteractionProduceNoRequest() {
        val assist = QrExposureAssist()
        val dark = QrLuminanceStats(18f, 0.8f, 0f)
        repeat(3) {
            assertNull(assist.update(it * 50_000_000L, dark, 0, 0, 0, 0f))
            assertNull(assist.update(it * 50_000_000L, dark.copy(centerMean = Float.NaN), 0, -3, 3, 1f / 3))
        }
        assist.onManualInteraction(100_000_000L, 1)
        assertNull(assist.update(3_000_000_000L, dark, 1, -3, 3, 1f / 3))
    }
}
