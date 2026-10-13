package com.hearsight.navcore

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VisualizationTest {
    private val scene = Scene(
        boxes = listOf(
            Box(-2.4, -2.2, -1.0, 12.0, 2.5),
            Box(-0.5, 0.3, 2.2, 2.6, 1.0),
            Box(0.9, 1.3, 3.4, 3.7, 1.7, Surface.PERSON),
        ),
        roadRightOf = 1.8,
        imu = true,
        semantics = true,
        colour = true,
    )

    private fun frame(): Pair<DepthImage, StepResult> {
        val cam = SimCamera(scene, width = 424, height = 240)
        val pipe = Pipeline()
        var img: DepthImage? = null
        var res: StepResult? = null
        repeat(6) { img = cam.read(); res = pipe.step(img!!) }
        return img!! to res!!
    }

    private fun isRed(c: Int) = (c shr 16 and 0xFF) > 150 && (c shr 8 and 0xFF) < 120 && (c and 0xFF) < 120

    @Test
    fun floorPointsProjectWhereTheCameraSeesThem() {
        val (img, res) = frame()
        val plane = assertNotNull(res.plane)
        // A floor point 2 m straight ahead: deproject the pixel it lands on and check the depth.
        val px = assertNotNull(CameraOverlay.project(img, plane, Pt(1.5, 0.0)))
        val u = px[0].toInt(); val v = px[1].toInt()
        assertEquals(img.width / 2.0, px[0].toDouble(), 2.0)  // straight ahead = image centre column
        val z = img.depth[v * img.width + u]
        val y = (v - img.cy) / img.fy * z
        val x = (u - img.cx) / img.fx * z
        assertEquals(0.0, plane.height(x, y, z), 0.05)          // the pixel really is floor
    }

    @Test
    fun obstaclesAreTintedAndThePathIsDrawn() {
        val (img, res) = frame()
        val out = CameraOverlay.render(img, res, useColor = true)
        // The box ahead covers the image centre a little below the horizon.
        val plane = res.plane!!
        val boxFoot = CameraOverlay.project(img, plane, Pt(2.25, -0.1))!!
        val u = boxFoot[0].toInt(); val v = boxFoot[1].toInt() - 25  // well above its untinted base
        assertTrue(isRed(out[v * img.width + u]), "box face should be tinted red")
        assertTrue(out.count { it == 0xFF3CE63C.toInt() } > 50, "green path should be drawn")
        assertTrue(out.count { it == 0xFFC83CC8.toInt() } > 20, "purple target should be drawn")
        // The path goes behind the box: none of it may be painted over the box face.
        assertTrue(out[v * img.width + u] != 0xFF3CE63C.toInt())
        val boxPixels = (0 until img.width * img.height).filter { isRed(out[it]) }
        assertTrue(boxPixels.none { out[it] == 0xFF3CE63C.toInt() })
    }

    /** Writes sample frames as PNG when HEARSIGHT_PREVIEW_DIR is set (for looking at the output). */
    @Test
    fun writePreview() {
        val dir = System.getenv("HEARSIGHT_PREVIEW_DIR") ?: return
        val (img, res) = frame()
        for ((name, colour) in listOf("camera-colour" to true, "camera-depth" to false)) {
            val px = CameraOverlay.render(img, res, useColor = colour)
            val bi = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
            bi.setRGB(0, 0, img.width, img.height, px, 0, img.width)
            val big = BufferedImage(img.width * 2, img.height * 2, BufferedImage.TYPE_INT_RGB)
            big.createGraphics().apply { drawImage(bi, 0, 0, img.width * 2, img.height * 2, null); dispose() }
            ImageIO.write(big, "png", File(dir, "$name.png"))
        }
        println("PREVIEW cue=${res.cue} angle=%.0f target=${res.plan.target} projected=${res.plan.target?.let { CameraOverlay.project(img, res.plane!!, it)?.toList() }}".format(res.plan.angleDeg))
    }
}
