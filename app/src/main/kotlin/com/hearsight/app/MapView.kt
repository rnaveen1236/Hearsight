package com.hearsight.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.hearsight.navcore.Params
import com.hearsight.navcore.Pt
import com.hearsight.navcore.StepResult
import com.hearsight.navcore.Surface

/**
 * Top-down debug map in the style of the dotLumen demo: floor shading, obstacles, path, target,
 * and below the user a strip showing the odometry trail (where they came from, as red arrows).
 * Segmentation colours: road amber, grass green, people yellow, vehicles orange.
 */
class MapView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    @Volatile private var result: StepResult? = null
    private var bitmap: Bitmap? = null
    private var pixels = IntArray(0)

    private val bmpPaint = Paint().apply { isFilterBitmap = false }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 230, 60); style = Paint.Style.STROKE; strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(200, 60, 200) }
    private val userPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(239, 68, 68) }
    private val stripPaint = Paint().apply { color = Color.rgb(28, 28, 32) }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 50, 50) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; textSize = 40f; textAlign = Paint.Align.CENTER }

    fun show(r: StepResult) {
        result = r
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 22))
        val r = result
        val g = r?.grid
        if (g == null) {
            canvas.drawText(if (r == null) "Not running" else "Looking for the floor…", width / 2f, height / 2f, textPaint)
            return
        }

        if (bitmap?.width != g.cols || bitmap?.height != g.rows) {
            bitmap = Bitmap.createBitmap(g.cols, g.rows, Bitmap.Config.ARGB_8888)
            pixels = IntArray(g.rows * g.cols)
        }
        for (row in 0 until g.rows) for (col in 0 until g.cols) {
            val i = g.idx(row, col)
            val c = when {
                g.obstacle[i] -> when (g.objectAt(i)) {
                    Surface.PERSON -> Color.rgb(250, 204, 21)
                    Surface.VEHICLE -> Color.rgb(249, 115, 22)
                    else -> Color.rgb(220, 40, 40)
                }
                g.dropoff[i] -> Color.rgb(200, 40, 200)
                g.cost[i].isInfinite() -> Color.rgb(30, 30, 30)
                g.unknown[i] -> Color.rgb(55, 55, 55)
                else -> {
                    // Shade by cost, without the surface's own cost so road isn't just "dark".
                    val surfaceCost = when (g.groundAt(i)) {
                        Surface.ROAD -> PARAMS.roadCost
                        Surface.TERRAIN -> PARAMS.terrainCost
                        else -> 0f
                    }
                    val v = (235 - 25 * (g.cost[i] - surfaceCost)).toInt().coerceIn(40, 235)
                    when (g.groundAt(i)) {
                        Surface.ROAD -> Color.rgb(v, v * 3 / 5, v / 4)
                        Surface.TERRAIN -> Color.rgb(v * 3 / 5, v, v * 2 / 5)
                        else -> Color.rgb(v, v, v)
                    }
                }
            }
            pixels[(g.rows - 1 - row) * g.cols + col] = c  // forward = up
        }
        val bmp = bitmap!!
        bmp.setPixels(pixels, 0, g.cols, 0, 0, g.cols, g.rows)

        val behindRows = BEHIND_METRES / g.cell
        val scale = minOf(width.toFloat() / g.cols, height.toFloat() / (g.rows + behindRows))
        val w = g.cols * scale; val h = g.rows * scale; val hb = behindRows * scale
        val top = (height - h - hb) / 2
        val dst = RectF((width - w) / 2, top, (width + w) / 2, top + h)
        canvas.drawRect(dst.left, dst.bottom, dst.right, dst.bottom + hb, stripPaint)
        canvas.drawBitmap(bmp, null, dst, bmpPaint)

        val range = g.rows * g.cell
        fun px(p: Pt) = floatArrayOf(
            dst.left + ((p.lat + g.halfWidth) / (2 * g.halfWidth) * dst.width()).toFloat(),
            dst.bottom - (p.fwd / range * dst.height()).toFloat(),
        )

        // Odometry trail: past positions as small arrows pointing the way the user faced.
        for (tp in r.trail) {
            if (tp.at.fwd < -BEHIND_METRES || tp.at.fwd > range) continue
            val c = px(tp.at)
            val a = Math.toRadians(tp.headingDeg)
            val dx = Math.sin(a).toFloat(); val dy = -Math.cos(a).toFloat()  // screen: forward = up
            val arrow = Path().apply {
                moveTo(c[0] + 14 * dx, c[1] + 14 * dy)
                lineTo(c[0] - 8 * dx - 7 * dy, c[1] - 8 * dy + 7 * dx)
                lineTo(c[0] - 8 * dx + 7 * dy, c[1] - 8 * dy - 7 * dx)
                close()
            }
            canvas.drawPath(arrow, trailPaint)
        }

        val plan = r.plan
        if (plan.reachable) {
            val path = Path()
            plan.path.forEachIndexed { k, p ->
                val (x, y) = px(p).let { it[0] to it[1] }
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, pathPaint)
            val t = px(plan.target!!)
            canvas.drawCircle(t[0], t[1], 18f, targetPaint)
        }

        val u = px(Pt(0.0, 0.0))
        val tri = Path().apply {
            moveTo(u[0], u[1] - 50); lineTo(u[0] - 22, u[1]); lineTo(u[0] + 22, u[1]); close()
        }
        canvas.drawPath(tri, userPaint)
    }

    private companion object {
        const val BEHIND_METRES = 1.5f
        val PARAMS = Params()
    }
}
