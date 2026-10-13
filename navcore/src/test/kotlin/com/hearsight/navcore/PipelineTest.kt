package com.hearsight.navcore

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PipelineTest {
    private fun run(scene: Scene, frames: Int = 3, noise: Double = 0.005): StepResult {
        val cam = SimCamera(scene, noise = noise)
        val pipe = Pipeline()
        var res: StepResult? = null
        repeat(frames) { res = pipe.step(cam.read()) }
        return res!!
    }

    private fun fit(scene: Scene, noise: Double, seed: Int): Plane? {
        val img = SimCamera(scene, noise = noise, seed = seed).read()
        return fitGroundPlane(deproject(img, Params()), Params(), null, Random(seed))
    }

    @Test
    fun groundPlaneRecoversHeightAndPitch() {
        for (seed in 0 until 4) {
            val pl = assertNotNull(fit(Scene(height = 1.55, pitchDeg = 30.0), 0.01, seed))
            assertEquals(1.55, pl.cameraHeight, 0.05)
            assertEquals(30.0, pl.pitchDeg, 3.0)
        }
    }

    @Test
    fun floorIsNotConfusedWithLowerFloorBeyondAStep() {
        for (seed in 0 until 4) {
            val pl = assertNotNull(fit(Scene(stepDownAt = 1.8), 0.015, seed))
            assertEquals(1.6, pl.cameraHeight, 0.05)
            assertEquals(25.0, pl.pitchDeg, 2.0)
        }
    }

    @Test
    fun floorIsNotConfusedWithTableTops() {
        val scene = Scene(boxes = listOf(Box(-0.5, 0.5, 1.0, 1.3, 0.7), Box(0.6, 2.0, 1.5, 2.5, 0.75)))
        for (seed in 0 until 4) {
            val pl = assertNotNull(fit(scene, 0.01, seed))
            assertEquals(1.6, pl.cameraHeight, 0.08)
        }
    }

    @Test
    fun openFloorGoesStraight() {
        val res = run(Scene())
        assertTrue(res.plan.reachable)
        assertEquals(Command.STRAIGHT, res.cue.command)
        val g = res.grid!!
        assertFalse(g.obstacle.any { it })
        assertFalse(g.dropoff.any { it })
    }

    @Test
    fun boxAheadIsDetectedAndAvoidedWithAGentleTurn() {
        val res = run(Scene(boxes = listOf(Box(-0.4, 0.4, 2.0, 2.5, 1.0))))
        val g = res.grid!!
        val r = g.rowOf(2.1); val c = g.colOf(0.0)
        assertTrue((r - 1..r + 1).any { rr -> (c - 1..c + 1).any { cc -> g.obstacle[g.idx(rr, cc)] } })
        assertTrue(res.plan.reachable)
        assertTrue(res.cue.command == Command.LEFT || res.cue.command == Command.RIGHT)
        assertTrue(abs(res.plan.angleDeg) < 45, "turn too sharp: ${res.plan.angleDeg}")
        for (pt in res.plan.path) {
            assertTrue(g.cost[g.idx(g.rowOf(pt.fwd), g.colOf(pt.lat))].isFinite(), "path crosses blocked cell at $pt")
        }
    }

    @Test
    fun boxOnTheLeftSteersRight() {
        val res = run(Scene(boxes = listOf(Box(-0.9, 0.15, 1.8, 2.4, 1.0))))
        assertTrue(res.plan.angleDeg > 0, "angle ${res.plan.angleDeg}")
    }

    @Test
    fun d455NoiseModelSeesALowCurbThatGenericSettingsMiss() {
        // 15 cm curb 2.5 m ahead, D455-like depth noise (0.5%).
        val scene = Scene(boxes = listOf(Box(-2.5, 2.5, 2.5, 2.8, 0.15)))
        fun curbSeen(params: Params): Boolean {
            val cam = SimCamera(scene, noise = 0.005)
            val pipe = Pipeline(params)
            var res: StepResult? = null
            repeat(3) { res = pipe.step(cam.read()) }
            val g = res!!.grid!!
            return (g.rowOf(2.4)..g.rowOf(2.9)).any { r -> (0 until g.cols).any { c -> g.obstacle[g.idx(r, c)] } }
        }
        assertTrue(curbSeen(Params()), "D455 defaults should see the curb")
        assertFalse(curbSeen(Params(noiseK = 0.012f)), "generic D4xx noise allowance hides it")
    }

    @Test
    fun wallBlocksPath() {
        val res = run(Scene(boxes = listOf(Box(-3.0, 3.0, 1.6, 1.8, 2.0))))
        assertFalse(res.plan.reachable)
        assertEquals(Command.STOP, res.cue.command)
    }

    @Test
    fun stepDownIsDetectedAndAnnounced() {
        val res = run(Scene(stepDownAt = 1.8, stepDepth = 0.2))
        val g = res.grid!!
        val drops = (0 until g.rows).filter { r -> (0 until g.cols).any { c -> g.dropoff[g.idx(r, c)] } }
        assertTrue(drops.isNotEmpty())
        assertTrue(g.fwdOf(drops.first()) > 1.6, "drop-off detected too early at ${g.fwdOf(drops.first())}")
        assertTrue(!res.plan.reachable || res.plan.path.maxOf { it.fwd } < 1.9)
        assertEquals(Command.STOP, res.cue.command)
    }

    @Test
    fun tableTopJumpIsRejectedByTracker() {
        val p = Params()
        val tracker = PlaneTracker(p)
        val floor = Plane(0.0, -0.906, -0.423, 1.6)
        val table = Plane(0.0, -0.906, -0.423, 0.9)
        tracker.update(floor)
        repeat(p.planeJumpFrames - 1) { assertEquals(1.6, tracker.update(table)!!.d, 1e-9) }
    }

    @Test
    fun eigenvectorOfDiagonalMatrix() {
        val v = smallestEigenvector(doubleArrayOf(3.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 2.0))
        assertEquals(1.0, abs(v[1]), 1e-9)
    }

    @Test
    fun pipelineIsFastEnoughForAPhone() {
        val cam = SimCamera(Scene(boxes = listOf(Box(-0.4, 0.5, 2.3, 2.8, 1.0))))
        val pipe = Pipeline()
        repeat(5) { pipe.step(cam.read()) }  // warm-up / JIT
        val ms = (0 until 20).map { pipe.step(cam.read()).millis }.average()
        println("navcore step: %.1f ms/frame (desktop JVM)".format(ms))
        assertTrue(ms < 50)
    }
}
