package com.ahu.ahutong.ui.screen.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.PlanarYUVLuminanceSource
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 只解 QR，在分析线程复用可见区域的 Y 亮度缓冲。
 *
 * - 命中一次即回调并停止（[emitted]），避免同一张码连发；
 * - 不持有相机帧（`analyze` 结束前 close）；
 * - 原文只在内存里过一手，**不落库、不打日志**（学习通的 `enc` 属于凭据）。
 */
internal class QrAnalyzer(
    private val onFrame: (List<QrDetection>, ImageProxy) -> Unit,
    private val onFailure: () -> Unit,
    private val nanoTime: () -> Long = System::nanoTime,
    private val recognizeAfterNanos: Long? = null,
    /** 命中后是否停止分析。多码候选需要继续扫一小段时间再决定。 */
    private val stopAfterFirst: Boolean = true,
    private val onAssist: ((QrAssistFrame) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private val decoder = QrImageDecoder()
    private val emitted = AtomicBoolean(false)
    private var previousFrameNanos: Long? = null
    private var analyzedFrames = 0
    private var luminance: ByteArray? = null
    private var failedFrames = 0

    override fun analyze(image: ImageProxy) {
        if (emitted.get()) {
            image.close()
            return
        }
        try {
            val now = nanoTime()
            if (recognizeAfterNanos?.let { now < it } == true) return
            if (previousFrameNanos?.let { now - it < 50_000_000L } == true) return
            previousFrameNanos = now
            val plane = image.planes[0]
            val crop = image.cropRect
            require(crop.left >= 0 && crop.top >= 0 && crop.right <= image.width && crop.bottom <= image.height)
            val width = crop.width()
            val height = crop.height()
            val data = copyQrLuminance(plane.buffer, width, height, plane.rowStride, plane.pixelStride,
                luminance, crop.left, crop.top)
            luminance = data
            val source = PlanarYUVLuminanceSource(
                data, width, height, 0, 0, width, height, false
            )
            val potential = mutableListOf<QrPotentialTarget>()
            val detections = decoder.decodeCandidates(source, tryHarder = analyzedFrames % 4 == 0,
                onCandidate = if (onAssist == null) null else { center, size ->
                    potential += QrPotentialTarget(center, size)
                })
                .map { it.rotated(image.imageInfo.rotationDegrees) }
            analyzedFrames = (analyzedFrames + 1) % 4
            if (detections.isNotEmpty()) {
                onFrame(detections, image)
                if (stopAfterFirst) emitted.set(true)
            } else if (onAssist != null) {
                // Do not retain ImageProxy or its buffer in a queued UI callback.
                val rotation = image.imageInfo.rotationDegrees
                val geometry = QrDetection("", potential.map { it.center }, width.toFloat() / height)
                    .rotated(rotation)
                val targets = potential.mapIndexed { index, target ->
                    target.copy(center = geometry.corners[index])
                }
                onAssist(QrAssistFrame(now, geometry.aspectRatio,
                    targets.minByOrNull { kotlin.math.hypot(it.center.x - 0.5f, it.center.y - 0.5f) },
                    sampleQrLighting(data, width, height)))
            }
            failedFrames = 0
        } catch (_: Exception) {
            // A single invalid frame is transient. Repeated pipeline faults need visible recovery.
            failedFrames++
            if (failedFrames >= 3 && emitted.compareAndSet(false, true)) onFailure()
        } finally {
            image.close()
        }
    }

}
