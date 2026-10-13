package com.hearsight.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import com.hearsight.navcore.Box
import com.hearsight.navcore.Command
import com.hearsight.navcore.DepthSource
import com.hearsight.navcore.Scene
import com.hearsight.navcore.SimCamera
import com.hearsight.navcore.StepResult
import com.hearsight.navcore.Surface

class MainActivity : Activity() {
    private lateinit var feedback: Feedback
    private lateinit var camera: RealSenseCamera
    @Volatile private var segmenter: Segmenter? = null
    @Volatile private var segmenterLoaded = false
    private var engine: NavigationEngine? = null

    private lateinit var commandText: TextView
    private lateinit var detailText: TextView
    private lateinit var statusText: TextView
    private lateinit var map: MapView
    private lateinit var startButton: Button
    private lateinit var demoSwitch: Switch
    private lateinit var outdoorSwitch: Switch

    @Volatile private var uiPending = false
    private var fps = 0.0
    private var lastFrameAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        commandText = findViewById(R.id.command)
        detailText = findViewById(R.id.detail)
        statusText = findViewById(R.id.status)
        map = findViewById(R.id.map)
        startButton = findViewById(R.id.start)
        demoSwitch = findViewById(R.id.demo)
        outdoorSwitch = findViewById(R.id.outdoor)

        feedback = Feedback(this)
        findViewById<CheckBox>(R.id.optVibrate).setOnCheckedChangeListener { _, on -> feedback.vibrate = on }
        findViewById<CheckBox>(R.id.optSpeech).setOnCheckedChangeListener { _, on -> feedback.speech = on }
        findViewById<CheckBox>(R.id.optBeeps).setOnCheckedChangeListener { _, on -> feedback.beeps = on }

        // Loading the model takes a moment; do it off the UI thread.
        Thread {
            segmenter = Segmenter.load(this)
            segmenterLoaded = true
            runOnUiThread { updateStatus() }
        }.start()

        camera = Cameras.realSense(this) { connected ->
            runOnUiThread {
                feedback.speak(if (connected) "Camera connected" else "Camera disconnected")
                updateStatus()
            }
        }
        if (!Cameras.AVAILABLE) demoSwitch.isChecked = true

        startButton.setOnClickListener { if (engine?.isRunning == true) stopGuidance() else startGuidance() }
        updateStatus()
    }

    private fun startGuidance() {
        val demo = demoSwitch.isChecked
        if (!demo && !camera.isConnected()) {
            feedback.speak("No camera. Plug in the RealSense.")
            detailText.text = "No RealSense found. Connect it with a USB-C OTG cable."
            return
        }
        val source: DepthSource = if (demo) demoCamera() else camera
        // Demo frames carry simulated labels; the real camera needs the model (and colour).
        // The model is trained on street scenes: indoors it may call the floor "road", so it
        // only runs when the user switches on outdoor mode.
        val seg = if (demo || !outdoorSwitch.isChecked) null else segmenter
        if (!demo) camera.wantColor = seg != null
        engine = NavigationEngine(source, ::onResult, segmenter = seg, onError = { msg ->
            runOnUiThread {
                detailText.text = msg
                if (engine?.isRunning != true) stopGuidance()
            }
        }).also { it.start() }
        feedback.active = true
        feedback.speak("Guidance started")
        startButton.setText(R.string.stop)
        startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(239, 68, 68))
        demoSwitch.isEnabled = false
        outdoorSwitch.isEnabled = false
    }

    private fun stopGuidance() {
        engine?.stop()
        engine = null
        feedback.active = false
        feedback.speak("Guidance stopped")
        startButton.setText(R.string.start)
        startButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(34, 197, 94))
        demoSwitch.isEnabled = true
        outdoorSwitch.isEnabled = true
        commandText.text = "READY"
        commandText.setTextColor(Color.rgb(244, 244, 245))
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
        ),
        noise = 0.01,
        frameDelayMs = 66,
    )

    /** Called on the navigation thread for every frame. */
    private fun onResult(r: StepResult) {
        feedback.update(r.cue)
        val now = System.nanoTime()
        if (lastFrameAt != 0L) fps = 0.9 * fps + 0.1 * 1e9 / (now - lastFrameAt)
        lastFrameAt = now
        if (uiPending) return  // drop UI frames rather than queue them
        uiPending = true
        runOnUiThread {
            uiPending = false
            render(r)
        }
    }

    private fun render(r: StepResult) {
        val c = r.cue
        commandText.text = when (c.command) {
            Command.STRAIGHT -> "↑  STRAIGHT"
            Command.LEFT -> "↖  LEFT"
            Command.RIGHT -> "↗  RIGHT"
            Command.STOP -> "✋  STOP"
        }
        commandText.setTextColor(
            when (c.command) {
                Command.STRAIGHT -> Color.rgb(34, 197, 94)
                Command.LEFT, Command.RIGHT -> Color.rgb(234, 179, 8)
                Command.STOP -> Color.rgb(239, 68, 68)
            },
        )
        if (c.say != null) detailText.text = c.say
        map.show(r)
        val pl = r.plane
        statusText.text = buildString {
            append("%.0f fps · %.0f ms/frame".format(fps, r.millis))
            if (pl != null) append(" · cam %.2f m, pitch %.0f°".format(pl.cameraHeight, pl.pitchDeg))
            append(" · floor: ${r.floorSource.name.lowercase()}")
            append(
                when {
                    demoSwitch.isChecked -> " · labels: simulated"
                    segmenter == null -> " · no segmentation model"
                    !outdoorSwitch.isChecked -> " · indoor (no segmentation)"
                    else -> " · seg %.0f ms".format(engine?.segMillis ?: 0.0)
                },
            )
            r.odometry?.let {
                append(" · odom ${it.tracking.name.lowercase()}, heading %+.0f°, %.1f m".format(it.headingDeg, it.distance))
            }
            if (!demoSwitch.isChecked) append(" · ${camera.imuStatus}")
            append(" · steer %+.0f°".format(c.angleDeg))
        }
    }

    private fun updateStatus() {
        statusText.text = when {
            !Cameras.AVAILABLE -> "Built without librealsense: demo mode only (see README)."
            camera.isConnected() -> {
                val name = camera.deviceName() ?: "RealSense"
                if (name.contains("D455")) "$name connected."
                else "$name connected. Settings are tuned for the D455: see Params.kt."
            }
            else -> "RealSense not connected. Plug it in with a USB-C OTG cable."
        }
    }

    override fun onDestroy() {
        engine?.stop()
        feedback.shutdown()
        segmenter?.close()
        super.onDestroy()
    }
}
