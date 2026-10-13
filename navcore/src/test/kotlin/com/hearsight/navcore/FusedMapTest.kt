package com.hearsight.navcore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FusedMapTest {
    private val walls = listOf(
        Box(-2.4, -2.2, -1.0, 12.0, 2.5),
        Box(2.2, 2.4, -1.0, 12.0, 2.5),
    )

    private fun obstacleNear(g: Grid, fwd: Double, lat: Double, radius: Double = 0.25): Boolean {
        val r0 = g.rowOf(fwd - radius); val r1 = g.rowOf(fwd + radius)
        val c0 = g.colOf(lat - radius); val c1 = g.colOf(lat + radius)
        for (r in maxOf(0, r0)..minOf(g.rows - 1, r1)) for (c in maxOf(0, c0)..minOf(g.cols - 1, c1)) {
            if (g.obstacle[g.idx(r, c)]) return true
        }
        return false
    }

    private fun run(cam: SimCamera, frames: Int, params: Params = Params()): StepResult {
        val pipe = Pipeline(params)
        var res: StepResult? = null
        repeat(frames) { res = pipe.step(cam.read()) }
        return res!!
    }

    @Test
    fun remembersAnObstacleBesideTheUserAfterItLeavesTheView() {
        // Walk 1.5 m past a box on the left; at the end it is next to the user, out of view.
        val scene = Scene(boxes = walls + Box(-1.1, -0.7, 1.4, 1.8, 0.9), walkSpeed = 0.03, imu = true, loopAt = 100.0)
        val fused = run(SimCamera(scene, width = 424, height = 240), 50)
        val single = run(SimCamera(scene, width = 424, height = 240), 50, Params(fuseMap = false))
        // box is now at fwd ~ -0.1..0.3, lat ~ -0.9
        assertTrue(obstacleNear(fused.grid!!, 0.15, -0.9), "fused map should remember the box")
        assertFalse(obstacleNear(single.grid!!, 0.15, -0.9), "single-frame grid can't see it")
    }

    @Test
    fun remembersObstaclesAfterTurningAway() {
        // Box 2 m ahead; turn right 70 degrees on the spot so it leaves the field of view.
        val scene = Scene(boxes = walls + Box(-0.25, 0.25, 1.9, 2.3, 1.0), yawRateDeg = 2.0, imu = true)
        val fused = run(SimCamera(scene, width = 424, height = 240), 36)
        val single = run(SimCamera(scene, width = 424, height = 240), 36, Params(fuseMap = false))
        // After a 70 degree right turn the box is at bearing -70 deg, ~2.1 m away.
        val a = Math.toRadians(70.0)
        val fwd = 2.1 * Math.cos(a); val lat = -2.1 * Math.sin(a)
        assertTrue(obstacleNear(fused.grid!!, fwd, lat, 0.35), "fused map should keep the box")
        assertFalse(obstacleNear(single.grid!!, fwd, lat, 0.35))
    }

    @Test
    fun aPersonWhoWalksAwayIsErased() {
        val person = Box(-0.25, 0.25, 2.0, 2.4, 1.8)
        val pipe = Pipeline()
        val withPerson = SimCamera(Scene(boxes = walls + person, imu = true), width = 424, height = 240)
        val empty = SimCamera(Scene(boxes = walls, imu = true), width = 424, height = 240)
        var res: StepResult? = null
        repeat(10) { res = pipe.step(withPerson.read()) }
        assertTrue(obstacleNear(res!!.grid!!, 2.2, 0.0))
        var clearedAfter = -1
        for (i in 1..20) {
            res = pipe.step(empty.read())
            if (!obstacleNear(res!!.grid!!, 2.2, 0.0)) { clearedAfter = i; break }
        }
        println("person erased after $clearedAfter frames")
        assertTrue(clearedAfter in 1..15, "person still in the map after half a second (15 frames)")
    }

    @Test
    fun staysConsistentWhileWalkingAndRecentering() {
        // 4 m walk: the 8 m window recentres several times; obstacles must stay where they are.
        val boxes = walls + listOf(Box(-1.0, -0.6, 2.0, 2.4, 1.0), Box(0.7, 1.1, 4.5, 4.9, 1.2))
        val cam = SimCamera(Scene(boxes = boxes, walkSpeed = 0.04, imu = true, loopAt = 100.0), width = 424, height = 240)
        val res = run(cam, 100)
        val g = res.grid!!
        // user at y = 4.0: second box is 0.5-0.9 m ahead at lat 0.9, first box is behind (not in grid)
        assertTrue(obstacleNear(g, 0.7, 0.9))
        // open floor straight ahead stays free
        assertFalse(obstacleNear(g, 1.5, -0.3, 0.15))
        // walls at |lat| = 2.3
        assertTrue(obstacleNear(g, 1.0, -2.3, 0.15))
        assertTrue(obstacleNear(g, 1.0, 2.3, 0.15))
    }

    @Test
    fun mapIsClearedWhenTrackingIsLost() {
        val pipe = Pipeline()
        val cam = SimCamera(Scene(boxes = walls + Box(-0.3, 0.3, 2.0, 2.4, 1.0), imu = true), width = 424, height = 240)
        repeat(5) { pipe.step(cam.read()) }
        assertTrue(pipe.map.occupiedVoxels() > 0)
        val img = cam.read()
        pipe.step(DepthImage(img.width, img.height, FloatArray(img.depth.size), img.fx, img.fy, img.cx, img.cy, img.gravityUp, img.gyroDelta))
        assertEquals(0, pipe.map.occupiedVoxels())
    }

    @Test
    fun fullPipelineStaysFastEnough() {
        val cam = SimCamera(Scene(boxes = walls + Box(-0.3, 0.3, 2.0, 2.4, 1.0), walkSpeed = 0.03, imu = true), width = 424, height = 240)
        val pipe = Pipeline()
        repeat(10) { pipe.step(cam.read()) }
        val ms = (0 until 30).map { pipe.step(cam.read()).millis }.average()
        println("full pipeline incl. odometry + fused map: %.1f ms/frame (desktop JVM, 424x240)".format(ms))
        assertTrue(ms < 60)
    }
}
