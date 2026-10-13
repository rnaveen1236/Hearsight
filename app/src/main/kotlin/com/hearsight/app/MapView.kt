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
import com.hearsight.navcore.Pt
import com.hearsight.navcore.StepResult

/** Top-down debug map in the style of the dotLumen demo: floor shading, obstacles, path, target. */
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
                g.obstacle[i] -> Color.rgb(220, 40, 40)
                g.dropoff[i] -> Color.rgb(200, 40, 200)
                g.cost[i].isInfinite() -> Color.rgb(30, 30, 30)
                g.unknown[i] -> Color.rgb(55, 55, 55)
                else -> (235 - 25 * g.cost[i]).toInt().coerceIn(40, 235).let { Color.rgb(it, it, it) }
            }
            pixels[(g.rows - 1 - row) * g.cols + col] = c  // forward = up
        }
        val bmp = bitmap!!
        bmp.setPixels(pixels, 0, g.cols, 0, 0, g.cols, g.rows)

        val scale = minOf(width.toFloat() / g.cols, height.toFloat() / g.rows)
        val w = g.cols * scale; val h = g.rows * scale
        val dst = RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawBitmap(bmp, null, dst, bmpPaint)

        val range = g.rows * g.cell
        fun px(p: Pt) = floatArrayOf(
            dst.left + ((p.lat + g.halfWidth) / (2 * g.halfWidth) * dst.width()).toFloat(),
            dst.bottom - (p.fwd / range * dst.height()).toFloat(),
        )

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
}
