package com.ahu.ahutong.ui.screen.main.home

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundParallaxTest {

    @Test
    fun `soft zone follows input linearly`() {
        assertEquals(0f, dampedParallax(0f), 1e-6f)
        assertEquals(0.35f, dampedParallax(0.35f), 1e-6f)
        assertEquals(-0.7f, dampedParallax(-0.7f), 1e-6f)
    }

    @Test
    fun `beyond the soft edge damping compresses the movement`() {
        val atEdge = dampedParallax(BackgroundParallaxSoftEdge)
        val slightlyPast = dampedParallax(BackgroundParallaxSoftEdge + 0.1f)
        assertTrue(slightlyPast > atEdge)
        // 强阻尼：越界 0.1 只换来远小于 0.1 的额外位移
        assertTrue(slightlyPast - atEdge < 0.1f)
    }

    @Test
    fun `output never reaches the wall so the wallpaper cannot expose background`() {
        listOf(1f, 2f, 50f, Float.MAX_VALUE, Float.POSITIVE_INFINITY).forEach { input ->
            val value = dampedParallax(input)
            assertTrue(value < 1f, "input=$input produced $value")
            assertTrue(value > 0f)
        }
    }

    @Test
    fun `sign is preserved and nan is neutral`() {
        assertEquals(0f, dampedParallax(Float.NaN))
        assertTrue(dampedParallax(-3f) < 0f)
        assertTrue(dampedParallax(3f) > 0f)
        assertEquals(abs(dampedParallax(3f)), abs(dampedParallax(-3f)), 1e-6f)
    }

    @Test
    fun `hitting the wall rebases the centre so reversing takes effect immediately`() {
        val range = BackgroundParallaxTiltRangeDeg
        // 往正方向一路推到越过墙
        val pushed = range + 6f
        val rebased = rebaseCenter(pushed, 0f)
        assertEquals(pushed - range, rebased, 1e-4f)
        // 画面仍贴在同一侧（不跳回中间），但零点已换到墙上
        val walled = tiltParallax(pushed, 0f, rebased, 0f)
        assertEquals(-dampedParallax(1f), walled.x, 1e-4f)
        // 反向倾斜立刻生效：再小的一点反向也会让位移离开墙
        val back = tiltParallax(pushed - 2f, 0f, rebased, 0f)
        assertTrue(abs(back.x) < abs(walled.x), "back=${back.x} wall=${walled.x}")
    }

    @Test
    fun `centre does not move while still inside the range`() {
        val inside = BackgroundParallaxTiltRangeDeg * 0.5f
        assertEquals(0f, rebaseCenter(inside, 0f), 1e-4f)
        assertEquals(inside, rebaseCenter(inside, inside), 1e-4f)
    }

    @Test
    fun `rebasing works on the negative wall too`() {
        val range = BackgroundParallaxTiltRangeDeg
        val rebased = rebaseCenter(-range - 4f, 0f)
        assertEquals(-range - 4f + range, rebased, 1e-4f)
        val back = tiltParallax(-range - 2f, 0f, rebased, 0f)
        assertTrue(abs(back.x) < abs(dampedParallax(1f)))
    }

    @Test
    fun `ordinary hand movement stays subtle`() {
        // 日常握持的 ±3° 抖动只应产生很小位移（满幅需要 18°）
        val small = tiltParallax(3f, -3f, 0f, 0f)
        assertTrue(abs(small.x) < 0.2f, "x=${small.x}")
        assertTrue(abs(small.y) < 0.2f, "y=${small.y}")
        // 且必须小于软区上限，不能提前进入阻尼段
        assertTrue(abs(small.x) < BackgroundParallaxSoftEdge)
    }

    @Test
    fun `tilt maps degrees to bounded normalised parallax`() {
        val still = tiltParallax(0f, 0f, 0f, 0f)
        assertEquals(0f, still.x, 1e-6f)
        assertEquals(0f, still.y, 1e-6f)

        // 右倾 9° → 背景向左 1 个软区上限附近；反向倾斜符号相反
        val right = tiltParallax(BackgroundParallaxTiltRangeDeg, 0f, 0f, 0f)
        assertTrue(right.x < -0.9f && right.x > -1f)
        val left = tiltParallax(-BackgroundParallaxTiltRangeDeg, 0f, 0f, 0f)
        assertTrue(left.x > 0.9f && left.x < 1f)

        // 完全翻转设备也只逼近墙，不会越界
        val extreme = tiltParallax(180f, 180f, 0f, 0f)
        assertTrue(abs(extreme.x) < 1f && abs(extreme.y) < 1f)
    }

    @Test
    fun `baseline removes static posture`() {
        // 斜握着不动：基线相同 → 居中
        val held = tiltParallax(12f, -8f, 12f, -8f)
        assertEquals(0f, held.x, 1e-6f)
        assertEquals(0f, held.y, 1e-6f)
    }

    @Test
    fun `gravity angles are finite for degenerate vectors`() {
        val (roll, pitch) = gravityAngles(0f, 0f, 0f)
        assertTrue(roll.isFinite() && pitch.isFinite())
    }
}