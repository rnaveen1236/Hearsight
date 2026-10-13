package com.hearsight.app

import android.util.Log
import com.hearsight.navcore.DepthSource
import com.hearsight.navcore.Pipeline
import com.hearsight.navcore.StepResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs camera -> navcore pipeline on a dedicated thread. If a [segmenter] is given, frames
 * with colour are segmented on a second thread whenever it is free (the model is slower than
 * the camera); labels go back to the pipeline with their frame id.
 */
class NavigationEngine(
    private val source: DepthSource,
    private val onResult: (StepResult) -> Unit,
    private val onError: (String) -> Unit,
    private val segmenter: Segmenter? = null,
) {
    private val segExecutor = segmenter?.let { Executors.newSingleThreadExecutor { r -> Thread(r, "hearsight-seg") } }
    private val segBusy = AtomicBoolean(false)
    /** Duration of the last segmentation, ms (0 if none yet). */
    @Volatile var segMillis = 0.0
        private set
    /**
     * Capture-to-cue latency of the last frame, ms: from the camera's frame timestamp (the
     * D455 stamps frames in host time by default) to the end of processing. Null if the
     * timestamps aren't in host time.
     */
    @Volatile var latencyMs: Double? = null
        private set
    private val pipeline = Pipeline()
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning get() = running

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "hearsight-nav").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        thread = null
        segExecutor?.shutdown()
        segExecutor?.awaitTermination(1, TimeUnit.SECONDS)
    }

    private fun loop() {
        try {
            source.start()
        } catch (t: Throwable) {
            Log.e(TAG, "camera start failed", t)
            running = false
            onError("Camera failed to start: ${t.message}")
            return
        }
        var missed = 0
        try {
            while (running) {
                val img = try {
                    source.read()
                } catch (t: Throwable) {
                    Log.w(TAG, "frame read failed", t)
                    null
                }
                if (img == null) {
                    // Feedback's watchdog tells the user to stop; report once per streak.
                    if (++missed == 3) onError("No depth frames from camera")
                    continue
                }
                missed = 0
                val result = pipeline.step(img)
                latencyMs = img.timestampMs?.let { System.currentTimeMillis() - it }?.takeIf { it in 0.0..10_000.0 }
                segment(img.rgb, img.width, img.height, result.frameId)
                onResult(result)
            }
        } finally {
            try { source.stop() } catch (t: Throwable) { Log.w(TAG, "camera stop failed", t) }
        }
    }

    private fun segment(rgb: ByteArray?, w: Int, h: Int, frameId: Long) {
        val seg = segmenter ?: return
        val exec = segExecutor ?: return
        if (rgb == null || !segBusy.compareAndSet(false, true)) return
        exec.execute {
            try {
                val t0 = System.nanoTime()
                pipeline.submitSemantics(frameId, seg.segment(rgb, w, h))
                segMillis = (System.nanoTime() - t0) / 1e6
            } catch (t: Throwable) {
                Log.w(TAG, "segmentation failed", t)
            } finally {
                segBusy.set(false)
            }
        }
    }

    private companion object {
        const val TAG = "NavigationEngine"
    }
}
