package com.hearsight.navcore

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionTest {
    /** world <- camera for a camera pitched down by [pitch] radians (no yaw). */
    private fun cameraRotation(pitch: Double) = doubleArrayOf(
        1.0, 0.0, 0.0,
        0.0, -sin(pitch), cos(pitch),
        0.0, -cos(pitch), -sin(pitch),
    )

    private fun angleDeg(a: DoubleArray, b: DoubleArray) =
        Math.toDegrees(acos((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1.0, 1.0)))

    /**
     * Head tilts from 20 to 40 degrees down in 0.5 s while walking (vertical bounce).
     * Returns the final up-vector error in degrees.
     */
    private fun tilt(useGyro: Boolean, gyroBias: Double = 0.0, holdSeconds: Double = 0.0): Double {
        val f = GravityFilter()
        val rnd = Random(3)
        val g = 9.81
        var prevR: DoubleArray? = null
        val total = 1.0 + 0.5 + holdSeconds
        var t = 0.0
        var nextAccel = 0.0
        val dt = 1.0 / 200  // gyro rate
        var pitch = 0.0
        while (t < total) {
            pitch = Math.toRadians(
                when {
                    t < 1.0 -> 20.0
                    t < 1.5 -> 20.0 + 20.0 * (t - 1.0) / 0.5
                    else -> 40.0
                },
            )
            val r = cameraRotation(pitch)
            if (useGyro) {
                prevR?.let { p ->
                    val w = M3.log(M3.mul(M3.transpose(p), r))
                    f.addGyro(w[0] / dt + gyroBias, w[1] / dt, w[2] / dt, t * 1000)
                }
                if (prevR == null) f.addGyro(0.0, 0.0, 0.0, t * 1000)
            }
            prevR = r
            if (t >= nextAccel) {
                // specific force: gravity reaction plus a 2 Hz walking bounce, in camera axes
                val bounce = 2.5 * sin(2 * PI * 2 * t) + 0.3 * rnd.nextDouble(-1.0, 1.0)
                val up = doubleArrayOf(r[6], r[7], r[8])  // world Z in camera coordinates
                f.add((g + bounce) * up[0], (g + bounce) * up[1], (g + bounce) * up[2])
                nextAccel += 1.0 / 63
            }
            t += dt
        }
        val r = cameraRotation(pitch)
        return angleDeg(f.up()!!, doubleArrayOf(r[6], r[7], r[8]))
    }

    @Test
    fun gyroRemovesGravityLagDuringHeadTilts() {
        val accelOnly = tilt(useGyro = false)
        val fused = tilt(useGyro = true)
        println("up error right after a 20 deg head tilt: accel only %.1f deg, gyro + accel %.2f deg".format(accelOnly, fused))
        assertTrue(fused < 1.0, "fused error $fused")
        assertTrue(accelOnly > 3.0, "accel-only should lag: $accelOnly")
    }

    @Test
    fun accelerometerCorrectsGyroDrift() {
        // A biased gyro (0.6 deg/s) for 20 s would drift 12 degrees on its own.
        val err = tilt(useGyro = true, gyroBias = Math.toRadians(0.6), holdSeconds = 20.0)
        assertTrue(err < 2.0, "drift not corrected: $err")
    }

    @Test
    fun countsStepsWhileWalkingAndNotWhileStanding() {
        val rnd = Random(5)
        val up = doubleArrayOf(0.0, -1.0, 0.0)
        val walking = StepDetector()
        val standing = StepDetector()
        var t = 0.0
        while (t < 10.0) {
            val bounce = 2.5 * sin(2 * PI * 1.8 * t) + 0.3 * rnd.nextDouble(-1.0, 1.0)
            walking.add(0.0, -(9.81 + bounce), 0.0, t * 1000, up)
            standing.add(0.0, -(9.81 + 0.3 * rnd.nextDouble(-1.0, 1.0)), 0.0, t * 1000, up)
            t += 1.0 / 63
        }
        assertEquals(18.0, walking.steps.toDouble(), 1.0)
        assertEquals(0, standing.steps)
    }

    @Test
    fun walkingSpeedFromCadenceAndTimeout() {
        val w = WalkingSpeed()
        for (i in 0..8) w.update(i * 250.0, i / 2)  // a step every 0.5 s
        assertEquals(2 * 0.65, w.speed(2000.0), 0.15)
        w.update(3600.0, 4)
        assertEquals(0.0, w.speed(3600.0), 1e-9)   // no step for 1.6 s: standing
    }

    @Test
    fun stepLengthIsLearnedFromOdometry() {
        val w = WalkingSpeed()
        w.interrupt(0)
        for (s in 1..16) w.calibrate(0.75, s)
        assertTrue(w.stepLength > 0.7, "step length ${w.stepLength}")
    }

    private val corridor = listOf(Box(-1.2, -1.0, -1.0, 60.0, 2.5), Box(1.0, 1.2, -1.0, 60.0, 2.5))

    private fun corridorWalk(stepCounter: Boolean): Double {
        val cam = SimCamera(
            Scene(boxes = corridor, walkSpeed = 0.03, imu = true, loopAt = 100.0, stepCounter = stepCounter),
            width = 424, height = 240, noise = 0.004,
        )
        val odo = DepthInertialOdometry()
        var first: Rigid? = null
        var last = Rigid.IDENTITY
        repeat(150) {
            val r = odo.update(cam.read())
            if (first == null) first = r.pose
            last = r.pose
        }
        return first!!.inverse().compose(last).translationNorm
    }

    @Test
    fun stepCounterMeasuresDistanceInABareCorridor() {
        val truth = 149 * 0.03
        val withSteps = corridorWalk(true)
        val without = corridorWalk(false)
        println("bare corridor, %.2f m walked: odometry says %.2f m with step counter, %.2f m without".format(truth, withSteps, without))
        assertTrue(without < 0.2 * truth)
        assertTrue(withSteps > 0.8 * truth && withSteps < 1.1 * truth, "with steps: $withSteps of $truth")
    }

    @Test
    fun roomWalkTeachesTheStepLength() {
        val room = listOf(
            Box(-2.2, -2.0, -1.0, 14.0, 2.5), Box(2.0, 2.2, -1.0, 14.0, 2.5),
            Box(-1.2, -0.9, 2.0, 2.3, 1.8), Box(0.9, 1.3, 3.5, 3.9, 1.2),
            Box(-1.5, -1.0, 5.0, 5.5, 0.8), Box(0.5, 1.0, 6.5, 7.0, 1.5), Box(-1.4, -1.0, 8.5, 9.0, 1.4),
        )
        val cam = SimCamera(
            Scene(boxes = room, walkSpeed = 0.04, imu = true, loopAt = 100.0, stepCounter = true, stepLength = 0.5),
            width = 424, height = 240, noise = 0.004,
        )
        val odo = DepthInertialOdometry()
        var r: OdometryResult? = null
        repeat(150) { r = odo.update(cam.read()) }
        val learned = r!!.stepLength!!
        println("learned step length %.3f m (true 0.50, default 0.65)".format(learned))
        assertTrue(learned < 0.62, "should move towards 0.5: $learned")
    }
}
