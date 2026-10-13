package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

enum class Tracking {
    /** First frame: the world frame is set here. */
    INIT,
    /** Depth alignment constrained all six degrees of freedom. */
    OK,
    /** Geometry can't see motion in some direction (e.g. a bare corridor); that part comes from the prediction. */
    DEGENERATE,
    /** Alignment failed: gyro-only rotation this frame, reference frame reset. */
    LOST,
}

class OdometryResult(
    /** world <- camera. World: X right, Y forward (both as at start), Z up when gravity is known. */
    val pose: Rigid,
    val tracking: Tracking,
    val inliers: Int,
    /** Head heading relative to the start, degrees, + = turned right. */
    val headingDeg: Double,
    /** Total distance travelled, metres. */
    val distance: Double,
)

/**
 * Depth-inertial odometry: the gyroscope predicts each frame's rotation, then point-to-plane
 * ICP aligns the depth frame against a keyframe to refine it and measure translation.
 *
 * - Projective data association: current points are projected into the keyframe's depth
 *   image, so no nearest-neighbour search is needed (fast enough for a phone).
 * - Huber-weighted Gauss-Newton on the 6-DoF twist.
 * - Degeneracy-aware: directions the geometry can't constrain (eigenvalues of the normal
 *   equations below a threshold) are not updated, so they keep the gyro / constant-velocity
 *   prediction instead of drifting. Classic case: walking along a bare corridor.
 * - Frame-to-keyframe alignment limits drift; a new keyframe is taken after moving
 *   [Params.odomKeyframeDist] or turning [Params.odomKeyframeDeg].
 *
 * Coordinates follow the RealSense camera frame (x right, y down, z forward). Gyro deltas
 * come in as [DepthImage.gyroDelta]: the current camera's rotation expressed in the previous
 * camera's frame.
 */
class DepthInertialOdometry(private val p: Params = Params()) {
    private var key: VertexMaps? = null
    private var worldFromKey = Rigid.IDENTITY
    private var keyFromPrev = Rigid.IDENTITY
    private var velocity = DoubleArray(3)  // last frame's translation, previous <- current
    private var distance = 0.0
    var pose: Rigid = Rigid.IDENTITY
        private set

    fun reset() {
        key = null
        keyFromPrev = Rigid.IDENTITY
        velocity = DoubleArray(3)
        distance = 0.0
        pose = Rigid.IDENTITY
    }

    /**
     * [upHint]: "up" in camera coordinates to use for the world frame when the camera has no
     * IMU (e.g. the floor normal). Only used on the first frame.
     */
    fun update(img: DepthImage, upHint: DoubleArray? = null): OdometryResult {
        val maps = VertexMaps.build(img, p.odomStride, p.odomMaxDepth)
        val k = key
        if (k == null) {
            worldFromKey = Rigid(gravityAlignedRotation(img.gravityUp ?: upHint), DoubleArray(3))
            key = maps
            keyFromPrev = Rigid.IDENTITY
            pose = worldFromKey
            return result(Tracking.INIT, 0)
        }

        val gyro = img.gyroDelta ?: M3.identity()
        val predicted = Rigid(gyro, velocity.copyOf())       // previous <- current
        val guess = keyFromPrev.compose(predicted)            // keyframe <- current
        val icp = align(k, maps, guess, hasGyro = img.gyroDelta != null)
            ?.takeIf { plausible(it.transform, gyro, img.gyroDelta != null) }

        val keyFromCur: Rigid
        val tracking: Tracking
        if (icp == null) {
            // Lost: trust the gyro for rotation, assume no translation, restart from here.
            keyFromCur = keyFromPrev.compose(Rigid(gyro, DoubleArray(3)))
            tracking = Tracking.LOST
        } else {
            keyFromCur = icp.transform
            tracking = if (icp.degenerateDims > 0) Tracking.DEGENERATE else Tracking.OK
        }

        val prevFromCur = keyFromPrev.inverse().compose(keyFromCur)
        velocity = if (tracking == Tracking.LOST) DoubleArray(3) else prevFromCur.t.copyOf()
        distance += prevFromCur.translationNorm
        pose = worldFromKey.compose(keyFromCur).normalized()

        if (tracking == Tracking.LOST ||
            keyFromCur.translationNorm > p.odomKeyframeDist ||
            keyFromCur.rotationDeg > p.odomKeyframeDeg
        ) {
            worldFromKey = pose
            key = maps
            keyFromPrev = Rigid.IDENTITY
        } else {
            keyFromPrev = keyFromCur
        }
        return result(tracking, icp?.inliers ?: 0)
    }

    private fun result(tracking: Tracking, inliers: Int): OdometryResult {
        val f = M3.apply(pose.r, 0.0, 0.0, 1.0)  // camera forward in world
        return OdometryResult(pose, tracking, inliers, Math.toDegrees(atan2(f[0], f[1])), distance)
    }

    private class Icp(val transform: Rigid, val inliers: Int, val degenerateDims: Int)

    /**
     * Reject physically impossible results instead of reporting them as motion: a walking
     * person moves less than [Params.odomMaxStep] per frame, and the frame's rotation must
     * agree with the gyro (when there is one) within [Params.odomMaxGyroDisagreeDeg].
     */
    private fun plausible(keyFromCur: Rigid, gyro: DoubleArray, hasGyro: Boolean): Boolean {
        val step = keyFromPrev.inverse().compose(keyFromCur)
        if (step.translationNorm > p.odomMaxStep) return false
        if (hasGyro && M3.angleDeg(M3.mul(M3.transpose(gyro), step.r)) > p.odomMaxGyroDisagreeDeg) return false
        return true
    }

    /**
     * Point-to-plane ICP of [cur] onto [key], starting from [guess] (key <- cur).
     * With a gyro, its rotation is also a soft prior: facing a single wall, a small turn and
     * a sideways step look alike to depth, and the prior stops ICP trading one for the other.
     */
    private fun align(key: VertexMaps, cur: VertexMaps, guess: Rigid, hasGyro: Boolean): Icp? {
        val guessRt = M3.transpose(guess.r)
        var t = guess
        val cosNormal = cos(Math.toRadians(p.odomNormalDeg))
        val h = DoubleArray(36)
        val b = DoubleArray(6)
        var inliers = 0
        var sumSq = 0.0
        var degenerate = 0
        var sources = 0

        for (iter in 0 until p.odomIters) {
            h.fill(0.0); b.fill(0.0)
            inliers = 0; sumSq = 0.0; sources = 0
            val gate = maxOf(p.odomGateMin, p.odomGateStart * Math.pow(0.7, iter.toDouble()))
            val r = t.r; val tt = t.t

            for (j in 1 until cur.h - 1 step p.odomSourceStep) for (i in 1 until cur.w - 1 step p.odomSourceStep) {
                val s = j * cur.w + i
                if (!cur.valid[s]) continue
                sources++
                val px = cur.v[3 * s].toDouble(); val py = cur.v[3 * s + 1].toDouble(); val pz = cur.v[3 * s + 2].toDouble()
                // transform into the keyframe
                val qx = r[0] * px + r[1] * py + r[2] * pz + tt[0]
                val qy = r[3] * px + r[4] * py + r[5] * pz + tt[1]
                val qz = r[6] * px + r[7] * py + r[8] * pz + tt[2]
                if (qz <= 0.1) continue
                val u = (key.fx * qx / qz + key.cx + 0.5).toInt()
                val v = (key.fy * qy / qz + key.cy + 0.5).toInt()
                if (u < 0 || u >= key.w || v < 0 || v >= key.h) continue
                val d = v * key.w + u
                if (!key.valid[d]) continue

                val nx = key.n[3 * d].toDouble(); val ny = key.n[3 * d + 1].toDouble(); val nz = key.n[3 * d + 2].toDouble()
                // normal compatibility (source normal rotated into the keyframe)
                val sx = cur.n[3 * s].toDouble(); val sy = cur.n[3 * s + 1].toDouble(); val sz = cur.n[3 * s + 2].toDouble()
                val rnx = r[0] * sx + r[1] * sy + r[2] * sz
                val rny = r[3] * sx + r[4] * sy + r[5] * sz
                val rnz = r[6] * sx + r[7] * sy + r[8] * sz
                if (rnx * nx + rny * ny + rnz * nz < cosNormal) continue

                val ex = qx - key.v[3 * d]; val ey = qy - key.v[3 * d + 1]; val ez = qz - key.v[3 * d + 2]
                if (ex * ex + ey * ey + ez * ez > gate * gate) continue
                val res = nx * ex + ny * ey + nz * ez
                val w = if (abs(res) < p.odomHuber) 1.0 else p.odomHuber / abs(res)

                // d(res)/d(twist): rotation part q x n, translation part n
                val j0 = qy * nz - qz * ny
                val j1 = qz * nx - qx * nz
                val j2 = qx * ny - qy * nx
                val jac = doubleArrayOf(j0, j1, j2, nx, ny, nz)
                for (a in 0 until 6) {
                    b[a] += w * jac[a] * res
                    for (c in a until 6) h[a * 6 + c] += w * jac[a] * jac[c]
                }
                inliers++
                sumSq += res * res
            }
            for (a in 0 until 6) for (c in 0 until a) h[a * 6 + c] = h[c * 6 + a]

            if (inliers < maxOf(p.odomMinInliers, (p.odomMinInlierRatio * sources).toInt())) return null

            if (hasGyro) {
                // residual = rotation away from the gyro prediction (left perturbation: phi + omega)
                val phi = M3.log(M3.mul(t.r, guessRt))
                val lambda = p.odomGyroWeight * inliers
                for (a in 0 until 3) { h[a * 6 + a] += lambda; b[a] += lambda * phi[a] }
            }

            // Solve H x = -b only in the well-constrained eigen-directions.
            val (vals, vecs) = symmetricEigen(h, 6)
            val minEig = (if (hasGyro) p.odomDegenerateRatio else p.odomDegenerateRatioNoGyro) * inliers
            val x = DoubleArray(6)
            degenerate = 0
            for (e in 0 until 6) {
                if (vals[e] < minEig) { degenerate++; continue }
                var proj = 0.0
                for (a in 0 until 6) proj += vecs[a * 6 + e] * b[a]
                val coef = -proj / vals[e]
                for (a in 0 until 6) x[a] += coef * vecs[a * 6 + e]
            }
            val step = Rigid(M3.exp(x[0], x[1], x[2]), doubleArrayOf(x[3], x[4], x[5]))
            t = step.compose(t)
            if (sqrt(x.sumOf { it * it }) < 1e-5) break
        }
        if (sqrt(sumSq / inliers) > p.odomMaxRmse) return null
        return Icp(t.normalized(), inliers, degenerate)
    }
}

/**
 * Rotation world <- camera so that world Z is "up" and world Y is the camera's forward
 * direction projected onto the floor. Without gravity, assumes a level camera.
 */
internal fun gravityAlignedRotation(up: DoubleArray?): DoubleArray {
    if (up == null) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, -1.0, 0.0)
    val l = sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2])
    val z = doubleArrayOf(up[0] / l, up[1] / l, up[2] / l)
    var y = doubleArrayOf(-z[2] * z[0], -z[2] * z[1], 1.0 - z[2] * z[2])  // camera z minus its up part
    val ly = sqrt(y[0] * y[0] + y[1] * y[1] + y[2] * y[2])
    y = doubleArrayOf(y[0] / ly, y[1] / ly, y[2] / ly)
    val x = doubleArrayOf(y[1] * z[2] - y[2] * z[1], y[2] * z[0] - y[0] * z[2], y[0] * z[1] - y[1] * z[0])
    // rows = world axes expressed in camera coordinates
    return doubleArrayOf(x[0], x[1], x[2], y[0], y[1], y[2], z[0], z[1], z[2])
}

/** Organised vertex + normal maps of one depth frame at reduced resolution. */
internal class VertexMaps(
    val w: Int,
    val h: Int,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val v: FloatArray,
    val n: FloatArray,
    val valid: BooleanArray,
) {
    companion object {
        private const val NORMAL_SPAN = 3

        fun build(img: DepthImage, stride: Int, maxDepth: Float): VertexMaps {
            val w = img.width / stride
            val h = img.height / stride
            val fx = img.fx / stride.toDouble(); val fy = img.fy / stride.toDouble()
            val cx = img.cx / stride.toDouble(); val cy = img.cy / stride.toDouble()
            val v = FloatArray(3 * w * h)
            val has = BooleanArray(w * h)
            for (j in 0 until h) for (i in 0 until w) {
                val z = img.depth[(j * stride) * img.width + i * stride]
                if (z <= 0.2f || z > maxDepth) continue
                val k = j * w + i
                v[3 * k] = ((i - cx) / fx * z).toFloat()
                v[3 * k + 1] = ((j - cy) / fy * z).toFloat()
                v[3 * k + 2] = z
                has[k] = true
            }
            // Normals from neighbours [NORMAL_SPAN] pixels away: at 2-3 m the D455's depth noise
            // makes adjacent-pixel normals far too noisy, which biases point-to-plane ICP.
            val n = FloatArray(3 * w * h)
            val valid = BooleanArray(w * h)
            val k = NORMAL_SPAN
            for (j in k until h - k) for (i in k until w - k) {
                val c = j * w + i
                val l = c - k; val r = c + k; val u = c - k * w; val d = c + k * w
                if (!has[c] || !has[l] || !has[r] || !has[u] || !has[d]) continue
                val z = v[3 * c + 2]
                val jump = 0.05f + 0.05f * z * k
                if (abs(v[3 * l + 2] - v[3 * c + 2]) > jump || abs(v[3 * r + 2] - v[3 * c + 2]) > jump ||
                    abs(v[3 * u + 2] - v[3 * c + 2]) > jump || abs(v[3 * d + 2] - v[3 * c + 2]) > jump
                ) continue
                val ax = v[3 * r] - v[3 * l]; val ay = v[3 * r + 1] - v[3 * l + 1]; val az = v[3 * r + 2] - v[3 * l + 2]
                val bx = v[3 * d] - v[3 * u]; val by = v[3 * d + 1] - v[3 * u + 1]; val bz = v[3 * d + 2] - v[3 * u + 2]
                var nx = ay * bz - az * by; var ny = az * bx - ax * bz; var nz = ax * by - ay * bx
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len < 1e-9f) continue
                nx /= len; ny /= len; nz /= len
                // face the camera
                if (nx * v[3 * c] + ny * v[3 * c + 1] + nz * v[3 * c + 2] > 0) { nx = -nx; ny = -ny; nz = -nz }
                n[3 * c] = nx; n[3 * c + 1] = ny; n[3 * c + 2] = nz
                valid[c] = true
            }
            return VertexMaps(w, h, fx, fy, cx, cy, v, n, valid)
        }
    }
}

/**
 * Integrates gyroscope samples (rad/s, camera axes) into the rotation between depth frames.
 * Call [add] for every gyro sample and [take] when a depth frame arrives.
 */
class GyroIntegrator {
    private var r = M3.identity()
    private var lastTimestampMs = Double.NaN
    var samples = 0
        private set

    fun add(wx: Double, wy: Double, wz: Double, timestampMs: Double) {
        if (!lastTimestampMs.isNaN()) {
            val dt = ((timestampMs - lastTimestampMs) / 1000.0).coerceIn(0.0, 0.02)
            if (dt > 0) r = M3.mul(r, M3.exp(wx * dt, wy * dt, wz * dt))
        }
        lastTimestampMs = timestampMs
        samples++
    }

    /** Rotation of the current camera in the previous frame's camera axes, since the last call. */
    fun take(): DoubleArray? {
        if (samples == 0) return null
        val out = M3.orthonormalize(r)
        r = M3.identity()
        samples = 0
        return out
    }

    fun reset() {
        r = M3.identity()
        lastTimestampMs = Double.NaN
        samples = 0
    }
}
