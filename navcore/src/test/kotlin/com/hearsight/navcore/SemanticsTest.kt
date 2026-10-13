package com.hearsight.navcore

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SemanticsTest {
    private fun run(scene: Scene, frames: Int = 6, params: Params = Params()): List<StepResult> {
        val cam = SimCamera(scene, width = 424, height = 240)
        val pipe = Pipeline(params)
        return (0 until frames).map { pipe.step(cam.read()) }
    }

    private fun Grid.pathOnRoad(plan: Plan) = plan.path.count { pt ->
        val r = rowOf(pt.fwd); val c = colOf(pt.lat)
        r in 0 until rows && c in 0 until cols && groundAt(idx(r, c)) == Surface.ROAD
    }

    @Test
    fun decodePicksMostLikelyClassAndRejectsUnsureOnes() {
        // 3 classes, 1x2 image: pixel 0 clearly class 2, pixel 1 a near tie
        val logits = floatArrayOf(
            0f, 1.0f,   // class 0
            0f, 1.1f,   // class 1
            5f, 0.9f,   // class 2
        )
        val toSurface = arrayOf(Surface.ROAD, Surface.WALKWAY, Surface.PERSON)
        val sem = SegmentationCodec.decode(logits, 3, 1, 2, toSurface, minConfidence = 0.5f)
        assertEquals(Surface.PERSON, Surface.of(sem.labels[0]))
        assertEquals(Surface.UNKNOWN, Surface.of(sem.labels[1]))
    }

    @Test
    fun preprocessNormalisesAndResizes() {
        // 2x1 RGB image: white, black -> 4x2 tensor
        val rgb = byteArrayOf(-1, -1, -1, 0, 0, 0)
        val t = SegmentationCodec.preprocess(rgb, 2, 1, 4, 2)
        val plane = 8
        assertEquals((1f - 0.485f) / 0.229f, t[0], 1e-5f)            // R of a white pixel
        assertEquals((0f - 0.406f) / 0.225f, t[2 * plane + 3], 1e-5f) // B of a black pixel
        assertEquals(t[0], t[4], 1e-6f)                                 // nearest-neighbour rows
    }

    @Test
    fun cityscapesMappingCoversAllClasses() {
        assertEquals(19, Cityscapes.CLASSES.size)
        assertEquals(19, Cityscapes.TO_SURFACE.size)
        assertEquals(Surface.ROAD, Cityscapes.TO_SURFACE[Cityscapes.CLASSES.indexOf("road")])
        assertEquals(Surface.WALKWAY, Cityscapes.TO_SURFACE[Cityscapes.CLASSES.indexOf("sidewalk")])
        assertEquals(Surface.PERSON, Cityscapes.TO_SURFACE[Cityscapes.CLASSES.indexOf("rider")])
    }

    @Test
    fun plannerPrefersTheSidewalkOverTheRoad() {
        // Box ahead, slightly left. The short way round is right, which means stepping onto
        // the road (box edge 0.4 + 0.3 m clearance > road edge 0.6); the sidewalk way is left.
        val base = Scene(boxes = listOf(Box(-0.9, 0.4, 2.0, 2.4, 1.0)), roadRightOf = 0.6, imu = true)
        val blind = run(base).last()
        val seeing = run(base.copy(semantics = true)).last()

        assertTrue(blind.plan.angleDeg > 0, "without labels it takes the short way, right: ${blind.plan.angleDeg}")
        assertTrue(seeing.plan.angleDeg < 0, "with labels it stays on the sidewalk, left: ${seeing.plan.angleDeg}")
        assertEquals(0, seeing.grid!!.pathOnRoad(seeing.plan))
        val g = seeing.grid!!
        assertEquals(Surface.ROAD, g.groundAt(g.idx(g.rowOf(2.0), g.colOf(1.5))))
        assertEquals(Surface.WALKWAY, g.groundAt(g.idx(g.rowOf(2.0), g.colOf(-1.0))))
    }

    @Test
    fun peopleGetAWiderBerthAndAreAnnounced() {
        val person = Box(-0.2, 0.2, 1.5, 1.8, 1.7, Surface.PERSON)
        val results = run(Scene(boxes = listOf(person), imu = true, semantics = true), frames = 8)
        val res = results.last()
        assertTrue(res.plan.reachable)
        val minDist = res.plan.path.minOf { pt ->
            // distance from path point to the person's footprint (y 1.5-1.8 ahead of the camera)
            val dy = maxOf(0.0, 1.5 - pt.fwd, pt.fwd - 1.8)
            val dx = maxOf(0.0, -0.2 - pt.lat, pt.lat - 0.2)
            hypot(dx, dy)
        }
        assertTrue(minDist >= 0.55, "path passes ${"%.2f".format(minDist)} m from the person")
        assertTrue(results.any { it.cue.say == "Person ahead" }, "should say 'Person ahead'")

        // 45 cm beside the person is off-limits; beside a plain box of the same size it isn't.
        val g = res.grid!!
        val beside = g.idx(g.rowOf(1.65), g.colOf(0.2 + 0.45))
        assertTrue(g.cost[beside].isInfinite(), "45 cm from a person must be off-limits")
        val box = run(Scene(boxes = listOf(person.copy(kind = Surface.STRUCTURE)), imu = true, semantics = true), 8).last()
        assertTrue(box.grid!!.cost[beside].isFinite(), "45 cm from a box is fine")
    }

    @Test
    fun warnsWhenTheRoadIsAhead() {
        val results = run(Scene(roadRightOf = 0.2, imu = true, semantics = true), frames = 8)
        val said = results.mapNotNull { it.cue.say }
        assertTrue(said.any { it.contains("road", ignoreCase = true) }, "said: $said")
    }

    @Test
    fun lateLabelsAreFusedOntoTheirOwnFrame() {
        // Labels computed for frame 2 arrive while frame 5 is being processed.
        val cam = SimCamera(Scene(roadRightOf = 0.6, imu = true, semantics = true), width = 424, height = 240)
        val pipe = Pipeline()
        val labels = HashMap<Long, SemanticImage>()
        var res: StepResult? = null
        repeat(6) {
            val img = cam.read()
            val stripped = DepthImage(img.width, img.height, img.depth, img.fx, img.fy, img.cx, img.cy, img.gravityUp, img.gyroDelta)
            res = pipe.step(stripped)
            labels[res!!.frameId] = img.semantics!!
            if (res!!.frameId == 4L) pipe.submitSemantics(2, labels[2]!!)
        }
        val g = assertNotNull(res!!.grid)
        assertEquals(Surface.ROAD, g.groundAt(g.idx(g.rowOf(2.0), g.colOf(1.5))))
        assertEquals(Surface.WALKWAY, g.groundAt(g.idx(g.rowOf(2.0), g.colOf(-0.5))))
    }

    @Test
    fun labelsForForgottenFramesAreIgnored() {
        val cam = SimCamera(Scene(roadRightOf = 0.6, imu = true, semantics = true))
        val pipe = Pipeline()
        val first = cam.read()
        pipe.step(first)
        pipe.submitSemantics(999, first.semantics!!)  // no such frame
        val res = pipe.step(cam.read())
        assertTrue(res.grid != null)
    }
}
