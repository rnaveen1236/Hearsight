package com.hearsight.app

import android.util.Log
import com.hearsight.navcore.DepthSource
import com.hearsight.navcore.Pipeline
import com.hearsight.navcore.StepResult

/** Runs camera -> navcore pipeline on a dedicated thread. */
class NavigationEngine(
    private val source: DepthSource,
    private val onResult: (StepResult) -> Unit,
    private val onError: (String) -> Unit,
) {
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
                onResult(pipeline.step(img))
            }
        } finally {
            try { source.stop() } catch (t: Throwable) { Log.w(TAG, "camera stop failed", t) }
        }
    }

    private companion object {
        const val TAG = "NavigationEngine"
    }
}
