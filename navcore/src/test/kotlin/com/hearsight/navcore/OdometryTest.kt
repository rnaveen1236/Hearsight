package com.hearsight.navcore

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OdometryTest {
    /** A room with walls and furniture: enough geometry to constrain all six degrees of freedom. */
    private val room = listOf(
        Box(-2.2, -2.0, -1.0, 12.0, 2.5),  // left wall
        Box(2.0, 2.2, -1.0, 12.0, 2.5),    // right wall
        Box(-1.2, -0.9, 2.0, 2.3, 1.8),
        Box(0.9, 1.3, 3.5, 3.9, 1.2),
        Box(-1.5, -1.0, 5.0, 5.5, 0.8),
        Box(0.5, 1.0, 6.5, 7.0, 1.5),
    )

    private class Run(val trueRel: Rigid, val estRel: Rigid, val states: List<Tracking>, val msPerFrame: Double)

    private fun walk(scene: Scene, frames: Int, params: Params = Params(), dropGyro: Boolean = false): Run {
        val cam = SimCamera(scene, width = 424, height = 240, noise = 0.004)
        val odo = DepthInertialOdometry(params)
        var true0: Rigid? = null
        var est0: Rigid? = null
        var trueN = Rigid.IDENTITY
        var estN = Rigid.IDENTITY
        val states = ArrayList<Tracking>()
        var nanos = 0L
        repeat(frames) {
            val truth = cam.truePose()
            var img = cam.read()
            if (dropGyro) img = DepthImage(img.width, img.height, img.depth, img.fx, img.fy, img.cx, img.cy, img.gravityUp, null)
            val t0 = System.nanoTime()
            val r = odo.update(img)
            nanos += System.nanoTime() - t0
            states += r.tracking
            if (true0 == null) { true0 = truth; est0 = r.pose }
            trueN = truth; estN = r.pose
        }
        return Run(true0!!.inverse().compose(trueN), est0!!.inverse().compose(estN), states, nanos / 1e6 / frames)
    }

    private fun Rigid.dist(o: Rigid) = Math.sqrt((0 until 3).sumOf { (t[it] - o.t[it]) * (t[it] - o.t[it]) })
    private fun Rigid.angleTo(o: Rigid) = inverse().compose(o).rotationDeg

    @Test
    fun tracksWalkingAndTurningInARoom() {
        // 1.8 m forward while turning 24 degrees right.
        val run = walk(Scene(boxes = room, walkSpeed = 0.03, yawRateDeg = 0.4, imu = true, loopAt = 100.0), 60)
        val err = run.trueRel.dist(run.estRel)
        println("room: travelled %.2f m, position error %.3f m, rotation error %.2f deg, %.1f ms/frame"
            .format(run.trueRel.translationNorm, err, run.trueRel.angleTo(run.estRel), run.msPerFrame))
        assertTrue(err < 0.05 * run.trueRel.translationNorm, "position error $err")
        assertTrue(run.trueRel.angleTo(run.estRel) < 1.5)
        assertTrue(Tracking.LOST !in run.states)
    }

    @Test
    fun noisyGyroIsCorrectedByDepth() {
        val run = walk(
            Scene(boxes = room, walkSpeed = 0.03, yawRateDeg = 0.4, imu = true, gyroNoiseDeg = 0.3, loopAt = 100.0), 60,
        )
        assertTrue(run.trueRel.angleTo(run.estRel) < 2.0, "rotation error ${run.trueRel.angleTo(run.estRel)}")
        assertTrue(run.trueRel.dist(run.estRel) < 0.1)
    }

    @Test
    fun worksWithoutGyro() {
        val run = walk(Scene(boxes = room, walkSpeed = 0.03, yawRateDeg = 0.4, imu = true, loopAt = 100.0), 60, dropGyro = true)
        assertTrue(run.trueRel.dist(run.estRel) < 0.1, "position error ${run.trueRel.dist(run.estRel)}")
        assertTrue(run.trueRel.angleTo(run.estRel) < 2.0)
    }

    @Test
    fun fastHeadTurnIsTrackedWithGyro() {
        // 60 deg/s head turn while walking slowly: 88 degrees in 1.5 s.
        val run = walk(Scene(boxes = room, walkSpeed = 0.02, yawRateDeg = 2.0, imu = true, loopAt = 100.0), 45)
        assertTrue(run.trueRel.angleTo(run.estRel) < 1.5, "rotation error ${run.trueRel.angleTo(run.estRel)}")
        assertTrue(run.trueRel.dist(run.estRel) < 0.15, "position error ${run.trueRel.dist(run.estRel)}")
        assertTrue(Tracking.LOST !in run.states, "states ${run.states.groupingBy { it }.eachCount()}")
    }

    @Test
    fun failedAlignmentIsReportedAsLostNotAsMotion() {
        // Same fast turn without a gyro: some frames can't be aligned. They must be flagged,
        // and no single frame may jump further than a person can walk.
        val cam = SimCamera(
            Scene(boxes = room, walkSpeed = 0.02, yawRateDeg = 2.0, imu = true, loopAt = 100.0),
            width = 424, height = 240, noise = 0.008,
        )
        val odo = DepthInertialOdometry()
        var last: Rigid? = null
        val states = ArrayList<Tracking>()
        repeat(45) {
            val img = cam.read()
            val r = odo.update(DepthImage(img.width, img.height, img.depth, img.fx, img.fy, img.cx, img.cy, img.gravityUp, null))
            states += r.tracking
            last?.let { assertTrue(it.dist(r.pose) <= Params().odomMaxStep + 1e-9, "jumped ${it.dist(r.pose)} m") }
            last = r.pose
        }
        assertTrue(Tracking.LOST in states)
    }

    @Test
    fun bareCorridorIsFlaggedAndDoesNotInventMotion() {
        // Floor + two flat walls: forward motion is invisible to depth alignment.
        val corridor = listOf(Box(-1.2, -1.0, -1.0, 60.0, 2.5), Box(1.0, 1.2, -1.0, 60.0, 2.5))
        val run = walk(Scene(boxes = corridor, walkSpeed = 0.03, imu = true, loopAt = 100.0), 40)
        assertTrue(Tracking.DEGENERATE in run.states, "corridor should be reported as degenerate")
        // camera frame: x right, y down, z forward
        val est = run.estRel.t
        assertTrue(abs(est[0]) < 0.05, "sideways drift ${est[0]}")
        assertTrue(est[2] < run.trueRel.t[2] + 0.05, "must not overestimate forward motion: ${est[2]}")
        assertTrue(run.trueRel.angleTo(run.estRel) < 1.0)
    }

    @Test
    fun coveredCameraFallsBackToGyro() {
        val cam = SimCamera(Scene(boxes = room, yawRateDeg = 1.0, imu = true), width = 212, height = 120)
        val odo = DepthInertialOdometry()
        odo.update(cam.read())
        val img = cam.read()
        val blind = DepthImage(img.width, img.height, FloatArray(img.depth.size), img.fx, img.fy, img.cx, img.cy, img.gravityUp, img.gyroDelta)
        val r = odo.update(blind)
        assertEquals(Tracking.LOST, r.tracking)
        assertEquals(1.0, r.headingDeg, 0.05)  // rotation still follows the gyro
    }

    @Test
    fun headingStartsAtZeroAndIsGravityAligned() {
        val cam = SimCamera(Scene(boxes = room, imu = true, pitchDeg = 30.0))
        val r = DepthInertialOdometry().update(cam.read())
        assertEquals(Tracking.INIT, r.tracking)
        assertEquals(0.0, r.headingDeg, 1e-6)
        // camera forward should point 30 degrees below the horizon in the world frame
        val f = M3.apply(r.pose.r, 0.0, 0.0, 1.0)
        assertEquals(-0.5, f[2], 1e-6)
    }

    @Test
    fun pipelineTrailShowsWhereTheUserCameFrom() {
        val cam = SimCamera(Scene(boxes = room, walkSpeed = 0.03, imu = true, loopAt = 100.0), width = 424, height = 240)
        val pipe = Pipeline()
        var res: StepResult? = null
        repeat(31) { res = pipe.step(cam.read()) }
        val trail = res!!.trail
        assertTrue(trail.size >= 10)
        // walked 0.9 m straight: the oldest trail point is ~0.9 m behind, facing forward
        assertEquals(-0.9, trail.first().at.fwd, 0.05)
        assertEquals(0.0, trail.first().at.lat, 0.05)
        assertEquals(0.0, trail.first().headingDeg, 1.0)
        assertEquals(0.0, trail.last().at.fwd, 0.05)
    }

    @Test
    fun gyroIntegratorAccumulatesRotation() {
        val g = GyroIntegrator()
        // 90 deg/s about the camera y axis (down) for 0.5 s at 200 Hz
        for (i in 0..100) g.add(0.0, Math.PI / 2, 0.0, i * 5.0)
        val r = g.take()!!
        assertEquals(45.0, M3.angleDeg(r), 0.01)
        assertEquals(null, g.take())
    }

    @Test
    fun symmetricEigenSolvesA6x6() {
        val m = DoubleArray(36)
        for (i in 0 until 6) for (j in 0 until 6) m[i * 6 + j] = if (i == j) (i + 1).toDouble() else 0.1
        val (vals, vecs) = symmetricEigen(m, 6)
        for (e in 0 until 6) for (i in 0 until 6) {
            val mv = (0 until 6).sumOf { m[i * 6 + it] * vecs[it * 6 + e] }
            assertEquals(vals[e] * vecs[i * 6 + e], mv, 1e-9)
        }
    }
}
