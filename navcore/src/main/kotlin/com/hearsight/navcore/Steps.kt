package com.hearsight.navcore

import kotlin.math.sqrt

/**
 * Counts walking steps from the accelerometer (pedestrian dead reckoning).
 *
 * Each step makes the body bounce: the acceleration along "up" rises above gravity at heel
 * strike. The vertical component is high-passed (slow running mean removed), low-passed
 * (about 3 Hz), and each rise above [Params.stepThreshold] counts as one step, with at least
 * [Params.stepMinIntervalMs] between steps and re-arming once it falls back below zero.
 */
class StepDetector(private val p: Params = Params()) {
    var steps = 0
        private set
    private var mean = Double.NaN
    private var smooth = 0.0
    private var armed = true
    private var lastStepMs = Double.NEGATIVE_INFINITY

    /**
     * Adds one accelerometer sample (m/s^2, camera frame) at [timestampMs]. [up] is the unit up
     * vector (from [GravityFilter]); without it the magnitude is used.
     */
    fun add(ax: Double, ay: Double, az: Double, timestampMs: Double, up: DoubleArray?) {
        val vertical = if (up != null) ax * up[0] + ay * up[1] + az * up[2] else sqrt(ax * ax + ay * ay + az * az)
        if (mean.isNaN()) mean = vertical
        mean += 0.02 * (vertical - mean)              // ~1 s running mean at 63 Hz
        smooth += 0.3 * ((vertical - mean) - smooth)  // ~3 Hz low-pass
        if (armed && smooth > p.stepThreshold && timestampMs - lastStepMs > p.stepMinIntervalMs) {
            steps++
            lastStepMs = timestampMs
            armed = false
        } else if (smooth < 0) {
            armed = true
        }
    }

    fun reset() {
        steps = 0; mean = Double.NaN; smooth = 0.0; armed = true; lastStepMs = Double.NEGATIVE_INFINITY
    }
}

/**
 * Turns a step count into walking speed, learning the user's step length from the depth
 * odometry whenever it can see forward motion (tracking OK, not degenerate).
 */
class WalkingSpeed(private val p: Params = Params()) {
    /** Current step length estimate, metres. */
    var stepLength = p.defaultStepLength
        private set
    private val stepTimes = ArrayDeque<Double>()  // ms of recent step events
    private var lastCount = -1
    private var calibDistance = 0.0
    private var calibSteps = 0
    private var lastSteps = -1

    /** Records the cumulative step count at [timestampMs]. */
    fun update(timestampMs: Double, steps: Int) {
        if (lastCount >= 0 && steps > lastCount) repeat(steps - lastCount) { stepTimes.addLast(timestampMs) }
        lastCount = steps
        while (stepTimes.isNotEmpty() && timestampMs - stepTimes.first() > p.cadenceWindowMs) stepTimes.removeFirst()
    }

    /**
     * Speed in m/s: cadence from the intervals between recent steps x step length; a typical
     * cadence until two steps are known; 0 if no step for [Params.stepTimeoutMs].
     */
    fun speed(nowMs: Double): Double {
        if (stepTimes.isEmpty() || nowMs - stepTimes.last() > p.stepTimeoutMs) return 0.0
        val cadence = if (stepTimes.size >= 2) {
            (stepTimes.size - 1) / ((stepTimes.last() - stepTimes.first()) / 1000.0).coerceAtLeast(0.25)
        } else {
            p.typicalCadenceHz
        }
        return cadence.coerceAtMost(3.0) * stepLength
    }

    /**
     * Calibration: [horizontal] metres measured by depth odometry while [steps] (cumulative)
     * were counted. Call only for frames where depth fully constrained the motion.
     */
    fun calibrate(horizontal: Double, steps: Int) {
        if (lastSteps >= 0) {
            calibDistance += horizontal
            calibSteps += steps - lastSteps
            if (calibSteps >= p.stepCalibrationSteps) {
                val measured = (calibDistance / calibSteps).coerceIn(0.35, 1.0)
                stepLength += 0.5 * (measured - stepLength)
                calibDistance = 0.0
                calibSteps = 0
            }
        }
        lastSteps = steps
    }

    /** Breaks the calibration window (e.g. a degenerate or lost frame in between). */
    fun interrupt(steps: Int) {
        calibDistance = 0.0
        calibSteps = 0
        lastSteps = steps
    }
}
