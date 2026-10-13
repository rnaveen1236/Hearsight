package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

enum class Command { STRAIGHT, LEFT, RIGHT, STOP }

/** What the user should feel / hear for this frame. */
data class Cue(
    val command: Command,
    val angleDeg: Double = 0.0,   // + = right
    val urgency: Double = 1.0,    // 0..1, how sharp the turn is
    val say: String? = null,      // speak this (already rate-limited)
)

/** Turns a plan into user cues: direction + urgency for haptics/beeps, and sparse speech. */
class Guidance(private val p: Params, private val clock: () -> Double = { System.nanoTime() / 1e9 }) {
    private var angle = 0.0
    private var command = Command.STRAIGHT
    private val lastSaid = HashMap<String, Double>()

    private fun say(text: String, now: Double): String? {
        if (now - (lastSaid[text] ?: Double.NEGATIVE_INFINITY) < p.speakInterval) return null
        lastSaid[text] = now
        return text
    }

    /** Distance to the nearest cell of [mask] in the walking corridor straight ahead, or null. */
    private fun nearestAhead(g: Grid, mask: BooleanArray): Double? {
        val half = ceil(p.corridorHalfWidth / g.cell).toInt()
        val mid = g.cols / 2
        for (r in 0 until g.rows) for (c in maxOf(0, mid - half)..min(g.cols - 1, mid + half)) {
            if (mask[g.idx(r, c)]) return g.fwdOf(r)
        }
        return null
    }

    fun update(plan: Plan, grid: Grid?): Cue {
        val now = clock()
        if (grid == null) {
            command = Command.STOP
            return Cue(Command.STOP, say = say("Stop. Can't see the floor.", now))
        }
        if (!plan.reachable) {
            command = Command.STOP
            return Cue(Command.STOP, say = say("Stop. Path blocked.", now))
        }

        angle = p.angleEma * plan.angleDeg + (1 - p.angleEma) * angle
        val limit = p.deadbandDeg + if (command == Command.STRAIGHT) p.hysteresisDeg else 0.0
        val cmd = when {
            abs(angle) < limit -> Command.STRAIGHT
            angle > 0 -> Command.RIGHT
            else -> Command.LEFT
        }

        var spoken: String? = null
        val drop = nearestAhead(grid, grid.dropoff)
        val obstacle = nearestAhead(grid, grid.obstacle)
        val person = nearestAhead(grid, BooleanArray(grid.obstacle.size) { grid.obstacle[it] && grid.objectAt(it) == Surface.PERSON })
        val vehicle = nearestAhead(grid, BooleanArray(grid.obstacle.size) { grid.obstacle[it] && grid.objectAt(it) == Surface.VEHICLE })
        val road = nearestAhead(grid, BooleanArray(grid.ground.size) { grid.groundAt(it) == Surface.ROAD })
        val pathOnRoad = plan.path.any { pt ->
            val r = grid.rowOf(pt.fwd); val c = grid.colOf(pt.lat)
            r in 0 until grid.rows && c in 0 until grid.cols && grid.groundAt(grid.idx(r, c)) == Surface.ROAD
        }
        if (drop != null && drop < p.warnDist) {
            spoken = say("Step down ahead", now)
        } else if (person != null && person < p.warnDist) {
            spoken = say("Person ahead", now)
        } else if (vehicle != null && vehicle < p.warnDist * 1.5) {
            spoken = say("Vehicle ahead", now)
        } else if (pathOnRoad) {
            spoken = say("Caution, path crosses road", now)
        } else if (road != null && road < p.roadWarnDist) {
            spoken = say("Road ahead", now)
        } else if (obstacle != null && obstacle < p.warnDist && cmd == Command.STRAIGHT) {
            spoken = say(if (obstacle >= 1.0) "Obstacle, %.1f meters".format(obstacle) else "Obstacle close", now)
        }
        if (spoken == null && cmd != command && cmd != Command.STRAIGHT) {
            val dir = if (cmd == Command.RIGHT) "right" else "left"
            spoken = say(
                when {
                    abs(angle) > p.sharpTurnDeg -> "Turn $dir"
                    abs(angle) < 20 -> "Slight $dir"
                    else -> dir.replaceFirstChar { it.uppercase() }
                }, now,
            )
        }
        command = cmd
        return Cue(cmd, angle, min(1.0, abs(angle) / p.sharpTurnDeg), spoken)
    }
}
