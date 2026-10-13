package com.hearsight.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Switch
import android.widget.TextView
import com.hearsight.navcore.Command
import com.hearsight.navcore.StepResult

/**
 * Controls and live view. Guidance itself runs in [NavigationService] / [NavController], so it
 * keeps going with the screen off or this activity closed.
 */
class MainActivity : Activity(), NavController.Listener {
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
        NavController.init(this)

        commandText = findViewById(R.id.command)
        detailText = findViewById(R.id.detail)
        statusText = findViewById(R.id.status)
        map = findViewById(R.id.map)
        startButton = findViewById(R.id.start)
        demoSwitch = findViewById(R.id.demo)
        outdoorSwitch = findViewById(R.id.outdoor)

        val feedback = NavController.feedback
        findViewById<CheckBox>(R.id.optVibrate).apply {
            isChecked = feedback.vibrate
            setOnCheckedChangeListener { _, on -> feedback.vibrate = on }
        }
        findViewById<CheckBox>(R.id.optSpeech).apply {
            isChecked = feedback.speech
            setOnCheckedChangeListener { _, on -> feedback.speech = on }
        }
        findViewById<CheckBox>(R.id.optBeeps).apply {
            isChecked = feedback.beeps
            setOnCheckedChangeListener { _, on -> feedback.beeps = on }
        }
        if (!Cameras.AVAILABLE) demoSwitch.isChecked = true

        startButton.setOnClickListener { if (NavController.isRunning) stopGuidance() else startGuidance() }

        // The ongoing notification (with its Stop button) needs this on Android 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        NavController.addListener(this)
        showState()
    }

    override fun onStop() {
        NavController.removeListener(this)
        super.onStop()
    }

    private fun startGuidance() {
        val demo = demoSwitch.isChecked
        if (!demo && !NavController.camera.isConnected()) {
            NavController.report("No camera. Plug in the RealSense.")
            return
        }
        NavigationService.start(this, demo, outdoorSwitch.isChecked)
    }

    private fun stopGuidance() {
        NavigationService.stop(this)
    }

    override fun onStateChanged() = runOnUiThread { showState() }

    override fun onMessage(msg: String) = runOnUiThread { detailText.text = msg }

    /** Called on the navigation thread for every frame. */
    override fun onResult(r: StepResult) {
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

    private fun showState() {
        val running = NavController.isRunning
        startButton.setText(if (running) R.string.stop else R.string.start)
        startButton.backgroundTintList =
            ColorStateList.valueOf(if (running) Color.rgb(239, 68, 68) else Color.rgb(34, 197, 94))
        demoSwitch.isEnabled = !running
        outdoorSwitch.isEnabled = !running
        if (running) {
            demoSwitch.isChecked = NavController.demo
            outdoorSwitch.isChecked = NavController.outdoor
        } else {
            commandText.text = "READY"
            commandText.setTextColor(Color.rgb(244, 244, 245))
            statusText.text = cameraStatus()
        }
    }

    private fun render(r: StepResult) {
        if (!NavController.isRunning) return
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
        val demo = NavController.demo
        statusText.text = buildString {
            append("%.0f fps · %.0f ms/frame".format(fps, r.millis))
            if (pl != null) append(" · cam %.2f m, pitch %.0f°".format(pl.cameraHeight, pl.pitchDeg))
            append(" · floor: ${r.floorSource.name.lowercase()}")
            append(
                when {
                    demo -> " · labels: simulated"
                    NavController.segmenter == null -> " · no segmentation model"
                    !NavController.outdoor -> " · indoor (no segmentation)"
                    else -> " · seg %.0f ms".format(NavController.segMillis)
                },
            )
            r.odometry?.let {
                append(" · odom ${it.tracking.name.lowercase()}, heading %+.0f°, %.1f m".format(it.headingDeg, it.distance))
                it.stepSpeed?.let { v -> append(" · steps %.1f m/s (step %.2f m)".format(v, it.stepLength ?: 0.0)) }
            }
            if (!demo) append(" · ${NavController.camera.imuStatus}")
            append(" · steer %+.0f°".format(c.angleDeg))
        }
    }

    private fun cameraStatus(): String {
        val camera = NavController.camera
        return when {
            !Cameras.AVAILABLE -> "Built without librealsense: demo mode only (see README)."
            camera.isConnected() -> {
                val name = camera.deviceName() ?: "RealSense"
                if (name.contains("D455")) "$name connected."
                else "$name connected. Settings are tuned for the D455: see Params.kt."
            }
            else -> "RealSense not connected. Plug it in with a USB-C OTG cable."
        }
    }
}
