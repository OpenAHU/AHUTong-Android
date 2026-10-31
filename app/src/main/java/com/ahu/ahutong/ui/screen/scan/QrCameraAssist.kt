package com.ahu.ahutong.ui.screen.scan

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A geometrically verified but undecoded QR; size is relative to the image's short side. */
internal data class QrPotentialTarget(val center: QrPoint, val size: Float)

internal data class QrCameraAdjustment(
    val zoomRatio: Float? = null,
    val meteringPoint: QrPoint? = null
)

/** Main-thread policy. All times are monotonic nanoseconds; requests never exceed camera bounds. */
internal class QrCameraAssist {
    private var previous: QrPotentialTarget? = null
    private var stableFrames = 0
    private var lastSeen: Long? = null
    private var lastZoom: Long? = null
    private var lastMetering: Long? = null
    private var manualUntil = Long.MIN_VALUE
    private var baseline: Float? = null
    private var requested: Float? = null
    private var requestOrigin: Float? = null

    fun onManualInteraction(nowNanos: Long, currentZoomRatio: Float) {
        manualUntil = nowNanos + 3_000_000_000L
        previous = null
        stableFrames = 0
        // Manual zoom owns the new field of view. Never restore the old automatic baseline.
        baseline = currentZoomRatio.takeIf { it.isFinite() && it > 0f }
        requested = null
        requestOrigin = null
        lastSeen = null
    }

    /** Restore only zoom owned by this session, including a request CameraX has not applied yet. */
    fun restorationRatio(zoom: ScannerZoomState): Float? {
        val original = baseline ?: return null
        val pending = requested ?: return null
        if (!zoom.ratio.isFinite() || !zoom.minRatio.isFinite() || !zoom.maxRatio.isFinite() ||
            zoom.minRatio <= 0f || zoom.maxRatio < zoom.minRatio ||
            zoom.ratio !in zoom.minRatio..zoom.maxRatio
        ) return null
        val owned = abs(zoom.ratio - pending) <= 0.08f ||
            abs(zoom.ratio - original) <= 0.08f ||
            requestOrigin?.let { abs(zoom.ratio - it) <= 0.08f } == true
        return original.coerceIn(zoom.minRatio, zoom.maxRatio).takeIf { owned }
    }

    fun update(
        nowNanos: Long,
        zoom: ScannerZoomState,
        candidate: QrPotentialTarget?,
        frameAspectRatio: Float = 1f,
        decoded: Boolean = false
    ): QrCameraAdjustment {
        if (decoded) {
            previous = null
            stableFrames = 0
            lastSeen = nowNanos
            return QrCameraAdjustment()
        }
        if (!zoom.ratio.isFinite() || !zoom.minRatio.isFinite() || !zoom.maxRatio.isFinite() ||
            zoom.minRatio <= 0f || zoom.maxRatio < zoom.minRatio ||
            zoom.ratio !in zoom.minRatio..zoom.maxRatio ||
            !frameAspectRatio.isFinite() || frameAspectRatio <= 0f || nowNanos < manualUntil
        ) return QrCameraAdjustment()

        val target = candidate?.takeIf {
            it.size.isFinite() && it.size > 0f && it.size <= 1f &&
                it.center.x.isFinite() && it.center.y.isFinite() &&
                it.center.x in 0f..1f && it.center.y in 0f..1f
        }
        if (candidate != null && target == null) return QrCameraAdjustment()
        if (target == null) {
            previous = null
            stableFrames = 0
            // Defocus can hide even the finder patterns. Periodically re-meter the aim area.
            val metering = if (elapsed(lastMetering, nowNanos, 1_500_000_000L)) {
                lastMetering = nowNanos
                QrPoint(0.5f, 0.5f)
            } else null
            val start = baseline
            val autoRatio = requested
            if (start != null && autoRatio != null &&
                lastSeen?.let { nowNanos - it >= 2_500_000_000L } == true &&
                elapsed(lastZoom, nowNanos, 700_000_000L) &&
                abs(zoom.ratio - autoRatio) <= 0.08f && zoom.ratio > start + 0.03f
            ) {
                val restored = maxOf(start, zoom.ratio / 1.35f).coerceIn(zoom.minRatio, zoom.maxRatio)
                requestOrigin = zoom.ratio
                requested = restored
                lastZoom = nowNanos
                return QrCameraAdjustment(zoomRatio = restored, meteringPoint = metering)
            }
            return QrCameraAdjustment(meteringPoint = metering)
        }

        lastSeen = nowNanos
        val oldTarget = previous
        stableFrames = if (oldTarget != null &&
            hypot(oldTarget.center.x - target.center.x, oldTarget.center.y - target.center.y) < 0.08f &&
            target.size / oldTarget.size in 0.6f..1.7f
        ) stableFrames + 1 else 1
        previous = target
        if (stableFrames < 2) return QrCameraAdjustment()

        val metering = if (elapsed(lastMetering, nowNanos, 1_200_000_000L)) {
            lastMetering = nowNanos
            target.center
        } else null
        val maxAutoRatio = minOf(4f, zoom.maxRatio)
        if (target.size >= 0.32f || zoom.ratio >= maxAutoRatio ||
            !elapsed(lastZoom, nowNanos, 700_000_000L) ||
            target.center.x !in 0.25f..0.75f || target.center.y !in 0.25f..0.75f
        ) return QrCameraAdjustment(meteringPoint = metering)

        // Optical zoom crops about the sensor centre. Leave room for all symbol edges and a quiet zone.
        val halfWidth = target.size * minOf(1f, 1f / frameAspectRatio) * 0.65f
        val halfHeight = target.size * minOf(1f, frameAspectRatio) * 0.65f
        val safeFactor = minOf(
            0.46f / (abs(target.center.x - 0.5f) + halfWidth),
            0.46f / (abs(target.center.y - 0.5f) + halfHeight)
        )
        val factor = minOf(1.5f, 0.32f / target.size, safeFactor, maxAutoRatio / zoom.ratio)
        if (factor < 1.08f) return QrCameraAdjustment(meteringPoint = metering)

        // Wait for CameraX to apply a previous request before accumulating another one.
        if (requested?.let { abs(it - zoom.ratio) > 0.08f } == true) {
            if (elapsed(lastZoom, nowNanos, 1_500_000_000L)) {
                requested = null
                requestOrigin = null
                baseline = zoom.ratio
                stableFrames = 0
            }
            return QrCameraAdjustment(meteringPoint = metering)
        }
        if (requested == null) baseline = zoom.ratio
        val ratio = (zoom.ratio * factor).coerceIn(zoom.minRatio, zoom.maxRatio)
        requestOrigin = zoom.ratio
        requested = ratio
        lastZoom = nowNanos
        return QrCameraAdjustment(ratio, metering)
    }

    private fun elapsed(previousNanos: Long?, nowNanos: Long, interval: Long): Boolean =
        previousNanos == null || nowNanos - previousNanos >= interval
}

/** Statistics of the central half of the Y plane, sampled before enhancement. */
internal data class QrLuminanceStats(
    val centerMean: Float,
    val darkFraction: Float,
    val brightFraction: Float
)

/** Small bounded exposure changes, with hysteresis and time for auto-exposure to settle. */
internal class QrExposureAssist {
    private var baseline: Int? = null
    private var previousDirection = 0
    private var stableFrames = 0
    private var lastRequest: Long? = null
    private var manualUntil = Long.MIN_VALUE

    fun onManualInteraction(nowNanos: Long, currentIndex: Int) {
        baseline = currentIndex
        manualUntil = nowNanos + 3_000_000_000L
        stableFrames = 0
    }

    fun update(
        nowNanos: Long,
        lighting: QrLuminanceStats,
        currentIndex: Int,
        minIndex: Int,
        maxIndex: Int,
        stepEv: Float
    ): Int? {
        if (nowNanos < manualUntil || minIndex > maxIndex || currentIndex !in minIndex..maxIndex ||
            !stepEv.isFinite() || stepEv <= 0f || !lighting.centerMean.isFinite() ||
            lighting.centerMean !in 0f..255f ||
            !lighting.darkFraction.isFinite() || lighting.darkFraction !in 0f..1f ||
            !lighting.brightFraction.isFinite() || lighting.brightFraction !in 0f..1f
        ) return null
        if (baseline == null) baseline = currentIndex
        val direction = when {
            lighting.centerMean < 45f && lighting.darkFraction > 0.25f -> 1
            lighting.centerMean > 220f && lighting.brightFraction > 0.25f -> -1
            lighting.centerMean in 75f..195f && lighting.darkFraction < 0.15f &&
                lighting.brightFraction < 0.15f -> 0
            else -> { stableFrames = 0; return null }
        }
        stableFrames = if (direction == previousDirection) stableFrames + 1 else 1
        previousDirection = direction
        if (stableFrames < 2 || lastRequest?.let { nowNanos - it < 1_000_000_000L } == true) return null
        val steps = (0.67f / stepEv).roundToInt()
        val target = ((baseline ?: currentIndex).toLong() + direction.toLong() * steps)
            .coerceIn(minIndex.toLong(), maxIndex.toLong()).toInt()
        if (currentIndex == target) return null
        lastRequest = nowNanos
        return target
    }
}
