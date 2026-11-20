package com.ahu.ahutong.ui.screen.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
    val zzz: Float? = null,
    val orbit: Float? = null,
    val ribbons: Float? = null,
    val confetti: Float? = null,
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
    /** 指定表情（任务状态映射）；为 null 时在空对话页随机巡演。 */
    emotionId: String? = null,
) {
    val tour = remember(sessionKey) { EmotionBallData.emotions.shuffled() }
    val engine = remember(tour) { EmotionBallEngine(tour) }
    LaunchedEffect(engine, calm) { engine.calm = calm }
    LaunchedEffect(engine, emotionId) { engine.setDirect(emotionId) }
    val frameTick = remember { mutableIntStateOf(0) }
    LaunchedEffect(engine) {
        while (true) {
            withFrameNanos { nanos ->
                engine.frame(nanos / 1_000_000L)
                frameTick.intValue++
            }
        }
    }
    val tapModifier = remember(engine, calm) {
        if (calm) Modifier
        else Modifier.pointerInput(engine) {
            detectTapGestures { engine.onTap() }
        }
    }
    Canvas(modifier.then(tapModifier)) {
        // 读取帧计数，让 Canvas 只在绘制阶段失效，不触发整页重组。
        frameTick.intValue
        engine.draw(this)
    }
}

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
    var zzz = 0f
    var orbit = 0f
    var ribbons = 0f
    var confetti = 0f
    var yaw = 0f

    fun clone() = BodyPose().also {
        it.x = x; it.y = y; it.scale = scale; it.rotate = rotate
        it.color = color; it.breathe = breathe
        it.zzz = zzz; it.orbit = orbit; it.ribbons = ribbons; it.confetti = confetti
        it.yaw = yaw
    }

    fun apply(patch: BodyPatch?) {
        if (patch == null) return
        patch.x?.let { x = it }
        patch.y?.let { y = it }
        patch.scale?.let { scale = it }
        patch.rotate?.let { rotate = it }
        patch.breathe?.let { breathe = it }
        patch.zzz?.let { zzz = it }
        patch.orbit?.let { orbit = it }
        patch.ribbons?.let { ribbons = it }
        patch.confetti?.let { confetti = it }
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
        it.body.zzz = body.zzz; it.body.orbit = body.orbit
        it.body.ribbons = body.ribbons; it.body.confetti = body.confetti
        it.body.yaw = body.yaw
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

private class Plane(val tilt: Float, val roll: Float)

private class TrailPoint(val x: Float, val y: Float, val z: Float, val l: Float)

private class Trail(
    var lam: Float,
    var lamVel: Float,
    val tilt: Float,
    val roll: Float,
    var rad: Float,
    val radVel: Float,
    val follow: Float,
    var carry: Float,
    val arc: Float,
    val width: Float,
    val hueBase: Float,
    val hueSpan: Float,
    val hueVel: Float,
    val orbitMode: Boolean,
) {
    var life = 0f
    var retract = 0f
    var alpha = 0f
    val hist = ArrayDeque<TrailPoint>()
    var backPath: Path? = null
    var frontPath: Path? = null
    var colors: List<Color> = emptyList()
    var start = Offset.Zero
    var end = Offset.Zero
}

private class Confetti(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val maxLife: Float,
    val size: Float,
    val round: Boolean,
    val color: Color,
    val stretch: Float,
    var rotation: Float,
    val rotationSpeed: Float,
) {
    var life = 0f
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
    private var tourEnabled = true
    private var directId: String? = null

    private val random = Random(System.nanoTime())
    private var started = false
    private var startMs = 0L
    private var lastMs = 0L
    private var dtSec = 1f / 60f
    private var clockSec = 0f

    private var spin: Spring? = null
    private val planes = mutableListOf<Plane>()
    private var planeG = 4
    private var baseHue = 0f
    private var spawnIdx = 0
    private val spawnAt = ArrayDeque<Long>()
    private var wasFast = false
    private var prevYaw = 0f
    private var orbitNextAt = 0L
    private val trails = mutableListOf<Trail>()
    private val confetti = mutableListOf<Confetti>()

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
            val direct = directId
            tourEnabled = direct == null
            setEmotion(direct?.let { defs[it] } ?: tour.first(), nowMs, initial = true)
            nextTourAt = nowMs + TOUR_INTERVAL_MS
        }
        dtSec = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.05f)
        lastMs = nowMs
        clockSec = (nowMs - startMs) / 1000f
        if (tourEnabled && tour.size > 1 && nowMs >= nextTourAt) {
            tourIndex = (tourIndex + 1) % tour.size
            setEmotion(tour[tourIndex], nowMs, initial = false)
            nextTourAt = nowMs + TOUR_INTERVAL_MS
        }
        currentPose = compose(nowMs, 0)
        updateEffects(nowMs, currentPose.body.yaw)
    }

    /** 外部按任务状态指定表情；null 表示回到随机巡演。 */
    fun setDirect(id: String?) {
        if (id == directId) return
        directId = id
        if (!started) return
        applyDirect(lastMs)
    }

    private fun applyDirect(now: Long) {
        val id = directId
        if (id == null) {
            tourEnabled = true
            nextTourAt = now + TOUR_INTERVAL_MS
            return
        }
        tourEnabled = false
        val emotion = defs[id] ?: return
        if (def?.id != emotion.id) setEmotion(emotion, now, initial = false)
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
        if (!calm) {
            // 进入表情时的一次性事件：自旋甩彩带 / 撒花
            val fx = basePose(emotion).body
            if (fx.ribbons > 0f) startSpin(if (fx.ribbons >= 1f) 2 else 1)
            if (fx.confetti > 0f) burstConfetti()
        }
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
            when {
                pick < 0.45f -> startSpin(1)
                pick < 0.8f -> bounceAt = now
                else -> blinkNow(now)
            }
            anticNext = now + rand(9000f, 18000f).toLong()
        }

        val steps = max(1, ceil(dtSec / (1f / 120f)).toInt())
        val sub = dtSec / steps
        repeat(steps) {
            ringSpring.step(ringSpeed, 1f, sub)
            openSpring.step(26f, 1f, sub)
            spin?.let { sp ->
                sp.step(6.2f, 1f, sub)
                if (abs(sp.target - sp.x) < 0.01f && abs(sp.v) < 0.05f) spin = null
            }
        }
        pose.body.yaw = spin?.x ?: 0f

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
            if (!calm) drawGroundShadow()
            drawTrails(front = false)
            withTransform({
                translate(HEAD_C + pose.body.x, HEAD_C + pose.body.y)
                rotate(pose.body.rotate, Offset.Zero)
                scale(pose.body.scale, pose.body.scale, Offset.Zero)
                translate(-HEAD_C, -HEAD_C)
            }) {
                drawBody()
                drawEye(pose.left, ringCur[0], pose.body.yaw)
                drawEye(pose.right, ringCur[1], pose.body.yaw)
            }
            drawTrails(front = true)
            if (!calm) {
                drawConfetti()
                drawSleepZ(pose.body.zzz)
            }
        }
    }

    private fun updateEffects(now: Long, yaw: Float) {
        val dt = dtSec
        val dYaw = yaw - prevYaw
        prevYaw = yaw
        val velocity = if (dt > 0f) dYaw / dt else 0f
        val fast = !calm && abs(velocity) >= 0.9f
        val dir = if (velocity >= 0f) 1 else -1

        if (fast && !wasFast) {
            makePlanes()
            spawnAt.clear()
            for (q in 0 until planeG) spawnAt.addLast(now + (q * rand(55f, 105f)).toLong())
        }
        if (!fast) spawnAt.clear()
        wasFast = fast
        if (abs(velocity) >= 5f) {
            while (spawnAt.isNotEmpty() && now >= spawnAt.first()) {
                spawnAt.removeFirst()
                spawnTrail(yaw - rand(0f, 0.18f) * dir)
            }
        }

        val orbitWant = !calm && currentPose.body.orbit > 0f
        if (orbitWant && now >= orbitNextAt) {
            if (trails.count { it.orbitMode } < 2) {
                spawnOrbit(trails.count { it.orbitMode })
            }
            orbitNextAt = now + 700
        }
        updateTrails(dt, fast, dir, dYaw, velocity, orbitWant)
        updateConfetti(dt)
    }

    /** 点一下小球：转一圈甩彩带（标题栏状态下不响应）。 */
    fun onTap() {
        if (calm) return
        startSpin(1)
    }

    private fun startSpin(turns: Int) {
        if (calm || spin != null) return
        val direction = if (random.nextBoolean()) 1f else -1f
        spin = Spring(0f, 0f, max(1, turns) * TAU * direction)
    }

    private fun makePlanes() {
        planes.clear()
        val count = if (random.nextFloat() < 0.45f) 2 else 3
        val roll0 = rand(-0.9f, 0.9f)
        for (index in 0 until count) {
            planes.add(
                Plane(
                    tilt = rand(0.16f, 0.72f),
                    roll = roll0 + index * (Math.PI.toFloat() / count) + rand(-0.15f, 0.15f),
                )
            )
        }
        planeG = random.nextInt(4, 7)
        baseHue = rand(0f, 360f)
        spawnIdx = 0
    }

    private fun spawnTrail(lam0: Float) {
        if (trails.size > 8 || planes.isEmpty()) return
        val plane = planes[spawnIdx % planes.size]
        val tierStep = 38f / max(planeG - 1, 1)
        val width = when {
            planeG <= 3 -> rand(8f, 10.5f)
            planeG == 4 -> rand(6.6f, 8.6f)
            else -> rand(5.6f, 7.4f)
        }
        trails.add(
            Trail(
                lam = lam0,
                lamVel = (if (random.nextBoolean()) 1f else -1f) * rand(0.5f, 1.1f),
                tilt = plane.tilt + rand(-0.04f, 0.04f),
                roll = plane.roll + rand(-0.05f, 0.05f),
                rad = 116f + spawnIdx * tierStep + rand(-1.5f, 1.5f),
                radVel = rand(0f, 2.5f),
                follow = rand(0.74f, 0.94f),
                carry = 0f,
                arc = rand(2.2f, 3.4f),
                width = width,
                hueBase = baseHue + 360f * spawnIdx / max(planeG, 1) + rand(-14f, 14f),
                hueSpan = rand(45f, 95f) * (if (random.nextBoolean()) 1f else -1f),
                hueVel = rand(18f, 42f) * (if (random.nextBoolean()) 1f else -1f),
                orbitMode = false,
            )
        )
        spawnIdx++
    }

    private fun spawnOrbit(index: Int) {
        if (trails.size > 8) return
        trails.add(
            Trail(
                lam = rand(0f, TAU),
                lamVel = (if (random.nextBoolean()) 1f else -1f) * rand(1.7f, 2.3f),
                tilt = rand(0.1f, 0.22f),
                roll = rand(-0.12f, 0.12f),
                rad = 124f + index * 16f,
                radVel = 0f,
                follow = 0.8f,
                carry = 0f,
                arc = rand(2.4f, 3.2f),
                width = rand(5.5f, 7f),
                hueBase = rand(0f, 360f),
                hueSpan = rand(45f, 95f) * (if (random.nextBoolean()) 1f else -1f),
                hueVel = rand(18f, 42f) * (if (random.nextBoolean()) 1f else -1f),
                orbitMode = true,
            )
        )
    }

    private fun orbitPoint(trail: Trail, lam: Float): TrailPoint {
        val hx = trail.rad * sin(lam)
        val hy = -trail.rad * cos(lam) * sin(trail.tilt)
        val ca = cos(trail.roll)
        val sa = sin(trail.roll)
        return TrailPoint(
            x = HEAD_C + hx * ca - hy * sa,
            y = HEAD_C + hx * sa + hy * ca,
            z = cos(lam) * cos(trail.tilt),
            l = lam,
        )
    }

    private fun updateTrails(
        dt: Float,
        fast: Boolean,
        dir: Int,
        dYaw: Float,
        velocity: Float,
        orbitWant: Boolean,
    ) {
        for (index in trails.indices.reversed()) {
            val trail = trails[index]
            trail.life += dt
            val retract = if (trail.orbitMode) !orbitWant else (!fast || trail.life > 5f)
            trail.retract = (trail.retract + (if (retract) dt / 0.5f else -dt / 0.35f))
                .coerceIn(0f, 1f)
            if (retract && trail.retract >= 1f) {
                trails.removeAt(index)
                continue
            }
            if (trail.orbitMode) {
                trail.lam += trail.lamVel * dt + dYaw * trail.follow
            } else if (fast) {
                trail.carry = velocity * trail.follow
                trail.lam += dYaw * trail.follow + trail.lamVel * dt
            } else {
                trail.lam += (trail.carry + trail.lamVel) * dt
                trail.carry *= kotlin.math.exp(-2.6f * dt)
                trail.lamVel *= kotlin.math.exp(-2.6f * dt)
            }
            trail.rad += trail.radVel * dt

            val last = trail.hist.lastOrNull()?.l ?: (trail.lam - 0.001f * dir)
            val dl = trail.lam - last
            val steps = min(ceil(abs(dl) / 0.09f).toInt(), 24)
            for (s in 1..steps) {
                trail.hist.addLast(orbitPoint(trail, last + dl * s / steps))
            }
            if (trail.hist.isEmpty()) trail.hist.addLast(orbitPoint(trail, trail.lam))

            val span = trail.arc * (1f - trail.retract * trail.retract * (3f - 2f * trail.retract))
            while (trail.hist.size > 2 && abs(trail.lam - trail.hist.first().l) > span) {
                trail.hist.removeFirst()
            }
            val over = trail.hist.first().let { abs(trail.lam - it.l) - span }
            if (trail.hist.size >= 2 && over > 0f) {
                val first = trail.hist.removeFirst()
                val trimmed = first.l + (if (trail.lam - first.l >= 0f) 1f else -1f) * over
                trail.hist.addFirst(orbitPoint(trail, trimmed))
            }
            while (trail.hist.size > 48) trail.hist.removeFirst()

            val zHead = cos(trail.lam) * cos(trail.tilt)
            val pz = 0.72f + 0.28f * zHead.coerceIn(0f, 1f)
            var grow = min(trail.life / 0.34f, 1f)
            grow = grow * grow * (3f - 2f * grow)
            val width = trail.width * pz * 1.7f * grow *
                (1f - 0.72f * trail.retract * trail.retract)
            trail.alpha = min(trail.life / 0.26f, 1f)
            val points = trail.hist.toList()
            val built = buildTrailPaths(points, width)
            trail.backPath = built.first
            trail.frontPath = built.second
            if (points.size >= 2) {
                trail.start = Offset(points.first().x, points.first().y)
                trail.end = Offset(points.last().x, points.last().y)
            }
            val hue = trail.hueBase + trail.hueVel * trail.life
            trail.colors = (0..3).map { i ->
                val frac = i / 3f
                Color.hsv(
                    ((hue + frac * trail.hueSpan) % 360f + 360f) % 360f,
                    0.55f,
                    0.95f,
                    alpha = 0.9f,
                )
            }
        }
    }

    private fun buildTrailPaths(points: List<TrailPoint>, width: Float): Pair<Path?, Path?> {
        val count = points.size
        if (count < 2 || width < 0.5f) return null to null
        val nx = FloatArray(count)
        val ny = FloatArray(count)
        for (e in 0 until count) {
            val p0 = points[if (e > 0) e - 1 else 0]
            val p1 = points[if (e < count - 1) e + 1 else count - 1]
            var dx = p1.x - p0.x
            var dy = p1.y - p0.y
            val length = sqrt(dx * dx + dy * dy).takeIf { it > 0.0001f } ?: 1f
            dx /= length
            dy /= length
            val d = width * (0.5f + (e.toFloat() / (count - 1)) * 0.5f) / 2f
            nx[e] = -dy * d
            ny[e] = dx * d
        }

        fun segment(a: Int, b: Int): Path {
            val path = Path()
            path.moveTo(points[a].x + nx[a], points[a].y + ny[a])
            for (k in a + 1..b) path.lineTo(points[k].x + nx[k], points[k].y + ny[k])
            for (k in b downTo a) path.lineTo(points[k].x - nx[k], points[k].y - ny[k])
            path.close()
            return path
        }

        var back: Path? = null
        var front: Path? = null
        var index = 0
        while (index < count) {
            val isFront = points[index].z >= 0f
            var end = index
            while (end + 1 < count && (points[end + 1].z >= 0f) == isFront) end++
            val a = max(index - 1, 0)
            val b = min(end + 1, count - 1)
            if (b > a) {
                val segmentPath = segment(a, b)
                if (isFront) {
                    if (front == null) front = segmentPath else front.addPath(segmentPath)
                } else {
                    if (back == null) back = segmentPath else back.addPath(segmentPath)
                }
            }
            index = end + 1
        }
        return back to front
    }

    private fun DrawScope.drawTrails(front: Boolean) {
        for (trail in trails) {
            val path = if (front) trail.frontPath else trail.backPath
            if (path == null || trail.colors.size < 2 || trail.alpha <= 0f) continue
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = trail.colors,
                    start = trail.start,
                    end = trail.end,
                ),
                alpha = trail.alpha,
            )
        }
    }

    private fun burstConfetti() {
        if (calm) return
        val count = 20
        for (i in 0 until count) {
            if (confetti.size >= 60) break
            val angle = (i.toFloat() / count) * TAU + rand(-0.35f, 0.35f)
            val speed = rand(170f, 360f)
            confetti.add(
                Confetti(
                    x = HEAD_C + cos(angle) * rand(96f, 116f),
                    y = HEAD_C + sin(angle) * rand(96f, 116f),
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - rand(20f, 75f),
                    maxLife = rand(0.45f, 0.85f),
                    size = rand(3.5f, 8f),
                    round = random.nextFloat() < 0.3f,
                    color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
                    stretch = if (random.nextFloat() < 0.7f) 1.9f else 1f,
                    rotation = rand(0f, 360f),
                    rotationSpeed = rand(-260f, 260f),
                )
            )
        }
    }

    private fun updateConfetti(dt: Float) {
        for (index in confetti.indices.reversed()) {
            val piece = confetti[index]
            piece.life += dt
            if (piece.life >= piece.maxLife) {
                confetti.removeAt(index)
                continue
            }
            piece.x += piece.vx * dt
            piece.y += piece.vy * dt
            val drag = 0.94f.pow(60f * dt)
            piece.vx *= drag
            piece.vy = piece.vy * drag + 40f * dt
            piece.rotation += piece.rotationSpeed * dt
        }
    }

    private fun DrawScope.drawConfetti() {
        for (piece in confetti) {
            val u = (piece.life / piece.maxLife).coerceIn(0f, 1f)
            val fade = if (u < 0.1f) u / 0.1f else (1f - (u - 0.1f) / 0.9f).pow(1.7f)
            val size = (piece.size * (1f - 0.4f * u)).coerceAtLeast(0.5f)
            withTransform({
                translate(piece.x, piece.y)
                rotate(piece.rotation, Offset.Zero)
                scale(size, size * piece.stretch, Offset.Zero)
            }) {
                if (piece.round) {
                    drawCircle(piece.color.copy(alpha = fade), radius = 0.5f)
                } else {
                    drawRect(
                        color = piece.color.copy(alpha = fade),
                        topLeft = Offset(-0.5f, -0.5f),
                        size = Size(1f, 1f),
                    )
                }
            }
        }
    }

    private fun DrawScope.drawSleepZ(zzz: Float) {
        if (zzz <= 0.01f) return
        for (z in 0 until 3) {
            val progress = ((clockSec * 0.33f + z / 3f) % 1f)
            val alpha = (
                (if (progress < 0.18f) progress / 0.18f else 1f - (progress - 0.18f) / 0.82f) *
                    0.8f * zzz
                ).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue
            val size = (12f + progress * 11f) * 0.8f
            val centerX = 180f + progress * 34f + 4f * sin(progress * 9f)
            val centerY = 48f - progress * 42f
            val half = size / 2f
            val rotation = -10f + progress * 14f
            val strokeWidth = size * 0.16f
            withTransform({
                translate(centerX, centerY)
                rotate(rotation, Offset.Zero)
            }) {
                val path = Path().apply {
                    moveTo(-half, -half)
                    lineTo(half, -half)
                    lineTo(-half, half)
                    lineTo(half, half)
                }
                drawPath(
                    path = path,
                    color = Color(0xFFA8A296).copy(alpha = alpha),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
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
            // 球内阴影：贴边整体收暗保证和轮廓贴合，右下方向再叠一层斜向渐变
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.7f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.055f),
                    ),
                    center = Offset(centerX, centerY),
                    radius = radius * 1.02f,
                ),
                radius = radius * 1.02f,
                center = Offset(centerX, centerY),
            )
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.065f),
                    ),
                    start = Offset(centerX - radius * 0.85f, centerY - radius * 0.85f),
                    end = Offset(centerX + radius, centerY + radius),
                ),
                topLeft = Offset(-15f, -15f),
                size = Size(VIEW_BOX, VIEW_BOX),
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

    private fun DrawScope.drawEye(pose: EyePose, ring: FloatArray, yaw: Float) {
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
        val total = (ox / halfWidth).coerceIn(-1.15f, 1.15f) + yaw
        val cosTheta = cos(total)
        if (cosTheta <= 0.02f) return
        val eyeX = centerX + halfWidth * sin(total) * 0.985f
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
        val CONFETTI_COLORS = listOf(
            Color(0xFFF9705C),
            Color(0xFF5B95F0),
            Color(0xFF3FBE86),
            Color(0xFFF5B13F),
            Color(0xFF9A72EE),
            Color(0xFF35C3BD),
        )
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
    out.body.zzz = lerp(a.body.zzz, b.body.zzz, t)
    out.body.orbit = lerp(a.body.orbit, b.body.orbit, t)
    out.body.ribbons = lerp(a.body.ribbons, b.body.ribbons, t)
    out.body.confetti = lerp(a.body.confetti, b.body.confetti, t)
    out.body.yaw = lerp(a.body.yaw, b.body.yaw, t)
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
