package com.hearsight.navcore

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** A floor-standing box in world coordinates (X right, Y forward, Z up). */
data class Box(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double, val zMax: Double)

data class Scene(
    val height: Double = 1.6,          // camera height above the floor
    val pitchDeg: Double = 25.0,       // camera looking down
    val boxes: List<Box> = emptyList(),
    val stepDownAt: Double? = null,    // Y beyond which the floor drops by stepDepth
    val stepDepth: Double = 0.2,
    val walkSpeed: Double = 0.0,       // metres per frame; loops back after loopAt
    val loopAt: Double = 6.0,
)

/** Ray-cast synthetic depth camera, for tests and the app's demo mode (no RealSense needed). */
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
    private var y0 = 0.0

    override fun start() {}
    override fun stop() {}

    override fun read(): DepthImage {
        if (frameDelayMs > 0) Thread.sleep(frameDelayMs)
        val img = render()
        y0 += scene.walkSpeed
        if (y0 > scene.loopAt) y0 = 0.0
        return img
    }

    fun render(): DepthImage {
        val s = scene
        val th = Math.toRadians(s.pitchDeg)
        // camera axes in world coordinates
        val zc = doubleArrayOf(0.0, cos(th), -sin(th))
        val yc = doubleArrayOf(0.0, -sin(th), -cos(th))
        val ox = 0.0; val oy = y0; val oz = s.height
        val depth = FloatArray(width * height)

        for (v in 0 until height) for (u in 0 until width) {
            val x = (u - width / 2.0) / f
            val y = (v - height / 2.0) / f
            // camera-z component of the ray is 1, so the hit parameter t equals depth
            val dx = x
            val dy = y * yc[1] + zc[1]
            val dz = y * yc[2] + zc[2]
            var t = Double.POSITIVE_INFINITY

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
            }
            for (b in s.boxes) {
                val hit = rayBox(ox, oy, oz, dx, dy, dz, b)
                if (hit != null) t = min(t, hit)
            }
            if (t.isFinite()) depth[v * width + u] = (t * (1 + noise * rnd.nextGaussian())).toFloat()
        }
        return DepthImage(width, height, depth, f.toFloat(), f.toFloat(), (width / 2.0).toFloat(), (height / 2.0).toFloat())
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
