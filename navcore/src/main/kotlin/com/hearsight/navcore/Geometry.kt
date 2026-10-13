package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One depth frame in metres (0 = no data), with pinhole intrinsics.
 * Camera frame (RealSense convention): x right, y down, z forward.
 */
class DepthImage(
    val width: Int,
    val height: Int,
    val depth: FloatArray,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
)

/** Packed xyz points: point i is (xyz[3i], xyz[3i+1], xyz[3i+2]). */
class PointCloud(val xyz: FloatArray, val size: Int) {
    fun x(i: Int) = xyz[3 * i]
    fun y(i: Int) = xyz[3 * i + 1]
    fun z(i: Int) = xyz[3 * i + 2]

    fun subset(keep: (Int) -> Boolean): PointCloud {
        val out = FloatArray(xyz.size)
        var n = 0
        for (i in 0 until size) if (keep(i)) {
            out[3 * n] = x(i); out[3 * n + 1] = y(i); out[3 * n + 2] = z(i); n++
        }
        return PointCloud(out, n)
    }
}

/**
 * Floor plane n . p + d = 0 with n pointing up (towards the camera), so d is the camera
 * height and [height] is a point's height above the floor.
 */
data class Plane(val nx: Double, val ny: Double, val nz: Double, val d: Double) {
    fun height(x: Float, y: Float, z: Float) = nx * x + ny * y + nz * z + d
    val cameraHeight get() = d
    /** How far the camera looks down (positive) relative to the floor. */
    val pitchDeg get() = Math.toDegrees(asin((-nz).coerceIn(-1.0, 1.0)))
    fun dot(o: Plane) = nx * o.nx + ny * o.ny + nz * o.nz
}

fun deproject(img: DepthImage, p: Params): PointCloud {
    val s = p.stride
    val out = FloatArray(3 * ((img.width + s - 1) / s) * ((img.height + s - 1) / s))
    var n = 0
    for (v in 0 until img.height step s) for (u in 0 until img.width step s) {
        val z = img.depth[v * img.width + u]
        if (z <= p.minDepth || z >= p.maxDepth) continue
        out[3 * n] = (u - img.cx) / img.fx * z
        out[3 * n + 1] = (v - img.cy) / img.fy * z
        out[3 * n + 2] = z
        n++
    }
    return PointCloud(out, n)
}

/**
 * Find the floor the user stands on.
 *
 * Fit on the nearest points first: further away the floor may continue at another level
 * (step, curb, ramp) and one plane would bridge both. Fall back to all points if the near
 * region has no floor (e.g. something right in front).
 */
fun fitGroundPlane(pts: PointCloud, p: Params, prior: Plane?, rnd: Random = Random(0)): Plane? {
    if (pts.size >= 200) {
        val zs = FloatArray(pts.size) { pts.z(it) }.also { it.sort() }
        val zCut = zs[(p.nearQuantile * (pts.size - 1)).toInt()]
        val near = ransac(pts.subset { pts.z(it) <= zCut }, p, prior, rnd)
        if (near != null && near !== prior) return near
    }
    return ransac(pts, p, prior, rnd)
}

private fun ransac(pts: PointCloud, p: Params, prior: Plane?, rnd: Random): Plane? {
    if (pts.size < 50) return prior
    val sample = if (pts.size <= p.ransacSample) IntArray(pts.size) { it }
    else IntArray(p.ransacSample) { rnd.nextInt(pts.size) }
    val minUp = cos(Math.toRadians(p.maxTiltDeg))
    val belowThresh = -3 * p.ransacThresh

    // Score = points on the plane minus points clearly below it. Nothing solid can be below
    // the floor, so this rejects table / box tops and planes tilted through the floor.
    fun score(pl: Plane): Int {
        var on = 0
        var below = 0
        for (i in sample) {
            val h = pl.height(pts.x(i), pts.y(i), pts.z(i))
            if (abs(h) < p.ransacThresh) on++ else if (h < belowThresh) below++
        }
        return on - below
    }

    var best: Plane? = null
    var bestScore = Int.MIN_VALUE
    if (prior != null) { best = prior; bestScore = score(prior) }
    repeat(p.ransacIters) {
        val a = sample[rnd.nextInt(sample.size)]
        val b = sample[rnd.nextInt(sample.size)]
        val c = sample[rnd.nextInt(sample.size)]
        val cand = planeFrom3(pts, a, b, c) ?: return@repeat
        // Floor normal must be roughly "up" (-y) and the camera at a plausible height.
        if (-cand.ny < minUp || cand.d < p.minCameraHeight || cand.d > p.maxCameraHeight) return@repeat
        val sc = score(cand)
        if (sc > bestScore) { best = cand; bestScore = sc }
    }
    if (best == null || bestScore < p.minInlierRatio * sample.size) return prior
    return refine(pts, best!!, p.ransacThresh) ?: best
}

private fun planeFrom3(pts: PointCloud, a: Int, b: Int, c: Int): Plane? {
    val ux = (pts.x(b) - pts.x(a)).toDouble(); val uy = (pts.y(b) - pts.y(a)).toDouble(); val uz = (pts.z(b) - pts.z(a)).toDouble()
    val vx = (pts.x(c) - pts.x(a)).toDouble(); val vy = (pts.y(c) - pts.y(a)).toDouble(); val vz = (pts.z(c) - pts.z(a)).toDouble()
    val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
    val len = sqrt(nx * nx + ny * ny + nz * nz)
    if (len < 1e-9) return null
    return oriented(nx / len, ny / len, nz / len, pts.x(a).toDouble(), pts.y(a).toDouble(), pts.z(a).toDouble())
}

/** Plane through point (px,py,pz) with normal n, flipped so d >= 0 (normal towards the camera). */
private fun oriented(nx: Double, ny: Double, nz: Double, px: Double, py: Double, pz: Double): Plane {
    val d = -(nx * px + ny * py + nz * pz)
    return if (d < 0) Plane(-nx, -ny, -nz, -d) else Plane(nx, ny, nz, d)
}

/** Least-squares plane through the inliers (smallest eigenvector of their covariance). */
private fun refine(pts: PointCloud, plane: Plane, thresh: Double): Plane? {
    var n = 0
    var mx = 0.0; var my = 0.0; var mz = 0.0
    for (i in 0 until pts.size) if (abs(plane.height(pts.x(i), pts.y(i), pts.z(i))) < thresh) {
        mx += pts.x(i); my += pts.y(i); mz += pts.z(i); n++
    }
    if (n < 30) return null
    mx /= n; my /= n; mz /= n
    val cov = DoubleArray(9)
    for (i in 0 until pts.size) if (abs(plane.height(pts.x(i), pts.y(i), pts.z(i))) < thresh) {
        val dx = pts.x(i) - mx; val dy = pts.y(i) - my; val dz = pts.z(i) - mz
        cov[0] += dx * dx; cov[1] += dx * dy; cov[2] += dx * dz
        cov[4] += dy * dy; cov[5] += dy * dz; cov[8] += dz * dz
    }
    cov[3] = cov[1]; cov[6] = cov[2]; cov[7] = cov[5]
    val nrm = smallestEigenvector(cov)
    val out = oriented(nrm[0], nrm[1], nrm[2], mx, my, mz)
    return if (-out.ny > 0) out else null
}

/** Jacobi eigen-decomposition of a symmetric 3x3 matrix; returns the eigenvector of the smallest eigenvalue. */
internal fun smallestEigenvector(m: DoubleArray): DoubleArray {
    val a = m.copyOf()
    val v = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    repeat(50) {
        var pi = 0; var qi = 1
        var max = abs(a[1])
        if (abs(a[2]) > max) { max = abs(a[2]); pi = 0; qi = 2 }
        if (abs(a[5]) > max) { max = abs(a[5]); pi = 1; qi = 2 }
        if (max < 1e-12) return@repeat
        val app = a[pi * 3 + pi]; val aqq = a[qi * 3 + qi]; val apq = a[pi * 3 + qi]
        val theta = 0.5 * Math.atan2(2 * apq, aqq - app)
        val c = cos(theta); val s = Math.sin(theta)
        for (k in 0 until 3) { // A = A * J
            val akp = a[k * 3 + pi]; val akq = a[k * 3 + qi]
            a[k * 3 + pi] = c * akp - s * akq
            a[k * 3 + qi] = s * akp + c * akq
        }
        for (k in 0 until 3) { // A = J^T * A
            val apk = a[pi * 3 + k]; val aqk = a[qi * 3 + k]
            a[pi * 3 + k] = c * apk - s * aqk
            a[qi * 3 + k] = s * apk + c * aqk
        }
        for (k in 0 until 3) { // V = V * J
            val vkp = v[k * 3 + pi]; val vkq = v[k * 3 + qi]
            v[k * 3 + pi] = c * vkp - s * vkq
            v[k * 3 + qi] = s * vkp + c * vkq
        }
    }
    var idx = 0
    if (a[4] < a[idx * 4]) idx = 1
    if (a[8] < a[idx * 4]) idx = 2
    return doubleArrayOf(v[idx], v[3 + idx], v[6 + idx])
}

/**
 * Smooths the floor estimate over time. Small changes are blended in; a large jump (e.g. a
 * table top briefly winning RANSAC) is only accepted once it has persisted for a few frames.
 */
class PlaneTracker(private val p: Params) {
    var plane: Plane? = null
        private set
    private var pending: Plane? = null
    private var pendingFrames = 0

    private fun close(a: Plane, b: Plane) =
        Math.toDegrees(Math.acos(a.dot(b).coerceIn(-1.0, 1.0))) <= p.planeJumpDeg &&
            abs(a.d - b.d) <= p.planeJumpHeight

    fun update(new: Plane?): Plane? {
        val cur = plane
        when {
            new == null -> Unit
            cur == null -> plane = new
            close(cur, new) -> {
                val k = p.planeBlend
                var nx = (1 - k) * cur.nx + k * new.nx
                var ny = (1 - k) * cur.ny + k * new.ny
                var nz = (1 - k) * cur.nz + k * new.nz
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= len; ny /= len; nz /= len
                plane = Plane(nx, ny, nz, (1 - k) * cur.d + k * new.d)
                pending = null
            }
            else -> {
                val pend = pending
                if (pend != null && close(pend, new)) pendingFrames++ else { pending = new; pendingFrames = 1 }
                if (pendingFrames >= p.planeJumpFrames) { plane = new; pending = null }
            }
        }
        return plane
    }
}

/** Unit forward and right axes lying in the floor plane, aligned with where the camera looks. */
fun groundFrame(pl: Plane): Pair<DoubleArray, DoubleArray> {
    // forward = camera z projected onto the plane
    var fx = -pl.nz * pl.nx; var fy = -pl.nz * pl.ny; var fz = 1.0 - pl.nz * pl.nz
    val len = sqrt(fx * fx + fy * fy + fz * fz)
    fx /= len; fy /= len; fz /= len
    // right = forward x up
    val rx = fy * pl.nz - fz * pl.ny
    val ry = fz * pl.nx - fx * pl.nz
    val rz = fx * pl.ny - fy * pl.nx
    return doubleArrayOf(fx, fy, fz) to doubleArrayOf(rx, ry, rz)
}
