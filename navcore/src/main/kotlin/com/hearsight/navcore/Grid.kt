package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min

/**
 * Egocentric 2.5D cost grid on the floor plane.
 * Row 0 is at the user's feet, rows go forward; columns go left -> right.
 */
class Grid(
    val rows: Int,
    val cols: Int,
    val cell: Float,
    val halfWidth: Float,
    val cost: FloatArray,          // Float.POSITIVE_INFINITY = not walkable
    val obstacle: BooleanArray,
    val dropoff: BooleanArray,
    val floor: BooleanArray,       // observed walkable floor
    val unknown: BooleanArray,     // never observed
    val clearance: FloatArray,     // metres to the nearest obstacle / drop-off
    /** Ground type per cell ([Surface] codes: WALKWAY / ROAD / TERRAIN / UNKNOWN). */
    val ground: ByteArray = ByteArray(rows * cols),
    /** Object type per obstacle cell ([Surface] codes: PERSON / VEHICLE / STRUCTURE / UNKNOWN). */
    val objectType: ByteArray = ByteArray(rows * cols),
) {
    fun groundAt(i: Int) = Surface.of(ground[i])
    fun objectAt(i: Int) = Surface.of(objectType[i])

    fun idx(r: Int, c: Int) = r * cols + c
    fun fwdOf(r: Int) = (r + 0.5) * cell
    fun latOf(c: Int) = (c + 0.5) * cell - halfWidth
    fun rowOf(fwd: Double) = floor(fwd / cell).toInt()
    fun colOf(lat: Double) = floor((lat + halfWidth) / cell).toInt()
    val startRow get() = 0
    val startCol get() = cols / 2
}

/** Grid from a single frame; [sem] optionally labels the points (same frame). */
fun buildGrid(pts: PointCloud, plane: Plane, p: Params, sem: SemanticImage? = null): Grid {
    val rows = Math.round(p.rangeFwd / p.cell)
    val cols = Math.round(2 * p.halfWidth / p.cell)
    val n = rows * cols
    val floorC = IntArray(n); val obsC = IntArray(n); val dropC = IntArray(n)
    val classC = if (sem != null) IntArray(n * Surface.COUNT) else null
    val (fa, ra) = groundFrame(plane)

    for (i in 0 until pts.size) {
        val x = pts.x(i); val y = pts.y(i); val z = pts.z(i)
        val f = fa[0] * x + fa[1] * y + fa[2] * z
        val r = ra[0] * x + ra[1] * y + ra[2] * z
        val ri = floor(f / p.cell).toInt()
        val ci = floor((r + p.halfWidth) / p.cell).toInt()
        if (ri < 0 || ri >= rows || ci < 0 || ci >= cols) continue
        val h = plane.height(x, y, z)
        val noise = p.noiseK * f * f
        val k = ri * cols + ci
        val label = if (sem != null) pts.label(i, sem) else Surface.UNKNOWN
        when {
            abs(h) < p.floorTol + noise -> {
                floorC[k]++
                if (classC != null && label.isGround()) classC[k * Surface.COUNT + label.ordinal]++
            }
            h > p.obstacleMinH + noise && h < p.obstacleMaxH -> {
                obsC[k]++
                if (classC != null && label.isObject()) classC[k * Surface.COUNT + label.ordinal]++
            }
            h < -(p.dropMinH + noise) -> dropC[k]++
        }
    }

    val obstacle = BooleanArray(n) { obsC[it] >= p.minPoints }
    // A cell full of floor with a few stray points below it is not a hole.
    val dropoff = BooleanArray(n) { dropC[it] >= p.minPoints && dropC[it] > floorC[it] && !obstacle[it] }
    val floor = BooleanArray(n) { floorC[it] > 0 && !obstacle[it] && !dropoff[it] }
    val unknown = BooleanArray(n) { floorC[it] + obsC[it] + dropC[it] == 0 }
    val ground = ByteArray(n)
    val objectType = ByteArray(n)
    if (classC != null) for (k in 0 until n) {
        ground[k] = majority(classC, k, p.semMinPoints.toFloat()) { it.isGround() }
        if (obstacle[k]) objectType[k] = majority(classC, k, p.semMinPoints.toFloat()) { it.isObject() }
    }
    return finishGrid(rows, cols, p, obstacle, dropoff, floor, unknown, ground, objectType)
}

/** Most frequent class at cell [k] among those passing [filter], if it has at least [min] votes. */
internal fun majority(counts: IntArray, k: Int, min: Float, filter: (Surface) -> Boolean): Byte {
    var best = Surface.UNKNOWN
    var bestN = 0
    for (s in Surface.values()) if (filter(s)) {
        val c = counts[k * Surface.COUNT + s.ordinal]
        if (c > bestN) { bestN = c; best = s }
    }
    return if (bestN >= min) best.code else Surface.UNKNOWN.code
}

/** Blind-zone handling, clearance and costs, shared by the per-frame grid and [FusedMap]. */
internal fun finishGrid(
    rows: Int,
    cols: Int,
    p: Params,
    obstacle: BooleanArray,
    dropoff: BooleanArray,
    floor: BooleanArray,
    unknown: BooleanArray,
    ground: ByteArray = ByteArray(rows * cols),
    objectType: ByteArray = ByteArray(rows * cols),
): Grid {
    val n = rows * cols

    // The camera can't see the floor right at the user's feet. Treat the rows before the
    // first observation as free (the user has just walked over them) instead of unknown,
    // otherwise the planner sidesteps through that band.
    val firstSeen = (0 until rows).firstOrNull { r -> (0 until cols).any { c -> !unknown[r * cols + c] } } ?: rows
    for (r in 0 until min(firstSeen + 1, rows)) for (c in 0 until cols) unknown[r * cols + c] = false

    val blocked = BooleanArray(n) { obstacle[it] || dropoff[it] }
    val clearance = distanceTransform(blocked, rows, cols).also { d -> for (i in d.indices) d[i] *= p.cell }

    // People and vehicles move and swerve: keep a wider berth than around walls.
    val personDist = distanceTransform(BooleanArray(n) { obstacle[it] && objectType[it] == Surface.PERSON.code }, rows, cols)
    val vehicleDist = distanceTransform(BooleanArray(n) { obstacle[it] && objectType[it] == Surface.VEHICLE.code }, rows, cols)

    val cost = FloatArray(n) {
        val cl = clearance[it]
        if (cl < p.bodyRadius || personDist[it] * p.cell < p.personRadius || vehicleDist[it] * p.cell < p.vehicleRadius) {
            Float.POSITIVE_INFINITY
        } else {
            p.inflationWeight * exp(-(cl - p.bodyRadius) / p.inflationScale) +
                (if (unknown[it]) p.unknownCost else 0f) +
                when (ground[it]) {
                    Surface.ROAD.code -> p.roadCost
                    Surface.TERRAIN.code -> p.terrainCost
                    else -> 0f
                }
        }
    }
    return Grid(rows, cols, p.cell, p.halfWidth, cost, obstacle, dropoff, floor, unknown, clearance, ground, objectType)
}

/** Two-pass chamfer distance (in cells) to the nearest blocked cell; 99 if none. */
internal fun distanceTransform(blocked: BooleanArray, rows: Int, cols: Int): FloatArray {
    val inf = 1e6f
    val d = FloatArray(rows * cols) { if (blocked[it]) 0f else inf }
    val a = 1f; val b = 1.4142135f
    for (r in 0 until rows) for (c in 0 until cols) {
        val i = r * cols + c
        var v = d[i]
        if (c > 0) v = min(v, d[i - 1] + a)
        if (r > 0) {
            v = min(v, d[i - cols] + a)
            if (c > 0) v = min(v, d[i - cols - 1] + b)
            if (c < cols - 1) v = min(v, d[i - cols + 1] + b)
        }
        d[i] = v
    }
    for (r in rows - 1 downTo 0) for (c in cols - 1 downTo 0) {
        val i = r * cols + c
        var v = d[i]
        if (c < cols - 1) v = min(v, d[i + 1] + a)
        if (r < rows - 1) {
            v = min(v, d[i + cols] + a)
            if (c < cols - 1) v = min(v, d[i + cols + 1] + b)
            if (c > 0) v = min(v, d[i + cols - 1] + b)
        }
        d[i] = v
    }
    for (i in d.indices) if (d[i] >= inf) d[i] = 99f
    return d
}
