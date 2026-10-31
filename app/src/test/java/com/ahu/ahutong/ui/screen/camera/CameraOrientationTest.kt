package com.ahu.ahutong.ui.screen.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraOrientationTest {

    @Test
    fun `stable angles quantise to the nearest quadrant`() {
        assertEquals(0, quantizeCameraRotation(0f, 0))
        assertEquals(90, quantizeCameraRotation(90f, 0))
        assertEquals(180, quantizeCameraRotation(180f, 0))
        assertEquals(270, quantizeCameraRotation(270f, 0))
    }

    @Test
    fun `hysteresis keeps the old quadrant near a boundary`() {
        // 46° 刚越过 45° 中线，但未超过 5° 迟滞 → 维持 0
        assertEquals(0, quantizeCameraRotation(46f, 0))
        // 60° 已经离开边界 15° → 切到 90
        assertEquals(90, quantizeCameraRotation(60f, 0))
    }

    @Test
    fun `hysteresis works going the other way`() {
        // 从 90 回到 0 的边界同样是 45°：44° 差 1° → 维持 90
        assertEquals(90, quantizeCameraRotation(44f, 90))
        assertEquals(0, quantizeCameraRotation(30f, 90))
    }

    @Test
    fun `wraparound between 0 and 270 is handled`() {
        // 差值走最短方向：-45° 边界在 315°
        assertEquals(0, quantizeCameraRotation(316f, 0))
        assertEquals(270, quantizeCameraRotation(300f, 0))
    }

    @Test
    fun `non finite input keeps the current rotation`() {
        assertEquals(90, quantizeCameraRotation(Float.NaN, 90))
        assertEquals(0, quantizeCameraRotation(Float.POSITIVE_INFINITY, 0))
    }

    @Test
    fun `angular distance takes the short way around`() {
        assertEquals(10f, angularDistance(350f, 0f))
        assertEquals(10f, angularDistance(0f, 350f))
        assertEquals(180f, angularDistance(0f, 180f))
    }

    @Test
    fun `mode store defaults to scan and round-trips`() {
        assertEquals(CameraMode.SCAN, CameraMode.fromKey(null))
        assertEquals(CameraMode.SCAN, CameraMode.fromKey("unknown"))
        assertEquals(CameraMode.NOTE, CameraMode.fromKey(CameraMode.NOTE.key))
    }
}
