package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 3x3 row-major rotation-matrix helpers. */
internal object M3 {
    fun identity() = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

    fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i ->
        val r = i / 3; val c = i % 3
        a[3 * r] * b[c] + a[3 * r + 1] * b[3 + c] + a[3 * r + 2] * b[6 + c]
    }

    fun transpose(a: DoubleArray) = doubleArrayOf(a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8])

    fun apply(a: DoubleArray, x: Double, y: Double, z: Double) = doubleArrayOf(
        a[0] * x + a[1] * y + a[2] * z,
        a[3] * x + a[4] * y + a[5] * z,
        a[6] * x + a[7] * y + a[8] * z,
    )

    /** Rotation by the vector w (axis * angle in radians), Rodrigues' formula. */
    fun exp(wx: Double, wy: Double, wz: Double): DoubleArray {
        val th = sqrt(wx * wx + wy * wy + wz * wz)
        if (th < 1e-12) return identity()
        val kx = wx / th; val ky = wy / th; val kz = wz / th
        val c = cos(th); val s = sin(th); val v = 1 - c
        return doubleArrayOf(
            c + kx * kx * v, kx * ky * v - kz * s, kx * kz * v + ky * s,
            ky * kx * v + kz * s, c + ky * ky * v, ky * kz * v - kx * s,
            kz * kx * v - ky * s, kz * ky * v + kx * s, c + kz * kz * v,
        )
    }

    /** Inverse of [exp]: rotation vector (axis * angle) of a rotation matrix. */
    fun log(a: DoubleArray): DoubleArray {
        val cosT = ((a[0] + a[4] + a[8] - 1) / 2).coerceIn(-1.0, 1.0)
        val th = acos(cosT)
        val vx = a[7] - a[5]; val vy = a[2] - a[6]; val vz = a[3] - a[1]
        if (th < 1e-9) return doubleArrayOf(vx / 2, vy / 2, vz / 2)
        val k = th / (2 * sin(th))
        return doubleArrayOf(k * vx, k * vy, k * vz)
    }

    fun angleDeg(a: DoubleArray) = Math.toDegrees(acos(((a[0] + a[4] + a[8] - 1) / 2).coerceIn(-1.0, 1.0)))

    /** Re-orthonormalise to stop numerical drift after many compositions. */
    fun orthonormalize(a: DoubleArray): DoubleArray {
        var x = doubleArrayOf(a[0], a[3], a[6])
        x = norm(x)
        var y = doubleArrayOf(a[1], a[4], a[7])
        val d = x[0] * y[0] + x[1] * y[1] + x[2] * y[2]
        y = norm(doubleArrayOf(y[0] - d * x[0], y[1] - d * x[1], y[2] - d * x[2]))
        val z = doubleArrayOf(x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0])
        return doubleArrayOf(x[0], y[0], z[0], x[1], y[1], z[1], x[2], y[2], z[2])
    }

    private fun norm(v: DoubleArray): DoubleArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}

/** Rigid transform p' = R p + t (R row-major 3x3). */
class Rigid(val r: DoubleArray, val t: DoubleArray) {
    /** this * o: apply o first, then this. */
    fun compose(o: Rigid): Rigid {
        val rt = M3.apply(r, o.t[0], o.t[1], o.t[2])
        return Rigid(M3.mul(r, o.r), doubleArrayOf(rt[0] + t[0], rt[1] + t[1], rt[2] + t[2]))
    }

    fun inverse(): Rigid {
        val rt = M3.transpose(r)
        val ti = M3.apply(rt, t[0], t[1], t[2])
        return Rigid(rt, doubleArrayOf(-ti[0], -ti[1], -ti[2]))
    }

    fun apply(x: Double, y: Double, z: Double): DoubleArray {
        val p = M3.apply(r, x, y, z)
        return doubleArrayOf(p[0] + t[0], p[1] + t[1], p[2] + t[2])
    }

    val translationNorm get() = sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2])
    val rotationDeg get() = M3.angleDeg(r)

    fun normalized() = Rigid(M3.orthonormalize(r), t.copyOf())

    companion object {
        val IDENTITY = Rigid(M3.identity(), DoubleArray(3))
    }
}

/**
 * Eigen-decomposition of a symmetric n x n matrix (row-major) by cyclic Jacobi rotations.
 * Returns eigenvalues and eigenvectors (eigenvector k is column k of the returned matrix).
 */
internal fun symmetricEigen(m: DoubleArray, n: Int): Pair<DoubleArray, DoubleArray> {
    val a = m.copyOf()
    val v = DoubleArray(n * n).also { for (i in 0 until n) it[i * n + i] = 1.0 }
    repeat(60) {
        var off = 0.0
        for (p in 0 until n) for (q in p + 1 until n) off += a[p * n + q] * a[p * n + q]
        if (off < 1e-24) return@repeat
        for (p in 0 until n) for (q in p + 1 until n) {
            val apq = a[p * n + q]
            if (abs(apq) < 1e-300) continue
            val theta = 0.5 * Math.atan2(2 * apq, a[q * n + q] - a[p * n + p])
            val c = cos(theta); val s = sin(theta)
            for (k in 0 until n) {
                val akp = a[k * n + p]; val akq = a[k * n + q]
                a[k * n + p] = c * akp - s * akq
                a[k * n + q] = s * akp + c * akq
            }
            for (k in 0 until n) {
                val apk = a[p * n + k]; val aqk = a[q * n + k]
                a[p * n + k] = c * apk - s * aqk
                a[q * n + k] = s * apk + c * aqk
            }
            for (k in 0 until n) {
                val vkp = v[k * n + p]; val vkq = v[k * n + q]
                v[k * n + p] = c * vkp - s * vkq
                v[k * n + q] = s * vkp + c * vkq
            }
        }
    }
    return DoubleArray(n) { a[it * n + it] } to v
}
