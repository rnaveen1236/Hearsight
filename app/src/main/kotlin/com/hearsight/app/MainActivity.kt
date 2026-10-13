package com.hearsight.app

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.StepResult

/**
 * Controls and live view. Guidance itself runs in [NavigationService] / [NavController] on the
 * live RealSense feed, so it keeps going with the screen off or this activity closed.
 */
class MainActivity : Activity(), NavController.Listener {
    private lateinit var commandText: TextView
    private lateinit var detailText: TextView
    private lateinit var statusText: TextView
    private lateinit var map: MapView
    private lateinit var cameraView: CameraView
    private lateinit var startButton: Button
    private lateinit var outdoorSwitch: Switch

    /** Start was pressed while a permission dialog was open: continue once it's answered. */
    private var startAfterPermission = false
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
        cameraView = findViewById(R.id.cameraView)
        startButton = findViewById(R.id.start)
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
        startButton.setOnClickListener { if (NavController.isRunning) stopGuidance() else startGuidance() }

        requestMissingPermissions()
    }

    /**
     * CAMERA: Android only lets apps open USB cameras like the D455 with it.
     * POST_NOTIFICATIONS (Android 13+): the ongoing notification with its Stop button.
     */
    private fun requestMissingPermissions(): Boolean {
        val wanted = buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) return false
        requestPermissions(wanted.toTypedArray(), REQUEST_PERMISSIONS)
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val pending = startAfterPermission
        startAfterPermission = false
        if (!CameraAccess.hasCameraPermission(this)) {
            NavController.report("Camera permission is needed to use the RealSense.")
        } else if (pending) {
            startGuidance()
        }
        showState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Opened by plugging in the RealSense (USB_DEVICE_ATTACHED): Android has granted USB access.
        showState()
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

    /** Camera permission, then the device, then USB permission, then start. */
    private fun startGuidance() {
        if (!CameraAccess.hasCameraPermission(this)) {
            startAfterPermission = true
            if (!requestMissingPermissions()) startAfterPermission = false
            return
        }
        val device = CameraAccess.device(this)
        if (device == null) {
            NavController.report("No camera. Plug in the RealSense.")
            return
        }
        CameraAccess.requestUsbPermission(this, device) { granted ->
            if (!granted) {
                NavController.report("USB access to the RealSense was refused. Press Start to ask again.")
            } else if (!NavController.camera.isConnected()) {
                NavController.report("The RealSense isn't responding. Unplug it and plug it in again.")
            } else {
                NavigationService.start(this, outdoorSwitch.isChecked)
            }
            showState()
        }
    }

    private fun stopGuidance() {
        NavigationService.stop(this)
    }

    override fun onStateChanged() = runOnUiThread { showState() }

    override fun onMessage(msg: String) = runOnUiThread { detailText.text = msg }

    /** Called on the navigation thread for every frame. */
    override fun onResult(img: DepthImage, r: StepResult) {
        cameraView.show(img, r)  // renders on its own thread, drops frames while busy
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
        outdoorSwitch.isEnabled = !running
        if (running) {
            outdoorSwitch.isChecked = NavController.outdoor
        } else {
            commandText.text = "READY"
            commandText.setTextColor(Color.rgb(244, 244, 245))
            cameraView.clear()
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
        statusText.text = buildString {
            append("%.0f fps · %.0f ms/frame".format(fps, r.millis))
            NavController.latencyMs?.let { append(" · latency %.0f ms".format(it)) }
            NavController.camera.skippedFrames.takeIf { it > 0 }?.let { append(" · skipped $it stale") }
            if (pl != null) append(" · cam %.2f m, pitch %.0f°".format(pl.cameraHeight, pl.pitchDeg))
            append(" · floor: ${r.floorSource.name.lowercase()}")
            append(
                when {
                    NavController.segmenter == null -> " · no segmentation model"
                    !NavController.outdoor -> " · indoor (no segmentation)"
                    else -> " · seg %.0f ms".format(NavController.segMillis)
                },
            )
            r.odometry?.let {
                append(" · odom ${it.tracking.name.lowercase()}, heading %+.0f°, %.1f m".format(it.headingDeg, it.distance))
                it.stepSpeed?.let { v -> append(" · steps %.1f m/s (step %.2f m)".format(v, it.stepLength ?: 0.0)) }
            }
            append(" · ${NavController.camera.imuStatus}")
            append(" · steer %+.0f°".format(c.angleDeg))
        }
    }

    private fun cameraStatus(): String {
        val camera = NavController.camera
        val access = CameraAccess.state(this)
        if (access != CameraAccess.State.GRANTED && access != CameraAccess.State.NO_DEVICE) {
            return "Camera access: ${access.label}. Press Start to grant it."
        }
        return when {
            camera.isConnected() -> {
                val name = camera.deviceName() ?: "RealSense"
                if (name.contains("D455")) "$name connected."
                else "$name connected. Settings are tuned for the D455: see Params.kt."
            }
            else -> "RealSense not connected. Plug it in with a USB-C OTG cable."
        }
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
    }
}
