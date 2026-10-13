package com.hearsight.app

import android.content.Context
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.StepResult
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Owns guidance for the whole app process: the live RealSense camera, segmentation model,
 * feedback and the navigation engine. There is no simulated input: guidance only runs on the
 * camera's live feed. [NavigationService] starts and stops it and keeps the process alive with
 * the screen off; [MainActivity] only displays its state.
 */
object NavController {
    interface Listener {
        /** Every processed live frame with its result (navigation thread). */
        fun onResult(img: DepthImage, r: StepResult) {}
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
    @Volatile var outdoor = false
        private set
    private var initialized = false

    val isRunning get() = engine?.isRunning == true
    val segMillis get() = engine?.segMillis ?: 0.0
    /** Capture-to-cue latency of the last frame, ms (null if camera timestamps aren't host time). */
    val latencyMs get() = engine?.latencyMs

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
    fun start(outdoor: Boolean): String? {
        if (isRunning) return null
        if (!camera.isConnected()) return "No RealSense found. Connect it with a USB-C OTG cable."
        this.outdoor = outdoor
        // The model is trained on street scenes: indoors it may call the floor "road", so it
        // only runs in outdoor mode (and only then is the colour stream switched on).
        val seg = if (outdoor) segmenter else null
        camera.wantColor = seg != null
        engine = NavigationEngine(
            camera,
            onResult = { img, r ->
                feedback.update(r.cue)
                listeners.forEach { it.onResult(img, r) }
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
}
