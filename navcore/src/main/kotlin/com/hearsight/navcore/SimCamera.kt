package com.hearsight.navcore

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** A floor-standing box in world coordinates (X right, Y forward, Z up); [kind] is its label. */
data class Box(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val zMax: Double,
    val kind: Surface = Surface.STRUCTURE,
)

data class Scene(
    val height: Double = 1.6,          // camera height above the floor
    val pitchDeg: Double = 25.0,       // camera looking down
    val boxes: List<Box> = emptyList(),
    val stepDownAt: Double? = null,    // world Y beyond which the floor drops by stepDepth
    val stepDepth: Double = 0.2,
    val walkSpeed: Double = 0.0,       // metres per frame along the heading; restarts after loopAt
    val loopAt: Double = 6.0,
    val yawRateDeg: Double = 0.0,      // heading change per frame, + = turning right
    val imu: Boolean = false,          // provide gravity "up" and gyro deltas like a D455
    val imuErrorDeg: Double = 0.0,     // IMU-to-depth misalignment, tilted about the camera x axis
    val gyroNoiseDeg: Double = 0.0,    // random gyro error per frame
    val roadRightOf: Double? = null,   // floor with world X beyond this is road (else sidewalk)
    val grassLeftOf: Double? = null,   // floor with world X below this is grass
    val semantics: Boolean = false,    // attach perfect per-pixel labels (stands in for the model)
)

/**
 * Ray-cast synthetic depth camera, for tests and the app's demo mode (no RealSense needed).
 * Defaults mimic a RealSense D455 at 424x240 with stride 2: 87 degree horizontal FOV.
 * The camera walks along its heading and can turn; [truePose] gives the ground truth.
 */
class SimCamera(
    val scene: Scene = Scene(boxes = listOf(Box(-0.4, 0.5, 2.3, 2.8, 1.0))),
    val width: Int = 212,
    val height: Int = 120,
    hfovDeg: Double = 87.0,
    private val noise: Double = 0.005,
    seed: Int = 0,
    private val frameDelayMs: Long = 0,
) : DepthSource {
    private val f = (width / 2.0) / tan(Math.toRadians(hfovDeg) / 2)
    private val rnd = Random(seed)
    private var px = 0.0
    private var py = 0.0
    private var yaw = 0.0          // radians, + = right
    private var walked = 0.0
    private var lastRotation: DoubleArray? = null

    override fun start() {}
    override fun stop() {}

    override fun read(): DepthImage {
        if (frameDelayMs > 0) Thread.sleep(frameDelayMs)
        val img = render()
        px += scene.walkSpeed * sin(yaw)
        py += scene.walkSpeed * cos(yaw)
        walked += scene.walkSpeed
        yaw += Math.toRadians(scene.yawRateDeg)
        if (scene.walkSpeed > 0 && walked > scene.loopAt) {
            px = 0.0; py = 0.0; yaw = 0.0; walked = 0.0; lastRotation = null
        }
        return img
    }

    /** world <- camera rotation (columns = camera x, y, z axes in world coordinates). */
    private fun rotation(): DoubleArray {
        val th = Math.toRadians(scene.pitchDeg)
        val fwd = doubleArrayOf(sin(yaw), cos(yaw), 0.0)
        val xc = doubleArrayOf(cos(yaw), -sin(yaw), 0.0)
        val zc = doubleArrayOf(fwd[0] * cos(th), fwd[1] * cos(th), -sin(th))
        val yc = doubleArrayOf(
            zc[1] * xc[2] - zc[2] * xc[1], zc[2] * xc[0] - zc[0] * xc[2], zc[0] * xc[1] - zc[1] * xc[0],
        )
        return doubleArrayOf(xc[0], yc[0], zc[0], xc[1], yc[1], zc[1], xc[2], yc[2], zc[2])
    }

    /** Ground truth world <- camera, world X right, Y forward (as at start), Z up, floor at Z = 0. */
    fun truePose() = Rigid(rotation(), doubleArrayOf(px, py, scene.height))

    fun render(): DepthImage {
        val s = scene
        val rot = rotation()
        val ox = px; val oy = py; val oz = s.height
        val depth = FloatArray(width * height)
        val labels = if (s.semantics) ByteArray(width * height) else null

        for (v in 0 until height) for (u in 0 until width) {
            val x = (u - width / 2.0) / f
            val y = (v - height / 2.0) / f
            // ray = x * camX + y * camY + camZ; its camera-z component is 1, so t equals depth
            val dx = rot[0] * x + rot[1] * y + rot[2]
            val dy = rot[3] * x + rot[4] * y + rot[5]
            val dz = rot[6] * x + rot[7] * y + rot[8]
            var t = Double.POSITIVE_INFINITY
            var kind = Surface.SKY

            if (dz < 0) {
                var tf = -oz / dz
                if (s.stepDownAt != null) {
                    if (oy + tf * dy > s.stepDownAt) tf = (-s.stepDepth - oz) / dz
                    if (dy > 0) { // vertical face of the step
                        val tFace = (s.stepDownAt - oy) / dy
                        val zf = oz + tFace * dz
                        if (zf < 0 && zf > -s.stepDepth) tf = min(tf, tFace)
                    }
                }
                t = tf
                val hx = ox + tf * dx
                kind = when {
                    s.roadRightOf != null && hx > s.roadRightOf -> Surface.ROAD
                    s.grassLeftOf != null && hx < s.grassLeftOf -> Surface.TERRAIN
                    else -> Surface.WALKWAY
                }
            }
            for (b in s.boxes) {
                val hit = rayBox(ox, oy, oz, dx, dy, dz, b)
                if (hit != null && hit < t) { t = hit; kind = b.kind }
            }
            if (t.isFinite()) depth[v * width + u] = (t * (1 + noise * rnd.nextGaussian())).toFloat()
            if (labels != null) labels[v * width + u] = kind.code
        }

        var gravity: DoubleArray? = null
        var gyro: DoubleArray? = null
        if (s.imu) {
            // World up (0,0,1) in camera coordinates; tilted by the simulated IMU error.
            val e = Math.toRadians(s.pitchDeg + s.imuErrorDeg)
            gravity = doubleArrayOf(0.0, -cos(e), -sin(e))
            lastRotation?.let { prev ->
                var delta = M3.mul(M3.transpose(prev), rot)  // previous <- current
                if (s.gyroNoiseDeg > 0) {
                    val k = Math.toRadians(s.gyroNoiseDeg)
                    delta = M3.mul(delta, M3.exp(k * rnd.nextGaussian(), k * rnd.nextGaussian(), k * rnd.nextGaussian()))
                }
                gyro = delta
            }
        }
        lastRotation = rot
        return DepthImage(
            width, height, depth, f.toFloat(), f.toFloat(), (width / 2.0).toFloat(), (height / 2.0).toFloat(),
            gravity, gyro, labels?.let { SemanticImage(width, height, it) },
        )
    }

    private fun rayBox(ox: Double, oy: Double, oz: Double, dx: Double, dy: Double, dz: Double, b: Box): Double? {
        var tmin = Double.NEGATIVE_INFINITY
        var tmax = Double.POSITIVE_INFINITY
        fun slab(o: Double, d: Double, lo: Double, hi: Double): Boolean {
            if (d == 0.0) return o in lo..hi
            val t1 = (lo - o) / d; val t2 = (hi - o) / d
            tmin = max(tmin, min(t1, t2)); tmax = min(tmax, max(t1, t2))
            return true
        }
        if (!slab(ox, dx, b.xMin, b.xMax) || !slab(oy, dy, b.yMin, b.yMax) || !slab(oz, dz, 0.0, b.zMax)) return null
        return if (tmax >= tmin && tmax > 0) max(tmin, 0.0) else null
    }
}

private fun Random.nextGaussian(): Double {
    // Box-Muller
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return kotlin.math.sqrt(-2 * kotlin.math.ln(u1)) * cos(2 * Math.PI * u2)
}
