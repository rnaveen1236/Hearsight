package com.hearsight.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.hearsight.navcore.CameraOverlay
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.Params
import com.hearsight.navcore.StepResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The live RealSense picture with the navigation drawn on it (navcore [CameraOverlay]):
 * obstacles red, drop-offs magenta, the planned path green, the steering target purple.
 * Tap to switch between the colour image (outdoor mode streams colour) and the depth image.
 *
 * Frames are rendered on a background thread and dropped while one is still rendering, so
 * the display never slows down guidance.
 */
class CameraView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val renderer = Executors.newSingleThreadExecutor { r -> Thread(r, "hearsight-preview") }
    private val busy = AtomicBoolean(false)
    private val params = Params()
    private val lock = Any()
    private var front: Bitmap? = null
    private var pixels = IntArray(0)
    @Volatile private var showColour = true
    @Volatile private var hasColour = false

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
    private val labelBg = Paint().apply { color = Color.argb(140, 0, 0, 0) }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY; textSize = 36f; textAlign = Paint.Align.CENTER
    }

    init {
        setOnClickListener {
            showColour = !showColour
            invalidate()
        }
        contentDescription = "Live camera view with the planned path"
    }

    /** Called from the navigation thread for every frame. */
    fun show(img: DepthImage, r: StepResult) {
        if (!busy.compareAndSet(false, true)) return
        renderer.execute {
            try {
                hasColour = img.rgb != null
                val n = img.width * img.height
                if (pixels.size != n) pixels = IntArray(n)
                CameraOverlay.render(img, r, params, useColor = showColour, out = pixels)
                synchronized(lock) {
                    val bmp = front?.takeIf { it.width == img.width && it.height == img.height }
                        ?: Bitmap.createBitmap(img.width, img.height, Bitmap.Config.ARGB_8888)
                    bmp.setPixels(pixels, 0, img.width, 0, 0, img.width, img.height)
                    front = bmp
                }
                postInvalidateOnAnimation()
            } finally {
                busy.set(false)
            }
        }
    }

    fun clear() {
        synchronized(lock) { front = null }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the D455's 16:9 depth aspect ratio at the available width.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w * 9 / 16)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(20, 20, 22))
        synchronized(lock) {
            val bmp = front
            if (bmp == null) {
                canvas.drawText("Camera view appears when guidance starts", width / 2f, height / 2f, emptyPaint)
                return
            }
            canvas.drawBitmap(bmp, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), bmpPaint)
        }
        val label = when {
            !showColour -> "DEPTH · tap for colour"
            hasColour -> "COLOUR · tap for depth"
            else -> "DEPTH (colour only in Outdoor mode)"
        }
        canvas.drawRect(0f, 0f, labelPaint.measureText(label) + 24f, 44f, labelBg)
        canvas.drawText(label, 12f, 32f, labelPaint)
    }

    override fun onDetachedFromWindow() {
        renderer.shutdown()
        super.onDetachedFromWindow()
    }
}
