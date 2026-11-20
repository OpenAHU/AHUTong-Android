package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

internal data class EyePatch(
    val x: Float? = null,
    val y: Float? = null,
    val scaleX: Float? = null,
    val scaleY: Float? = null,
    val rotate: Float? = null,
    val open: Float? = null,
    val lookX: Float? = null,
    val lookY: Float? = null,
    val color: Int? = null,
)

internal data class BodyPatch(
    val x: Float? = null,
    val y: Float? = null,
    val scale: Float? = null,
    val rotate: Float? = null,
    val breathe: Float? = null,
    val color: Int? = null,
)

internal data class Anim(
    val type: String,
    val target: String,
    val prop: String,
    val amp: Float = 0f,
    val period: Float = 0f,
    val phase: Float = 0f,
    val speed: Float = 0f,
    val decay: Float = 0f,
    val interval: Float = 0f,
    val dur: Float = 0f,
    val phaseMs: Float = 0f,
    val depth: Float = 1f,
)

internal data class Frame(
    val at: Long,
    val body: BodyPatch? = null,
    val bothEyes: EyePatch? = null,
    val leftEye: EyePatch? = null,
    val rightEye: EyePatch? = null,
)

internal data class SeqSettle(val kind: String? = null, val next: String? = null)

internal data class BallSequence(val frames: List<Frame>, val settle: SeqSettle)

internal data class BallEmotion(
    val id: String,
    val name: String,
    val transition: Long = 500L,
    val gaze: Boolean = true,
    val pool: IntArray = intArrayOf(0, 8),
    val poolMs: LongArray = longArrayOf(9000L, 16000L),
    val blinkMs: LongArray? = null,
    val poolSpeed: Float = 6f,
    val openness: Float = 1f,
    val antics: Boolean = false,
    val baseBody: BodyPatch? = null,
    val baseBoth: EyePatch? = null,
    val baseLeft: EyePatch? = null,
    val baseRight: EyePatch? = null,
    val anims: List<Anim> = emptyList(),
    val sequence: BallSequence? = null,
)

/**
 * 空对话页的圆胖小球：复用 emotion-gallery 的形象与表情参数，
 * 进入页面时随机挑四个表情轮换演出。
 */
@Composable
internal fun EmotionBall(
    modifier: Modifier = Modifier,
    sessionKey: Any? = null,
    /** 落在标题栏里时保持安静：不弹跳、不做待机小动作。 */
    calm: Boolean = false,
) {
    val tour = remember(sessionKey) { EmotionBallData.emotions.shuffled().take(TOUR_SIZE) }
    val engine = remember(tour) { EmotionBallEngine(tour) }
    LaunchedEffect(engine, calm) { engine.calm = calm }
    val frameTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(engine) {
        while (true) {
            withFrameNanos { nanos ->
                engine.frame(nanos / 1_000_000L)
                frameTick.intValue++
            }
        }
    }
    Canvas(modifier) {
        // 读取帧计数，让 Canvas 只在绘制阶段失效，不触发整页重组。
        frameTick.intValue
        engine.draw(this)
    }
}

private const val TOUR_SIZE = 4
private const val TOUR_INTERVAL_MS = 4200L

private const val VIEW_BOX = 259f
private const val DEFAULT_BODY_COLOR = 0xFFF3F0EA.toInt()
private const val DEFAULT_EYE_COLOR = 0xFF1A1A1A.toInt()

private class BodyPose {
    var x = 0f
    var y = 0f
    var scale = 1f
    var rotate = 0f
    var color = DEFAULT_BODY_COLOR
    var breathe = 0.01f

    fun clone() = BodyPose().also {
        it.x = x; it.y = y; it.scale = scale; it.rotate = rotate
        it.color = color; it.breathe = breathe
    }

    fun apply(patch: BodyPatch?) {
        if (patch == null) return
        patch.x?.let { x = it }
        patch.y?.let { y = it }
        patch.scale?.let { scale = it }
        patch.rotate?.let { rotate = it }
        patch.breathe?.let { breathe = it }
        patch.color?.let { color = it }
    }
}

private class EyePose {
    var x = 0f
    var y = 0f
    var scaleX = 1f
    var scaleY = 1f
    var rotate = 0f
    var open = 1f
    var color = DEFAULT_EYE_COLOR
    var lookX = 0f
    var lookY = 0f

    fun clone() = EyePose().also {
        it.x = x; it.y = y; it.scaleX = scaleX; it.scaleY = scaleY
        it.rotate = rotate; it.open = open; it.color = color
        it.lookX = lookX; it.lookY = lookY
    }

    fun apply(patch: EyePatch?) {
        if (patch == null) return
        patch.x?.let { x = it }
        patch.y?.let { y = it }
        patch.scaleX?.let { scaleX = it }
        patch.scaleY?.let { scaleY = it }
        patch.rotate?.let { rotate = it }
        patch.open?.let { open = it }
        patch.color?.let { color = it }
        patch.lookX?.let { lookX = it }
        patch.lookY?.let { lookY = it }
    }
}

private class Pose {
    val body = BodyPose()
    val left = EyePose()
    val right = EyePose()

    fun clone() = Pose().also {
        it.body.x = body.x; it.body.y = body.y; it.body.scale = body.scale
        it.body.rotate = body.rotate; it.body.color = body.color; it.body.breathe = body.breathe
        copyEye(left, it.left)
        copyEye(right, it.right)
    }

    private fun copyEye(from: EyePose, to: EyePose) {
        to.x = from.x; to.y = from.y; to.scaleX = from.scaleX; to.scaleY = from.scaleY
        to.rotate = from.rotate; to.open = from.open; to.color = from.color
        to.lookX = from.lookX; to.lookY = from.lookY
    }
}

private class Spring(var x: Float, var v: Float = 0f, var target: Float = x) {
    fun step(freq: Float, damp: Float, dt: Float) {
        v += (-2f * damp * freq * v - freq * freq * (x - target)) * dt
        x += v * dt
        if (!x.isFinite() || !v.isFinite()) {
            x = target
            v = 0f
        }
    }
}

private sealed interface SeqResult {
    data object None : SeqResult
    data object Switch : SeqResult
    class Value(val pose: Pose) : SeqResult
}

private class EmotionBallEngine(tourSource: List<BallEmotion>) {

    /** 标题栏状态：不做弹跳与待机小动作，保持安静。 */
    var calm = false

    private val defs = EmotionBallData.emotions.associateBy { it.id }
    private val basePoses = mutableMapOf<String, Pose>()
    private val framePoses = mutableMapOf<String, List<Pair<Long, Pose>>>()
    private val tour = tourSource
    private var tourIndex = 0
    private var nextTourAt = 0L

    private val random = Random(System.nanoTime())
    private var started = false
    private var startMs = 0L
    private var lastMs = 0L
    private var dtSec = 1f / 60f
    private var clockSec = 0f

    private var def: BallEmotion? = null
    private var prevPose: Pose? = null
    private var lastPose: Pose? = null
    private var currentPose = Pose()
    private var emoStart = 0L
    private var transStart = 0L
    private var transDur = 0L

    private var seq: BallSequence? = null
    private var seqDone = false

    private val ringSpring = Spring(1f)
    private var ringSpeed = 6f
    private var ringSrc: Array<FloatArray> = EmotionBallData.expressions[0]
    private var ringDst: Array<FloatArray> = EmotionBallData.expressions[0]
    private var ringCur: Array<FloatArray> = EmotionBallData.expressions[0]
    private var exprIdx = 0
    private var poolPos = 0
    private var poolNext = 0L

    private val openSpring = Spring(1f)
    private val blinkQueue = ArrayDeque<Pair<Long, Float>>()
    private var blinkNext = Long.MAX_VALUE
    private var anticNext = 0L
    private var bounceAt = -1L

    private val bodyPath: Path
    private val bodyMinX: Float
    private val bodyMaxX: Float
    private val bodyMinY: Float
    private val bodyMaxY: Float
    private val silMinY: Float
    private val silMaxY: Float
    private val silRows: List<FloatArray>

    init {
        val ring = EmotionBallData.bodyRing
        bodyPath = Path().apply {
            moveTo(ring[0], ring[1])
            var i = 2
            while (i < ring.size) {
                lineTo(ring[i], ring[i + 1])
                i += 2
            }
            close()
        }
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var i = 0
        while (i < ring.size) {
            minX = min(minX, ring[i]); maxX = max(maxX, ring[i])
            minY = min(minY, ring[i + 1]); maxY = max(maxY, ring[i + 1])
            i += 2
        }
        bodyMinX = minX; bodyMaxX = maxX; bodyMinY = minY; bodyMaxY = maxY
        silMinY = minY
        silMaxY = maxY
        silRows = buildSilhouette(ring, minY, maxY)
    }

    private fun buildSilhouette(ring: FloatArray, minY: Float, maxY: Float): List<FloatArray> {
        val step = 2f
        val count = ((maxY - minY) / step).toInt() + 1
        val rows = ArrayList<FloatArray>(count)
        val points = ring.size / 2
        for (r in 0 until count) {
            val y = minY + r * step
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (e in 0 until points) {
                val a = e * 2
                val b = ((e + 1) % points) * 2
                val y0 = ring[a + 1]
                val y1 = ring[b + 1]
                if ((y0 <= y && y1 >= y) || (y1 <= y && y0 >= y)) {
                    val t = if (y1 == y0) 0f else (y - y0) / (y1 - y0)
                    val x = ring[a] + (ring[b] - ring[a]) * t
                    if (x < lo) lo = x
                    if (x > hi) hi = x
                }
            }
            if (lo > hi) {
                lo = EmotionBallData.HEAD_C - 4f
                hi = EmotionBallData.HEAD_C + 4f
            }
            rows.add(floatArrayOf(lo, hi))
        }
        return rows
    }

    private fun silAt(y: Float): FloatArray {
        val ratio = ((y.coerceIn(silMinY, silMaxY) - silMinY) / 2f)
        val idx = ratio.roundToIntSafe().coerceIn(0, silRows.lastIndex)
        return silRows[idx]
    }

    private fun basePose(emotion: BallEmotion): Pose = basePoses.getOrPut(emotion.id) {
        val pose = Pose()
        pose.body.apply(emotion.baseBody)
        pose.left.apply(emotion.baseBoth)
        pose.right.apply(emotion.baseBoth)
        pose.left.apply(emotion.baseLeft)
        pose.right.apply(emotion.baseRight)
        pose
    }

    private fun framesOf(emotion: BallEmotion): List<Pair<Long, Pose>> =
        framePoses.getOrPut(emotion.id) {
            emotion.sequence?.frames.orEmpty()
                .sortedBy { it.at }
                .map { frame ->
                    val pose = basePose(emotion).clone()
                    pose.body.apply(frame.body)
                    pose.left.apply(frame.bothEyes)
                    pose.right.apply(frame.bothEyes)
                    pose.left.apply(frame.leftEye)
                    pose.right.apply(frame.rightEye)
                    frame.at to pose
                }
        }

    fun frame(nowMs: Long) {
        if (!started) {
            started = true
            startMs = nowMs
            lastMs = nowMs
            setEmotion(tour.first(), nowMs, initial = true)
            nextTourAt = nowMs + TOUR_INTERVAL_MS
        }
        dtSec = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.05f)
        lastMs = nowMs
        clockSec = (nowMs - startMs) / 1000f
        if (tour.size > 1 && nowMs >= nextTourAt) {
            tourIndex = (tourIndex + 1) % tour.size
            setEmotion(tour[tourIndex], nowMs, initial = false)
            nextTourAt = nowMs + TOUR_INTERVAL_MS
        }
        currentPose = compose(nowMs, 0)
    }

    private fun setEmotion(emotion: BallEmotion, now: Long, initial: Boolean) {
        val previousId = def?.id
        prevPose = lastPose?.clone()
        def = emotion
        emoStart = now
        transStart = now
        transDur = if (initial) 0L else emotion.transition
        seq = emotion.sequence
        seqDone = false
        poolPos = 0
        setExpr(emotion.pool.firstOrNull() ?: 0, if (emotion.poolSpeed >= 10f) 10f else 8f)
        poolNext = now + rand(emotion.poolMs[0].toFloat(), emotion.poolMs[1].toFloat()).toLong()
        blinkQueue.clear()
        if (!initial && previousId != null && previousId != emotion.id && emotion.blinkMs != null) {
            blinkNow(now)
        }
        blinkNext = emotion.blinkMs?.let {
            now + rand(it[0].toFloat(), it[1].toFloat()).toLong()
        } ?: Long.MAX_VALUE
        anticNext = now + rand(2500f, 5000f).toLong()
        if (initial) {
            ringSpring.x = 1f
            ringSpring.v = 0f
            ringSpring.target = 1f
            openSpring.x = emotion.openness
            openSpring.v = 0f
            openSpring.target = emotion.openness
        }
    }

    private fun setExpr(index: Int, speed: Float) {
        if (index == exprIdx && ringSpring.x >= 0.999f) return
        val s = ringSpring.x.coerceIn(0f, 1f)
        ringSrc = arrayOf(
            lerpRing(ringSrc[0], ringDst[0], s),
            lerpRing(ringSrc[1], ringDst[1], s),
        )
        ringDst = EmotionBallData.expressions[index]
        ringSpring.x = 0f
        ringSpring.v = 0f
        ringSpring.target = 1f
        ringSpeed = speed
        exprIdx = index
    }

    private fun blinkNow(now: Long) {
        blinkQueue.addLast(now to 0.05f)
        blinkQueue.addLast(now + 70 to 0.05f)
        blinkQueue.addLast(now + 150 to 1.08f)
        blinkQueue.addLast(now + 300 to 1f)
        if (random.nextFloat() < 0.14f) {
            blinkQueue.addLast(now + 370 to 0.05f)
            blinkQueue.addLast(now + 480 to 1f)
        }
    }

    private fun compose(now: Long, depth: Int): Pose {
        val emotion = def ?: return Pose()
        val t = now - emoStart
        var pose: Pose
        when (val sampled = sampleSequence(t, now)) {
            SeqResult.Switch -> return if (depth < 4) compose(now, depth + 1) else basePose(emotion).clone()
            is SeqResult.Value -> pose = sampled.pose
            SeqResult.None -> pose = basePose(emotion).clone()
        }

        val breathe = pose.body.breathe
        if (breathe != 0f) {
            val phase = ((now - startMs) / 1000f) * (Math.PI.toFloat() * 2f) / 3.6f
            pose.body.scale += breathe * sin(phase)
            if (!calm) pose.body.y += breathe * 55f * sin(phase + 0.6f)
        }

        for (anim in emotion.anims) applyAnim(pose, anim, t.toFloat())

        if (now >= poolNext) {
            if (emotion.pool.size > 1) {
                poolPos = (poolPos + 1 + rand(0f, emotion.pool.size - 1f).toInt()) % emotion.pool.size
                setExpr(emotion.pool[poolPos], emotion.poolSpeed)
            }
            poolNext = now + rand(emotion.poolMs[0].toFloat(), emotion.poolMs[1].toFloat()).toLong()
        }

        if (emotion.blinkMs != null && now >= blinkNext) {
            blinkNow(now)
            blinkNext = now + rand(emotion.blinkMs[0].toFloat(), emotion.blinkMs[1].toFloat()).toLong()
        }
        var openKey: Float? = null
        while (blinkQueue.isNotEmpty() && now >= blinkQueue.first().first) {
            openKey = blinkQueue.removeFirst().second
        }
        openSpring.target = openKey ?: if (blinkQueue.isEmpty()) emotion.openness else openSpring.target

        if (!calm && emotion.antics && now >= anticNext) {
            val pick = random.nextFloat()
            if (pick < 0.8f) bounceAt = now else blinkNow(now)
            anticNext = now + rand(9000f, 18000f).toLong()
        }

        val steps = max(1, ceil(dtSec / (1f / 120f)).toInt())
        val sub = dtSec / steps
        repeat(steps) {
            ringSpring.step(ringSpeed, 1f, sub)
            openSpring.step(26f, 1f, sub)
        }

        ringCur = if (ringSpring.x < 0.999f || abs(ringSpring.v) > 0.001f) {
            val s = ringSpring.x.coerceIn(0f, 1.35f)
            arrayOf(
                lerpRing(ringSrc[0], ringDst[0], s),
                lerpRing(ringSrc[1], ringDst[1], s),
            )
        } else {
            ringDst
        }

        if (calm) {
            bounceAt = -1L
        } else if (bounceAt >= 0) {
            val elapsed = (now - bounceAt) / 1000f
            if (elapsed >= BOUNCE_TOTAL) {
                bounceAt = -1L
            } else {
                var acc = 0f
                var index = 0
                while (index < BOUNCE_SEGS.size && elapsed >= acc + BOUNCE_SEGS[index].second) {
                    acc += BOUNCE_SEGS[index].second
                    index++
                }
                val seg = BOUNCE_SEGS[min(index, BOUNCE_SEGS.lastIndex)]
                val n = (elapsed - acc) / seg.second
                pose.body.y += -4f * seg.first * n * (1f - n)
            }
        }

        if (emotion.gaze) {
            val w = (now - startMs) / 1000f
            pose.left.lookX += 1.4f * sin(0.42f * w) + 0.5f * sin(1.0f * w)
            pose.right.lookX += 1.4f * sin(0.42f * w + 1f) + 0.5f * sin(1.0f * w + 2f)
            pose.left.lookY += 0.9f * sin(0.58f * w)
            pose.right.lookY += 0.9f * sin(0.58f * w + 1f)
        }

        val openScale = openSpring.x.coerceIn(0.02f, 1.5f)
        pose.left.open = pose.left.open.coerceIn(0f, 1.3f) * openScale
        pose.right.open = pose.right.open.coerceIn(0f, 1.3f) * openScale
        pose.left.scaleX = max(pose.left.scaleX, 0.05f)
        pose.left.scaleY = max(pose.left.scaleY, 0.05f)
        pose.right.scaleX = max(pose.right.scaleX, 0.05f)
        pose.right.scaleY = max(pose.right.scaleY, 0.05f)

        val tt = now - transStart
        if (transDur > 0 && tt < transDur) {
            prevPose?.let { pose = lerpPose(it, pose, easeInOutCubic(tt.toFloat() / transDur)) }
        }
        lastPose = pose
        return pose
    }

    private fun sampleSequence(t: Long, now: Long): SeqResult {
        val sequence = seq ?: return SeqResult.None
        val frames = framesOf(def ?: return SeqResult.None)
        if (frames.isEmpty()) return SeqResult.None
        val last = frames.last()
        if (t >= last.first) {
            if (!seqDone) {
                seqDone = true
                val settle = sequence.settle
                if (settle.next != null) {
                    defs[settle.next]?.let { setEmotion(it, now, initial = false) }
                    return SeqResult.Switch
                }
                if (settle.kind == "base") {
                    prevPose = lastPose?.clone()
                    transStart = now
                    transDur = def?.transition ?: 500L
                    seq = null
                    return SeqResult.None
                }
            }
            return SeqResult.Value(last.second.clone())
        }
        if (t <= frames.first().first) return SeqResult.Value(frames.first().second.clone())
        for (i in 0 until frames.size - 1) {
            val a = frames[i]
            val b = frames[i + 1]
            if (t >= a.first && t < b.first) {
                val progress = easeInOutCubic((t - a.first).toFloat() / (b.first - a.first).toFloat())
                return SeqResult.Value(lerpPose(a.second, b.second, progress))
            }
        }
        return SeqResult.Value(last.second.clone())
    }

    private fun applyAnim(pose: Pose, anim: Anim, t: Float) {
        val value = when (anim.type) {
            "sine" -> anim.amp * sin(TAU * t / (if (anim.period > 0f) anim.period else 2000f) + anim.phase)
            "pulse" -> anim.amp * 0.5f * (1f - cos(TAU * t / (if (anim.period > 0f) anim.period else 1000f) + anim.phase))
            "jitter" -> {
                val s = t / 1000f * (if (anim.speed > 0f) anim.speed else 8f)
                var v = (sin(s * 3.1f + seed) + sin(s * 5.7f + seed * 2.3f) + sin(s * 9.3f + seed * 4.1f)) / 3f * anim.amp
                if (anim.decay > 0f) v *= (1f - t / anim.decay).coerceIn(0f, 1f)
                v
            }
            "scan" -> {
                val period = if (anim.period > 0f) anim.period else 800f
                val p = ((t + anim.phaseMs) % period) / period
                val tri = if (p < 0.5f) p * 4f - 1f else 3f - p * 4f
                anim.amp * tri
            }
            "glance" -> {
                val period = if (anim.period > 0f) anim.period else 3600f
                val ph = TAU * (((t + anim.phaseMs) % period) / period) + anim.phase
                anim.amp * tanh(2.8f * sin(ph))
            }
            "blink" -> {
                val interval = if (anim.interval > 0f) anim.interval else 3800f
                val dur = if (anim.dur > 0f) anim.dur else 200f
                val p = (t + anim.phaseMs + seed * 97f) % interval
                if (p >= dur) 0f else -anim.depth * sin(Math.PI.toFloat() * (p / dur))
            }
            else -> 0f
        }
        if (anim.target == "body") {
            val body = pose.body
            when (anim.prop) {
                "scale" -> body.scale += value
                "x" -> body.x += value
                "y" -> body.y += value
                "rotate" -> body.rotate += value
            }
            return
        }
        val targets: List<EyePose> = when (anim.target) {
            "eyes" -> listOf(pose.left, pose.right)
            "left" -> listOf(pose.left)
            "right" -> listOf(pose.right)
            else -> emptyList()
        }
        for (eye in targets) {
            when (anim.prop) {
                "scale" -> {
                    eye.scaleX += value
                    eye.scaleY += value
                }
                "x" -> eye.x += value
                "y" -> eye.y += value
                "rotate" -> eye.rotate += value
                "open" -> eye.open += value
                "lookX" -> eye.lookX += value
                "lookY" -> eye.lookY += value
            }
        }
    }

    fun draw(scope: DrawScope) {
        val pose = currentPose
        val scale = scope.size.minDimension / VIEW_BOX
        scope.withTransform({
            translate(15f * scale, 15f * scale)
            scale(scale, scale, Offset.Zero)
        }) {
            drawGroundShadow()
            withTransform({
                translate(HEAD_C + pose.body.x, HEAD_C + pose.body.y)
                rotate(pose.body.rotate, Offset.Zero)
                scale(pose.body.scale, pose.body.scale, Offset.Zero)
                translate(-HEAD_C, -HEAD_C)
            }) {
                drawBody()
                drawEye(pose.left, ringCur[0])
                drawEye(pose.right, ringCur[1])
            }
        }
    }

    /**
     * 温润的流光球身：底色是调色板与白色混合的薄雾，
     * 球内的颜色来自几团缓慢游动的点光源，与对话页背景同一手法。
     */
    private fun DrawScope.drawBody() {
        val centerX = (bodyMinX + bodyMaxX) / 2f
        val centerY = (bodyMinY + bodyMaxY) / 2f
        val radius = (bodyMaxX - bodyMinX) / 2f
        val t = clockSec * (TAU / 28f)
        val phase = clockSec * 0.08f
        drawPath(bodyPath, mixWhite(paletteAt(phase), 0.7f).copy(alpha = 0.55f))
        clipPath(bodyPath) {
            drawSoftBlob(
                center = Offset(
                    centerX + radius * 0.3f * sin(t),
                    centerY - radius * 0.3f + radius * 0.18f * cos(t),
                ),
                color = mixWhite(paletteAt(phase + 0.4f), 0.45f),
                radius = radius,
                alpha = 0.3f,
            )
            drawSoftBlob(
                center = Offset(
                    centerX + radius * 0.55f + radius * 0.12f * cos(t + 1.3f),
                    centerY + radius * 0.1f + radius * 0.2f * sin(t * 0.8f),
                ),
                color = mixWhite(paletteAt(phase + 2.1f), 0.45f),
                radius = radius * 0.85f,
                alpha = 0.26f,
            )
            drawSoftBlob(
                center = Offset(
                    centerX + radius * 0.12f * sin(t + 2.2f),
                    centerY + radius * 0.55f + radius * 0.12f * cos(t * 0.6f),
                ),
                color = mixWhite(paletteAt(phase + 4f), 0.45f),
                radius = radius * 0.9f,
                alpha = 0.22f,
            )
            // 球内右下渐变阴影：靠近边缘悄悄压暗，托出球体体积
            val innerCenter = Offset(centerX - radius * 0.22f, centerY - radius * 0.24f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.16f),
                    ),
                    center = innerCenter,
                    radius = radius * 1.28f,
                ),
                radius = radius * 1.28f,
                center = innerCenter,
            )
            // 左上一点点柔高光，保住球体的温润感
            val highlightCenter = Offset(centerX - radius * 0.34f, centerY - radius * 0.38f)
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.2f),
                        1f to Color.Transparent,
                    ),
                    center = highlightCenter,
                    radius = radius * 0.9f,
                ),
                radius = radius * 0.9f,
                center = highlightCenter,
            )
        }
        // 玻璃感边缘光：极细的一圈白，左上 / 右下各一段中间厚、两端收细的亮边
        drawPath(
            path = bodyPath,
            color = Color.White.copy(alpha = 0.34f),
            style = Stroke(width = 1.7f),
        )
        val edgeRadius = radius * 0.99f
        val edgeCenter = Offset(centerX, centerY)
        drawEdgeLight(edgeCenter, edgeRadius, startDeg = 196f, totalDeg = 58f, maxWidth = 2.2f, maxAlpha = 0.6f)
        drawEdgeLight(edgeCenter, edgeRadius, startDeg = 16f, totalDeg = 50f, maxWidth = 1.8f, maxAlpha = 0.48f)
    }

    /** 沿圆弧铺一串宽度按正弦收放的短段，做出中间厚、两端薄的玻璃亮边。 */
    private fun DrawScope.drawEdgeLight(
        center: Offset,
        radius: Float,
        startDeg: Float,
        totalDeg: Float,
        maxWidth: Float,
        maxAlpha: Float,
    ) {
        val segments = 26
        val step = totalDeg / segments
        val pi = Math.PI.toFloat()
        for (i in 0 until segments) {
            val u = (i + 0.5f) / segments
            val bell = sin(pi * u)
            drawArc(
                color = Color.White.copy(alpha = maxAlpha * bell),
                startAngle = startDeg + i * step - step * 0.1f,
                sweepAngle = step * 1.2f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = maxWidth * bell.coerceAtLeast(0.05f), cap = StrokeCap.Butt),
            )
        }
    }

    /** 球体外投影：往右下偏的渐变阴影，光从左上来，边缘只露右下的一弯。 */
    private fun DrawScope.drawSoftBlob(center: Offset, color: Color, radius: Float, alpha: Float) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to color.copy(alpha = alpha),
                    0.55f to color.copy(alpha = alpha * 0.4f),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    private fun paletteAt(position: Float): Color {
        val colors = ASSISTANT_GLOW_COLORS
        val count = colors.size
        var p = position % count
        if (p < 0f) p += count
        val index = p.toInt()
        return Color(
            lerpColor(
                colors[index].toArgb(),
                colors[(index + 1) % count].toArgb(),
                p - index,
            )
        )
    }

    /** 球下的一小片落地阴影：椭圆径向渐变，随球弹起时留在原地。 */
    private fun DrawScope.drawGroundShadow() {
        val center = Offset(HEAD_C, 230f)
        val radius = 76f
        withTransform({ scale(1f, 0.11f, center) }) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }

    private fun DrawScope.drawEye(pose: EyePose, ring: FloatArray) {
        val base = centroid(ring)
        val open = pose.open.coerceIn(0.02f, 2.4f)
        val sy = (pose.scaleY * open).coerceIn(0.02f, 2.4f)
        val sxBase = pose.scaleX
        val halfH = EmotionBallData.EYE_HALF * sy + 2f
        var eyeY = HEAD_C + (base.y - HEAD_C) + pose.y + pose.lookY
        eyeY = eyeY.coerceIn(silMinY + halfH, silMaxY - halfH)
        val sil = silAt(eyeY)
        val centerX = (sil[0] + sil[1]) / 2f
        val halfWidth = max((sil[1] - sil[0]) / 2f, 12f)
        val ox = (base.x - HEAD_C) + pose.x + pose.lookX
        val theta = (ox / halfWidth).coerceIn(-1.15f, 1.15f)
        val cosTheta = cos(theta)
        if (cosTheta <= 0.02f) return
        val eyeX = centerX + halfWidth * sin(theta) * 0.985f
        val dyN = (eyeY - HEAD_C) / 130f
        val fy = sqrt(1f - dyN * dyN * 0.22f)
        val path = ringPath(ring)
        withTransform({
            translate(eyeX, eyeY)
            if (pose.rotate != 0f) rotate(pose.rotate, Offset.Zero)
            scale(sxBase * cosTheta, sy * fy, Offset.Zero)
            translate(-base.x, -base.y)
        }) {
            drawPath(path, color = Color(pose.color))
        }
    }

    private val seed = random.nextFloat() * 100f

    private companion object {
        const val HEAD_C = EmotionBallData.HEAD_C
        const val TAU = Math.PI.toFloat() * 2f
        val BOUNCE_SEGS = listOf(48f to 0.5f, 28f to 0.382f, 14f to 0.27f, 6f to 0.177f)
        val BOUNCE_TOTAL = BOUNCE_SEGS.sumOf { it.second.toDouble() }.toFloat()
    }
}

private fun ringPath(ring: FloatArray): Path = Path().apply {
    moveTo(ring[0], ring[1])
    var i = 2
    while (i < ring.size) {
        lineTo(ring[i], ring[i + 1])
        i += 2
    }
    close()
}

private fun centroid(ring: FloatArray): Offset {
    var x = 0f
    var y = 0f
    var i = 0
    while (i < ring.size) {
        x += ring[i]
        y += ring[i + 1]
        i += 2
    }
    val count = ring.size / 2
    return Offset(x / count, y / count)
}

private fun lerpRing(a: FloatArray, b: FloatArray, t: Float): FloatArray {
    val out = FloatArray(a.size)
    for (i in a.indices) out[i] = a[i] + (b[i] - a[i]) * t
    return out
}

private fun lerpPose(a: Pose, b: Pose, t: Float): Pose {
    val out = Pose()
    out.body.x = lerp(a.body.x, b.body.x, t)
    out.body.y = lerp(a.body.y, b.body.y, t)
    out.body.scale = lerp(a.body.scale, b.body.scale, t)
    out.body.rotate = lerp(a.body.rotate, b.body.rotate, t)
    out.body.color = lerpColor(a.body.color, b.body.color, t)
    out.body.breathe = lerp(a.body.breathe, b.body.breathe, t)
    lerpEye(a.left, b.left, out.left, t)
    lerpEye(a.right, b.right, out.right, t)
    return out
}

private fun lerpEye(a: EyePose, b: EyePose, out: EyePose, t: Float) {
    out.x = lerp(a.x, b.x, t)
    out.y = lerp(a.y, b.y, t)
    out.scaleX = lerp(a.scaleX, b.scaleX, t)
    out.scaleY = lerp(a.scaleY, b.scaleY, t)
    out.rotate = lerp(a.rotate, b.rotate, t)
    out.open = lerp(a.open, b.open, t)
    out.color = lerpColor(a.color, b.color, t)
    out.lookX = lerp(a.lookX, b.lookX, t)
    out.lookY = lerp(a.lookY, b.lookY, t)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mixWhite(color: Color, amount: Float): Color =
    Color(lerpColor(color.toArgb(), 0xFFFFFFFF.toInt(), amount))

private fun lerpColor(a: Int, b: Int, t: Float): Int {
    if (a == b) return b
    val ar = (a shr 16) and 0xFF
    val ag = (a shr 8) and 0xFF
    val ab = a and 0xFF
    val br = (b shr 16) and 0xFF
    val bg = (b shr 8) and 0xFF
    val bb = b and 0xFF
    val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
    val g = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
    val blue = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or blue
}

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3f) / 2f

private fun EmotionBallEngine.rand(a: Float, b: Float): Float = Math.random().toFloat() * (b - a) + a

private fun Float.roundToIntSafe(): Int = if (isFinite()) roundToInt() else 0
