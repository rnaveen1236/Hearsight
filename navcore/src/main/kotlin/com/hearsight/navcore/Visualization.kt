package com.hearsight.navcore

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders the live camera view with the navigation overlaid, like the camera panel of the
 * dotLumen demo:
 * - background: the colour image (if streamed) or the depth image, colour-coded by distance;
 * - obstacles tinted red and drop-offs magenta, from each pixel's height above the floor;
 * - the planned path drawn on the floor in green, and the steering target as a purple dot,
 *   hidden where something nearer stands in front of them (depth test against the image); a
 *   target below the view is pinned to the image edge.
 *
 * Output is ARGB pixels at the depth image's resolution; the app shows them scaled up.
 */
object CameraOverlay {
    private const val GREEN = 0xFF3CE63C.toInt()
    private const val PURPLE = 0xFFC83CC8.toInt()
    private const val RED = 0xFFDC2828.toInt()
    private const val MAGENTA = 0xFFC828C8.toInt()

    fun render(
        img: DepthImage,
        result: StepResult,
        p: Params = Params(),
        useColor: Boolean = true,
        out: IntArray = IntArray(img.width * img.height),
    ): IntArray {
        val w = img.width; val h = img.height
        val rgb = img.rgb?.takeIf { useColor && it.size >= w * h * 3 }

        // Background.
        for (i in 0 until w * h) {
            out[i] = if (rgb != null) {
                0xFF000000.toInt() or ((rgb[3 * i].toInt() and 0xFF) shl 16) or
                    ((rgb[3 * i + 1].toInt() and 0xFF) shl 8) or (rgb[3 * i + 2].toInt() and 0xFF)
            } else {
                depthColor(img.depth[i], p.maxDepth)
            }
        }

        // Obstacles and drop-offs, by height above the floor (same thresholds as the grid).
        val plane = result.plane
        if (plane != null) {
            val (fa, _) = groundFrame(plane)
            for (v in 0 until h) for (u in 0 until w) {
                val i = v * w + u
                val z = img.depth[i]
                if (z <= p.minDepth || z >= p.maxDepth) continue
                val x = (u - img.cx) / img.fx * z
                val y = (v - img.cy) / img.fy * z
                val hgt = plane.height(x, y, z)
                val fwd = fa[0] * x + fa[1] * y + fa[2] * z
                val noise = p.noiseK * fwd * fwd
                when {
                    hgt > p.obstacleMinH + noise && hgt < p.obstacleMaxH -> out[i] = blend(out[i], RED, 0.55f)
                    hgt < -(p.dropMinH + noise) -> out[i] = blend(out[i], MAGENTA, 0.6f)
                }
            }

            // Planned path on the floor, then the steering target.
            val pts = result.plan.path.mapNotNull { project(img, plane, it) }
            for (k in 1 until pts.size) line(img, out, pts[k - 1], pts[k], GREEN, 2)
            result.plan.target?.let { t ->
                project(img, plane, t)?.let { c ->
                    val inside = c[0] in 0f..(w - 1f) && c[1] in 0f..(h - 1f)
                    // Often the target is on the floor just below the view (the camera can't see
                    // the user's feet): pin it to the image edge as a direction marker.
                    val shown = if (inside) c else floatArrayOf(c[0].coerceIn(6f, w - 7f), c[1].coerceIn(6f, h - 7f), 0f)
                    disc(img, out, shown, 6, PURPLE)
                }
            }
        }
        return out
    }

    /**
     * Pixel position (u, v) and camera depth z of floor point [pt] (metres ahead / right of the
     * user), or null if it is behind the camera.
     */
    fun project(img: DepthImage, plane: Plane, pt: Pt): FloatArray? {
        val (fa, ra) = groundFrame(plane)
        // floor point right below the camera, plus pt along the floor axes
        val x = -plane.d * plane.nx + pt.fwd * fa[0] + pt.lat * ra[0]
        val y = -plane.d * plane.ny + pt.fwd * fa[1] + pt.lat * ra[1]
        val z = -plane.d * plane.nz + pt.fwd * fa[2] + pt.lat * ra[2]
        if (z < 0.2) return null
        return floatArrayOf((img.fx * x / z + img.cx).toFloat(), (img.fy * y / z + img.cy).toFloat(), z.toFloat())
    }

    /** Near = red, far = blue (a turbo-like ramp); no data = black. */
    fun depthColor(z: Float, maxDepth: Float): Int {
        if (z <= 0f) return 0xFF000000.toInt()
        val t = ((z - 0.3f) / (maxDepth - 0.3f)).coerceIn(0f, 1f)
        val r = (255 * (1.5f - abs(4 * t - 1f))).coerceIn(0f, 255f)
        val g = (255 * (1.5f - abs(4 * t - 2f))).coerceIn(0f, 255f)
        val b = (255 * (1.5f - abs(4 * t - 3f))).coerceIn(0f, 255f)
        return 0xFF000000.toInt() or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    private fun blend(a: Int, b: Int, k: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(a, s) * (1 - k) + ch(b, s) * k).roundToInt().coerceIn(0, 255) shl s
        return 0xFF000000.toInt() or mix(16) or mix(8) or mix(0)
    }

    private fun line(img: DepthImage, out: IntArray, a: FloatArray, b: FloatArray, color: Int, radius: Int) {
        val n = max(1, hypot((b[0] - a[0]).toDouble(), (b[1] - a[1]).toDouble()).toInt())
        if (n > 4 * (img.width + img.height)) return  // a point projected absurdly far (near the horizon): skip
        for (s in 0..n) {
            val t = s.toFloat() / n
            val c = FloatArray(3) { a[it] + t * (b[it] - a[it]) }
            disc(img, out, c, radius, color)
        }
    }

    /** Filled circle at (u, v) for a floor point at depth c[2]; pixels where the camera sees something nearer stay hidden. */
    private fun disc(img: DepthImage, out: IntArray, c: FloatArray, radius: Int, color: Int) {
        val w = img.width; val h = img.height
        val cx = c[0].roundToInt(); val cy = c[1].roundToInt()
        val z = c[2]
        for (y in max(0, cy - radius)..min(h - 1, cy + radius)) for (x in max(0, cx - radius)..min(w - 1, cx + radius)) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > radius * radius) continue
            val seen = img.depth[y * w + x]
            if (seen > 0f && seen < z - OCCLUSION_MARGIN - 0.03f * z) continue  // behind an obstacle
            out[y * w + x] = color
        }
    }

    private const val OCCLUSION_MARGIN = 0.15f
}
