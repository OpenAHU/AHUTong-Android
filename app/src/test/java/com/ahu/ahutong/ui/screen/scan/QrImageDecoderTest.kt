package com.ahu.ahutong.ui.screen.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QrImageDecoderTest {
    @Test
    fun nearFieldQrThatFillsTheImageStillDecodesWithItsOriginalCoordinates() {
        val payload = "8661234567"
        val decoder = QrImageDecoder()
        for (side in listOf(840, 1260)) {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, side, side,
                mapOf(EncodeHintType.MARGIN to 0))
            val pixels = ByteArray(side * side) { if (matrix[it % side, it / side]) 18 else 235.toByte() }
            for (inverted in listOf(false, true)) {
                val data = if (inverted) ByteArray(pixels.size) { (255 - (pixels[it].toInt() and 255)).toByte() } else pixels
                for (tryHarder in listOf(false, true)) {
                    val result = decoder.decodeCandidates(luminance(data, side, side), tryHarder).single()
                    assertEquals(payload, result.raw)
                    assertTrue(result.corners.first().x < 0.01f && result.corners.first().y < 0.01f)
                    assertTrue(result.corners[2].x > 0.99f && result.corners[2].y > 0.99f)
                }
            }
        }
        assertTrue(decoder.decodeCandidates(luminance(ByteArray(1280 * 960) { 235.toByte() }, 1280, 960), false).isEmpty())
    }

    @Test
    fun largeCodeRecoveryDoesNotDiscardASmallerCodeElsewhereInTheSameFrame() {
        for (invertedLarge in listOf(false, true)) for (invertedSmall in listOf(false, true)) {
            for (tryHarder in listOf(false, true)) {
                val found = QrImageDecoder().decodeCandidates(
                    largeAndSmallImage(invertedLarge, invertedSmall), tryHarder)
                assertEquals(setOf("8661234567", "small-fixture"), found.map { it.raw }.toSet(),
                    "large inverted=$invertedLarge, small inverted=$invertedSmall, harder=$tryHarder")
                assertEquals(2, found.size)
                assertTrue(found.single { it.raw == "8661234567" }.center.x < 0.5f)
                assertTrue(found.single { it.raw == "small-fixture" }.center.x > 0.8f)
            }
        }
    }

    @Test
    fun aLargeAndSmallCodeWithTheSamePayloadRemainSeparateTargets() {
        val found = QrImageDecoder().decodeCandidates(largeAndSmallImage(smallPayload = "8661234567"), false)
        assertEquals(listOf("8661234567", "8661234567"), found.map { it.raw })
        assertTrue(found.first().centerDistance(found.last()) > 0.3f)
    }

    private fun largeAndSmallImage(
        invertedLarge: Boolean = false,
        invertedSmall: Boolean = false,
        smallPayload: String = "small-fixture"
    ): PlanarYUVLuminanceSource {
        val width = 1900
        val height = 1300
        val pixels = ByteArray(width * height) { index ->
            if (invertedLarge && index % width < 1300) 18 else 235.toByte()
        }
        fun paste(payload: String, left: Int, top: Int, side: Int, margin: Int, inverted: Boolean) {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, side, side,
                mapOf(EncodeHintType.MARGIN to margin))
            for (y in 0 until side) for (x in 0 until side) {
                val value = if (matrix[x, y]) 18 else 235
                pixels[(top + y) * width + left + x] = (if (inverted) 255 - value else value).toByte()
            }
        }
        paste("8661234567", 0, 20, 1260, 0, invertedLarge)
        paste(smallPayload, 1600, 900, 160, 4, invertedSmall)
        return luminance(pixels, width, height)
    }

    @Test
    fun multipleCodesIncludingMixedPolaritiesKeepTheirOwnPayloadAndGeometry() {
        for (inverted in listOf(false, true)) {
            val found = QrImageDecoder().decodeCandidates(twoCodeImage(inverted))
            assertEquals(setOf("fixture-a", "fixture-b"), found.map { it.raw }.toSet())
            assertEquals(2, found.size)
            assertTrue(found.all { it.corners.size == 4 })
            assertTrue(found.single { it.raw == "fixture-a" }.center.x <
                found.single { it.raw == "fixture-b" }.center.x)
        }
    }

    private fun twoCodeImage(invertedSecond: Boolean = false): PlanarYUVLuminanceSource {
        val bytes = ByteArray(1280 * 720) { 235.toByte() }
        fun paste(payload: String, left: Int, top: Int, side: Int, inverted: Boolean = false) {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, side, side)
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                val value = if (matrix[x, y]) 18 else 235
                bytes[(top + y) * 1280 + left + x] = (if (inverted) 255 - value else value).toByte()
            }
        }
        paste("fixture-a", 400, 248, 224)
        paste("fixture-b", 700, 210, 160, invertedSecond)
        return luminance(bytes, 1280, 720)
    }

    @Test
    fun thoroughFallbackStillFindsDenseSmallQrMissedByTheFastSearch() {
        val payload = "fixture-" + "a".repeat(50)
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 80, 80)
        val bytes = ByteArray(1280 * 720) { 235.toByte() }
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            bytes[(150 + y) * 1280 + 140 + x] = if (matrix[x, y]) 18 else 235.toByte()
        }
        val decoder = QrImageDecoder()
        val source = luminance(bytes, 1280, 720)
        assertTrue(decoder.decodeCandidates(source, tryHarder = false).isEmpty())
        assertEquals(payload, decoder.decodeCandidates(source, tryHarder = true).single().raw)
    }

    @Test
    fun cameraSearchRestoresFullFrameGeometryAndFindsCodesOutsideTheAimArea() {
        val payload = "https://mobilelearn.chaoxing.com/widget/sign/e?id=fixture"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 224, 224)
        val decoder = QrImageDecoder()
        for ((left, top) in listOf(528 to 248, 8 to 8, 1048 to 488)) {
            for (inverted in listOf(false, true)) {
                val bytes = ByteArray(1280 * 720) { if (inverted) 0 else 255.toByte() }
                for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                    bytes[(top + y) * 1280 + left + x] = if (matrix[x, y] xor inverted) 0 else 255.toByte()
                }
                val source = luminance(bytes, 1280, 720)
                for (tryHarder in listOf(false, true)) {
                    val actual = decoder.decodeCandidates(source, tryHarder).single()
                    assertEquals(payload, actual.raw)
                    assertEquals(1280f / 720, actual.aspectRatio)
                }
            }
        }
    }

    @Test
    fun croppedPlaneReusesItsStorageWithoutChangingPaddingOrBufferPosition() {
        val plane = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 55, 4, 5, 6, 55, 7, 8, 9))
        plane.position(1)
        val destination = ByteArray(4)
        val result = copyQrLuminance(plane, 2, 2, 4, 1, destination, left = 1, top = 1)
        assertSame(destination, result)
        assertContentEquals(byteArrayOf(5, 6, 8, 9), result)
        assertEquals(1, plane.position())
        assertContentEquals(byteArrayOf(1, 2, 3), copyQrLuminance(plane, 3, 1, 4, 1, destination))
        assertFailsWith<IllegalArgumentException> { copyQrLuminance(plane, 3, 1, 4, 1, left = 2) }
        assertFailsWith<IllegalArgumentException> { copyQrLuminance(plane, 2, 2, 4, 1, top = 2) }
    }

    @Test
    fun geometryUsesFinderModulesForDifferentVersionsAndOffCentreFramePositions() {
        for (payload in listOf("8661234567", "long-qr-fixture-".repeat(25))) {
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 640, 360)
            val bytes = ByteArray(matrix.width * matrix.height) { index ->
                if (matrix[index % matrix.width, index / matrix.width]) 0 else 255.toByte()
            }
            val result = requireNotNull(QrImageDecoder().decodeCandidates(luminance(bytes, matrix.width, matrix.height)).singleOrNull())
            var left = matrix.width
            var right = 0
            var top = matrix.height
            var bottom = 0
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) {
                left = minOf(left, x); right = maxOf(right, x + 1)
                top = minOf(top, y); bottom = maxOf(bottom, y + 1)
            }
            assertEquals(payload, result.raw)
            assertEquals(4, result.corners.size)
            assertTrue(abs(result.corners[0].x * matrix.width - left) <= 4f)
            assertTrue(abs(result.corners[0].y * matrix.height - top) <= 4f)
            assertTrue(abs(result.corners[2].x * matrix.width - right) <= 4f)
            assertTrue(abs(result.corners[2].y * matrix.height - bottom) <= 4f)
        }
    }

    @Test
    fun contiguousPlaneBulkCopyPreservesStrideAndTheOriginalPosition() {
        val plane = ByteBuffer.wrap(byteArrayOf(99, 1, 2, 3, 55, 4, 5, 6))
        plane.position(1)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), copyQrLuminance(plane, 3, 2, 4, 1))
        assertEquals(1, plane.position())
    }
    @Test
    fun cameraPlaneRespectsPaddingPixelStrideAndBufferPosition() {
        val plane = ByteBuffer.wrap(byteArrayOf(99, 99, 1, 55, 2, 55, 99, 99, 3, 55, 4))
        plane.position(2)

        assertContentEquals(byteArrayOf(1, 2, 3, 4), copyQrLuminance(plane, 2, 2, 6, 2))
        assertEquals(2, plane.position())
    }

    @Test
    fun malformedFrameIsRejectedBeforeReadingOutsideTheBuffer() {
        assertFailsWith<IllegalArgumentException> {
            copyQrLuminance(ByteBuffer.allocate(3), 2, 2, 2, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            copyQrLuminance(ByteBuffer.allocate(16), 0, 2, 2, 1)
        }
    }

    @Test
    fun cameraAndPhotoDecoderReadNormalAndInvertedQrWithoutChangingPayload() {
        val payload = "https://mobilelearn.chaoxing.com/widget/sign/e?id=1&enc=a%2Bb"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256)
        val normal = ByteArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0 else 255.toByte()
        }
        val inverted = ByteArray(normal.size) { index -> (255 - (normal[index].toInt() and 255)).toByte() }

        val decoder = QrImageDecoder()
        for (pixels in listOf(normal, inverted)) {
            assertEquals(payload, decoder.decodeCandidates(luminance(pixels, matrix.width, matrix.height)).single().raw)
        }
    }

    @Test
    fun rotatedCameraFrameDecodesAndBlankPhotoDoesNotProduceAResult() {
        val payload = "8661234567"
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256)
        val rotated = ByteArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[y, matrix.width - 1 - x]) 0 else 255.toByte()
        }
        val decoder = QrImageDecoder()
        assertEquals(payload, decoder.decodeCandidates(luminance(rotated, matrix.width, matrix.height)).single().raw)
        assertTrue(decoder.decodeCandidates(luminance(ByteArray(256 * 256) { 255.toByte() }, 256, 256)).isEmpty())
    }

    @Test
    fun repeatedCameraOrGalleryResultsAreDeliveredOnceUntilExplicitRetry() {
        val gate = ScanResultGate()
        assertFalse(gate.accept("  "))
        assertTrue(gate.accept("code"))
        assertFalse(gate.accept("code"))
        assertFalse(gate.accept("another code"))
        gate.reset()
        assertTrue(gate.accept("code"))
    }

    private fun luminance(bytes: ByteArray, width: Int, height: Int) =
        PlanarYUVLuminanceSource(bytes, width, height, 0, 0, width, height, false)

    @Test
    fun cameraRotationPreservesPayloadAndMapsDetectorCornersToThePreview() {
        val detected = QrDetection("raw", listOf(QrPoint(0.2f, 0.3f)), 2f)
        assertEquals(QrPoint(0.7f, 0.2f), detected.rotated(90).corners.single())
        assertEquals(0.5f, detected.rotated(90).aspectRatio)
        assertEquals(detected, detected.rotated(360))
    }

    @Test
    fun fittedPreviewKeepsTheCentreAndMapsCornersInsideTheLetterboxedImage() {
        val centre = projectQrPoint(QrPoint(0.5f, 0.5f), 2f, 300f, 600f)
        assertEquals(150f, centre.x, 0.001f)
        assertEquals(300f, centre.y, 0.001f)
        val landscape = projectQrPoint(QrPoint(0.4f, 0.4f), 2f, 300f, 600f)
        assertEquals(120f, landscape.x, 0.001f)
        assertEquals(285f, landscape.y, 0.001f)
        assertEquals(QrPoint(0f, 225f), projectQrPoint(QrPoint(0f, 0f), 2f, 300f, 600f))
        assertEquals(QrPoint(300f, 375f), projectQrPoint(QrPoint(1f, 1f), 2f, 300f, 600f))
        assertEquals(QrPoint(225f, 0f), projectQrPoint(QrPoint(0f, 0f), 0.5f, 600f, 300f))
        assertEquals(QrPoint(375f, 300f), projectQrPoint(QrPoint(1f, 1f), 0.5f, 600f, 300f))
        val portrait = projectQrPoint(QrPoint(0.2f, 0.3f), 0.5f, 300f, 600f)
        assertEquals(60f, portrait.x, 0.001f)
        assertEquals(180f, portrait.y, 0.001f)
    }
}
