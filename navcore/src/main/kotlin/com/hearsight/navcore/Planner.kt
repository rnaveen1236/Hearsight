package com.hearsight.navcore

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** A point on the floor, metres: fwd ahead of the user, lat to the right. */
data class Pt(val fwd: Double, val lat: Double)

class Plan(
    val path: List<Pt> = emptyList(),  // smoothed, starts at the user's feet
    val target: Pt? = null,            // pure-pursuit lookahead point
    val angleDeg: Double = 0.0,        // + = turn right
) {
    val reachable get() = target != null
}

private val MOVES = listOf(
    intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1),
    intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1),
)

/**
 * There is no destination yet, so the goal is "as far ahead as possible, close to where the
 * user is facing, through the safest space" - the green path in the dotLumen demo.
 */
fun plan(g: Grid, p: Params, previousGoal: Pt? = null): Plan {
    val n = g.rows * g.cols
    val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
    val parent = IntArray(n) { -1 }
    val start = g.idx(g.startRow, g.startCol)
    dist[start] = 0.0
    val heap = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
    heap.add(0.0 to start)
    while (heap.isNotEmpty()) {
        val (d, i) = heap.poll()
        if (d > dist[i]) continue
        val r = i / g.cols; val c = i % g.cols
        for (m in MOVES) {
            val nr = r + m[0]; val nc = c + m[1]
            if (nr !in 0 until g.rows || nc !in 0 until g.cols) continue
            val j = g.idx(nr, nc)
            val w = g.cost[j]
            if (w.isInfinite()) continue
            val step = if (m[0] != 0 && m[1] != 0) 1.4142135 else 1.0
            val nd = d + step * (1.0 + w)
            if (nd < dist[j]) { dist[j] = nd; parent[j] = i; heap.add(nd to j) }
        }
    }

    var best = -1
    var bestScore = Double.NEGATIVE_INFINITY
    for (i in 0 until n) {
        if (i == start || dist[i].isInfinite()) continue
        val lat = g.latOf(i % g.cols)
        var score = g.fwdOf(i / g.cols) - p.lateralPenalty * abs(lat) - p.costWeight * dist[i]
        // Stick with the side chosen last frame, so a centred obstacle doesn't make the
        // route flip left/right with depth noise.
        if (previousGoal != null) score -= p.goalCommitment * abs(lat - previousGoal.lat)
        if (score > bestScore) { bestScore = score; best = i }
    }
    if (best < 0 || hypot(g.fwdOf(best / g.cols), g.latOf(best % g.cols)) < p.minGoalDist) return Plan()

    val cells = ArrayList<Int>()
    var i = best
    while (i != -1) { cells.add(i); i = parent[i] }
    cells.reverse()

    val raw = ArrayList<Pt>()
    raw.add(Pt(0.0, 0.0))
    for (k in 1 until cells.size) raw.add(Pt(g.fwdOf(cells[k] / g.cols), g.latOf(cells[k] % g.cols)))
    val path = smooth(stringPull(g, raw), p.smoothWindow)

    val target = path.firstOrNull { hypot(it.fwd, it.lat) >= p.lookahead } ?: path.last()
    return Plan(path, target, Math.toDegrees(atan2(target.lat, target.fwd)))
}

/**
 * Grid paths zig-zag in 45 degree steps. Replace them with straight segments wherever the
 * straight line is no more costly than the grid path it replaces, then resample per cell.
 */
private fun stringPull(g: Grid, pts: List<Pt>): List<Pt> {
    if (pts.size < 3) return pts
    fun cellCost(pt: Pt): Float {
        val r = g.rowOf(pt.fwd); val c = g.colOf(pt.lat)
        if (r !in 0 until g.rows || c !in 0 until g.cols) return Float.POSITIVE_INFINITY
        return if (r == 0) 0f else g.cost[g.idx(r, c)]
    }
    fun segmentMax(a: Pt, b: Pt): Float {
        val steps = maxOf(1, (hypot(b.fwd - a.fwd, b.lat - a.lat) / (g.cell * 0.5)).toInt())
        var m = 0f
        for (k in 0..steps) {
            val t = k.toDouble() / steps
            m = maxOf(m, cellCost(Pt(a.fwd + t * (b.fwd - a.fwd), a.lat + t * (b.lat - a.lat))))
        }
        return m
    }
    val keep = ArrayList<Pt>()
    var anchor = 0
    keep.add(pts[0])
    while (anchor < pts.size - 1) {
        var best = anchor + 1
        var worst = cellCost(pts[anchor + 1])
        for (j in anchor + 1 until pts.size) {
            worst = maxOf(worst, cellCost(pts[j]))
            if (segmentMax(pts[anchor], pts[j]) <= worst + 0.05f) best = j
        }
        keep.add(pts[best])
        anchor = best
    }
    val out = ArrayList<Pt>()
    for (k in 0 until keep.size - 1) {
        val a = keep[k]; val b = keep[k + 1]
        val steps = maxOf(1, (hypot(b.fwd - a.fwd, b.lat - a.lat) / g.cell).toInt())
        for (s in 0 until steps) {
            val t = s.toDouble() / steps
            out.add(Pt(a.fwd + t * (b.fwd - a.fwd), a.lat + t * (b.lat - a.lat)))
        }
    }
    out.add(keep.last())
    return out
}

private fun smooth(pts: List<Pt>, window: Int): List<Pt> {
    if (pts.size < 3 || window < 2) return pts
    val k = window / 2
    return pts.indices.map { i ->
        if (i == 0 || i == pts.size - 1) pts[i]
        else {
            val lo = maxOf(0, i - k); val hi = minOf(pts.size - 1, i + k)
            val sub = pts.subList(lo, hi + 1)
            Pt(sub.sumOf { it.fwd } / sub.size, sub.sumOf { it.lat } / sub.size)
        }
    }
}
