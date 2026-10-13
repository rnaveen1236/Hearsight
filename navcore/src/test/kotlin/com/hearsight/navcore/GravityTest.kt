package com.hearsight.navcore

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GravityTest {
    private val p = Params()

    private fun imuFit(scene: Scene, noise: Double = 0.01, seed: Int = 0): Plane? {
        val img = SimCamera(scene.copy(imu = true), noise = noise, seed = seed).read()
        return fitGroundPlaneWithGravity(deproject(img, p), img.gravityUp!!, p)
    }

    @Test
    fun recoversHeightAndPitchWithKnownGravity() {
        for (seed in 0 until 4) {
            val pl = assertNotNull(imuFit(Scene(height = 1.45, pitchDeg = 35.0), seed = seed))
            assertEquals(1.45, pl.cameraHeight, 0.03)
            assertEquals(35.0, pl.pitchDeg, 1.0)
        }
    }

    @Test
    fun findsFloorWhenAWallLeavesLittleFloorVisible() {
        // Depth-only RANSAC can't find a floor here; with gravity only the height is unknown.
        val scene = Scene(boxes = listOf(Box(-3.0, 3.0, 1.3, 1.5, 2.0)))
        val img = SimCamera(scene, noise = 0.01).read()
        assertNull(fitGroundPlane(deproject(img, p), p, null, Random(0)))

        val pl = assertNotNull(imuFit(scene))
        assertEquals(1.6, pl.cameraHeight, 0.03)
    }

    @Test
    fun ignoresTableTopsAndLowerFloorBeyondAStep() {
        val tables = Scene(boxes = listOf(Box(-0.5, 0.5, 1.0, 1.3, 0.7), Box(0.6, 2.0, 1.5, 2.5, 0.75)))
        for (seed in 0 until 4) {
            assertEquals(1.6, assertNotNull(imuFit(tables, 0.015, seed)).cameraHeight, 0.03)
            assertEquals(1.6, assertNotNull(imuFit(Scene(stepDownAt = 1.8), 0.015, seed)).cameraHeight, 0.03)
        }
    }

    @Test
    fun correctsSmallImuMisalignment() {
        val pl = assertNotNull(imuFit(Scene(imuErrorDeg = 3.0), noise = 0.005))
        assertEquals(25.0, pl.pitchDeg, 0.7)
        assertEquals(1.6, pl.cameraHeight, 0.03)
    }

    @Test
    fun rejectsImplausibleUpVector() {
        val img = SimCamera(Scene()).read()
        // "up" pointing along the camera's forward axis: camera looking straight down.
        assertNull(fitGroundPlaneWithGravity(deproject(img, p), doubleArrayOf(0.0, 0.0, -1.0), p))
    }

    @Test
    fun pipelineUsesImuWhenAvailable() {
        val scene = Scene(boxes = listOf(Box(-0.4, 0.4, 2.0, 2.5, 1.0)), imu = true)
        val cam = SimCamera(scene)
        val pipe = Pipeline()
        var res: StepResult? = null
        repeat(3) { res = pipe.step(cam.read()) }
        assertEquals(FloorSource.IMU, res!!.floorSource)
        assertTrue(res!!.plan.reachable)

        val noImu = Pipeline().step(SimCamera(scene.copy(imu = false)).read())
        assertEquals(FloorSource.VISION, noImu.floorSource)
    }

    @Test
    fun gravityFilterSmoothsAndRejectsJolts() {
        val f = GravityFilter(p)
        val th = Math.toRadians(20.0)
        assertNull(f.up())
        repeat(200) {
            f.add(0.0, -9.81 * cos(th), -9.81 * sin(th))
        }
        assertFalse(f.add(0.0, -25.0, 0.0), "a walking jolt must be rejected")
        assertFalse(f.add(0.0, -1.0, 0.0), "free fall must be rejected")
        val up = assertNotNull(f.up())
        assertEquals(-cos(th), up[1], 1e-6)
        assertEquals(-sin(th), up[2], 1e-6)
    }
}
