package com.hearsight.app

import android.content.Context
import com.hearsight.navcore.Box
import com.hearsight.navcore.DepthSource
import com.hearsight.navcore.Scene
import com.hearsight.navcore.SimCamera
import com.hearsight.navcore.StepResult
import com.hearsight.navcore.Surface
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns guidance for the whole app process: camera, segmentation model, feedback and the
 * navigation engine. [NavigationService] starts and stops it and keeps the process alive with
 * the screen off; [MainActivity] only displays its state.
 */
object NavController {
    interface Listener {
        /** Every processed frame (navigation thread). */
        fun onResult(r: StepResult) {}
        /** Started / stopped, camera plugged or unplugged, model loaded (any thread). */
        fun onStateChanged() {}
        /** Something the user should read, e.g. an error (any thread). */
        fun onMessage(msg: String) {}
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private lateinit var app: Context
    lateinit var feedback: Feedback
        private set
    lateinit var camera: RealSenseCamera
        private set
    @Volatile var segmenter: Segmenter? = null
        private set
    @Volatile var segmenterLoaded = false
        private set
    @Volatile private var engine: NavigationEngine? = null
    @Volatile var demo = false
        private set
    @Volatile var outdoor = false
        private set
    private var initialized = false

    val isRunning get() = engine?.isRunning == true
    val segMillis get() = engine?.segMillis ?: 0.0

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        app = context.applicationContext
        feedback = Feedback(app)
        camera = Cameras.realSense(app) { connected ->
            feedback.speak(if (connected) "Camera connected" else "Camera disconnected")
            listeners.forEach { it.onStateChanged() }
        }
        // Loading the model takes a moment; do it off the calling thread.
        Thread {
            segmenter = Segmenter.load(app)
            segmenterLoaded = true
            listeners.forEach { it.onStateChanged() }
        }.start()
    }

    /** Starts guidance. Returns an error message instead if it can't start. */
    @Synchronized
    fun start(demo: Boolean, outdoor: Boolean): String? {
        if (isRunning) return null
        if (!demo && !camera.isConnected()) return "No RealSense found. Connect it with a USB-C OTG cable."
        this.demo = demo
        this.outdoor = outdoor
        val source: DepthSource = if (demo) demoCamera() else camera
        // Demo frames carry simulated labels; the real camera needs the model (and colour).
        // The model is trained on street scenes: indoors it may call the floor "road", so it
        // only runs in outdoor mode.
        val seg = if (demo || !outdoor) null else segmenter
        if (!demo) camera.wantColor = seg != null
        engine = NavigationEngine(
            source,
            onResult = { r ->
                feedback.update(r.cue)
                listeners.forEach { it.onResult(r) }
            },
            onError = { msg ->
                listeners.forEach { it.onMessage(msg) }
                if (engine?.isRunning != true) NavigationService.stop(app)
            },
            segmenter = seg,
        ).also { it.start() }
        feedback.active = true
        feedback.speak("Guidance started")
        listeners.forEach { it.onStateChanged() }
        return null
    }

    @Synchronized
    fun stop() {
        val e = engine ?: return
        engine = null
        e.stop()
        feedback.active = false
        feedback.speak("Guidance stopped")
        listeners.forEach { it.onStateChanged() }
    }

    fun report(msg: String) {
        feedback.speak(msg)
        listeners.forEach { it.onMessage(msg) }
    }

    /** A sidewalk beside a road, with a box, a person and a step down, walked on a loop. */
    private fun demoCamera() = SimCamera(
        Scene(
            boxes = listOf(
                Box(-0.4, 0.5, 3.0, 3.5, 1.0),
                Box(-1.3, -0.9, 5.0, 5.3, 1.7, Surface.PERSON),
            ),
            roadRightOf = 1.4,
            semantics = true,
            stepDownAt = 7.5,
            walkSpeed = 0.03,
            loopAt = 6.0,
            yawRateDeg = 0.3,
            imu = true,
            imuErrorDeg = 1.0,
            stepCounter = true,
            fps = 15.0,
        ),
        noise = 0.01,
        frameDelayMs = 66,
    )
}
