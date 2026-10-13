package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Estimates the "up" direction in camera coordinates from the IMU.
 *
 * Accelerometer: a resting accelerometer measures the reaction to gravity, which points up.
 * For the RealSense D435i / D455 the IMU axes match the depth camera's (x right, y down,
 * z forward), so a level camera reads about (0, -9.8, 0). Samples whose magnitude is far from
 * 1 g are jolts from walking, not gravity, and are skipped.
 *
 * Gyroscope (complementary filter): when gyro samples arrive via [addGyro], the up vector is
 * rotated with the head immediately, and the accelerometer only slowly corrects the gyro's
 * drift ([Params.gravityAlphaWithGyro]). Without a gyro, the accelerometer alone is low-pass
 * filtered ([Params.gravityAlpha]), which lags behind fast head tilts.
 */
class GravityFilter(private val p: Params = Params()) {
    private var v: DoubleArray? = null
    private var lastGyroMs = Double.NaN
    private var gyroSeen = false
    var samples = 0
        private set

    /** Adds one accelerometer sample (m/s^2, camera frame). Returns false if it was rejected. */
    fun add(ax: Double, ay: Double, az: Double): Boolean {
        val norm = sqrt(ax * ax + ay * ay + az * az)
        if (norm < p.gravityMinNorm || norm > p.gravityMaxNorm) return false
        val cur = v
        val next = if (cur == null) doubleArrayOf(ax / norm, ay / norm, az / norm)
        else {
            val k = if (gyroSeen && samples >= p.gravityMinSamples) p.gravityAlphaWithGyro else p.gravityAlpha
            doubleArrayOf(
                (1 - k) * cur[0] + k * ax / norm,
                (1 - k) * cur[1] + k * ay / norm,
                (1 - k) * cur[2] + k * az / norm,
            )
        }
        v = normalize(next)
        samples++
        return true
    }

    /**
     * Adds one gyroscope sample (rad/s, camera frame): rotates the up vector by the head's
     * rotation since the previous gyro sample.
     */
    fun addGyro(wx: Double, wy: Double, wz: Double, timestampMs: Double) {
        val prev = lastGyroMs
        lastGyroMs = timestampMs
        gyroSeen = true
        val cur = v ?: return
        if (prev.isNaN()) return
        val dt = ((timestampMs - prev) / 1000.0).coerceIn(0.0, 0.02)
        if (dt <= 0) return
        // The camera turned by R (previous <- current); a fixed world vector in the new camera
        // frame is R^T times its old coordinates.
        val r = M3.exp(wx * dt, wy * dt, wz * dt)
        v = normalize(M3.apply(M3.transpose(r), cur[0], cur[1], cur[2]))
    }

    /** Current unit up vector, or null until enough samples have arrived. */
    fun up(): DoubleArray? = if (samples >= p.gravityMinSamples) v?.copyOf() else null

    fun reset() { v = null; samples = 0; lastGyroMs = Double.NaN; gyroSeen = false }

    private fun normalize(a: DoubleArray): DoubleArray {
        val len = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2])
        return doubleArrayOf(a[0] / len, a[1] / len, a[2] / len)
    }
}

/**
 * Floor detection when the IMU gives the "up" direction: the floor's tilt is known, so only
 * its height is searched for. Much more robust than RANSAC when little floor is visible
 * (standing close to a wall, crowded rooms).
 *
 * Every point's distance below the camera along "up" is histogrammed; the floor is the level
 * with the best score of points on it minus points clearly below it (the same "nothing solid
 * below the floor" rule as RANSAC). The nearest points are tried first, as in [fitGroundPlane].
 * Returns null if the IMU vector is implausible or no floor is visible.
 */
fun fitGroundPlaneWithGravity(pts: PointCloud, up: DoubleArray, p: Params): Plane? {
    val len = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
    if (len < 1e-6) return null
    val n = doubleArrayOf(up[0] / len, up[1] / len, up[2] / len)
    if (-n[1] < cos(Math.toRadians(p.maxTiltDeg))) return null  // camera pointing at the sky/floor

    horizontallyNear(pts, n, p)?.let { near -> levelFit(near, n, p)?.let { return refineWithin(pts, it, n, p) } }
    return levelFit(pts, n, p)?.let { refineWithin(pts, it, n, p) }
}

/**
 * The points closest to the user measured along the floor (not along the camera axis, where
 * a wall in front can be "nearer" than the floor at its base).
 */
private fun horizontallyNear(pts: PointCloud, n: DoubleArray, p: Params): PointCloud? {
    if (pts.size < 200) return null
    val dist = FloatArray(pts.size) { i ->
        val x = pts.x(i); val y = pts.y(i); val z = pts.z(i)
        val along = n[0] * x + n[1] * y + n[2] * z
        sqrt(maxOf(0.0, (x * x + y * y + z * z).toDouble() - along * along)).toFloat()
    }
    val cut = dist.copyOf().also { it.sort() }[(p.nearQuantile * (pts.size - 1)).toInt()]
    return pts.subset { dist[it] <= cut }
}

private const val BIN = 0.01  // histogram resolution, metres

/** Best floor level (camera height d) for a fixed normal [n], via a height histogram. */
private fun levelFit(pts: PointCloud, n: DoubleArray, p: Params): Plane? {
    if (pts.size < 50) return null
    val hi = p.maxCameraHeight + 1.0
    val bins = (hi / BIN).toInt() + 1
    val hist = IntArray(bins)
    var beyond = 0  // further below the camera than the histogram covers
    for (i in 0 until pts.size) {
        val t = -(n[0] * pts.x(i) + n[1] * pts.y(i) + n[2] * pts.z(i))  // depth below camera along up
        if (t < 0) continue
        val b = (t / BIN).toInt()
        if (b >= bins) beyond++ else hist[b]++
    }
    // suffix[b] = points at or below bin b
    val suffix = IntArray(bins + 1)
    suffix[bins] = beyond
    for (b in bins - 1 downTo 0) suffix[b] = suffix[b + 1] + hist[b]

    val band = (p.ransacThresh / BIN).toInt()
    val gap = (3 * p.ransacThresh / BIN).toInt()
    var bestScore = Int.MIN_VALUE
    var bestBin = -1
    var bestOn = 0
    val lo = (p.minCameraHeight / BIN).toInt()
    val top = minOf((p.maxCameraHeight / BIN).toInt(), bins - 1)
    for (b in lo..top) {
        val on = suffix[maxOf(0, b - band)] - suffix[minOf(bins, b + band + 1)]
        val below = suffix[minOf(bins, b + gap + 1)]
        val score = on - below
        if (score > bestScore) { bestScore = score; bestBin = b; bestOn = on }
    }
    if (bestBin < 0 || bestScore < p.imuMinInlierRatio * pts.size) return null

    // Mean level of the inliers.
    val d0 = (bestBin + 0.5) * BIN
    var sum = 0.0
    var cnt = 0
    for (i in 0 until pts.size) {
        val t = -(n[0] * pts.x(i) + n[1] * pts.y(i) + n[2] * pts.z(i))
        if (abs(t - d0) < p.ransacThresh) { sum += t; cnt++ }
    }
    if (cnt == 0 || bestOn == 0) return null
    return Plane(n[0], n[1], n[2], sum / cnt)
}

/**
 * Least-squares refinement of the plane on its inliers, accepted only if it stays within
 * [Params.imuMaxDeviationDeg] of the IMU's up. This absorbs small IMU-to-depth misalignment
 * and residual walking acceleration without letting a table top tilt the floor.
 */
private fun refineWithin(pts: PointCloud, pl: Plane, n: DoubleArray, p: Params): Plane {
    val r = refine(pts, pl, p.ransacThresh) ?: return pl
    val cosA = (r.nx * n[0] + r.ny * n[1] + r.nz * n[2]).coerceIn(-1.0, 1.0)
    return if (Math.toDegrees(acos(cosA)) <= p.imuMaxDeviationDeg) r else pl
}
