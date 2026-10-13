package com.hearsight.app

import android.content.Context
import android.util.Log
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.DepthSource
import com.hearsight.navcore.GravityFilter
import com.hearsight.navcore.GyroIntegrator
import com.hearsight.navcore.StepDetector
import com.intel.realsense.librealsense.Config
import com.intel.realsense.librealsense.DeviceListener
import com.intel.realsense.librealsense.Extension
import com.intel.realsense.librealsense.Frame
import com.intel.realsense.librealsense.FrameSet
import com.intel.realsense.librealsense.Pipeline
import com.intel.realsense.librealsense.RsContext
import com.intel.realsense.librealsense.StreamFormat
import com.intel.realsense.librealsense.StreamType
import com.intel.realsense.librealsense.VideoFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tan

/** Built with librealsense.aar present: real camera support. */
object Cameras {
    const val AVAILABLE = true

    fun realSense(context: Context, onConnection: (Boolean) -> Unit): RealSenseCamera =
        RealSenseCamera(context, onConnection)
}

/**
 * Intel RealSense D455 (also works with other D4xx) over USB-OTG using Intel's Android wrapper.
 * The D455 has a USB-C port, so a USB-C to USB-C cable goes straight into the phone.
 *
 * 424x240 depth @ 30 fps fits in USB 2.0 bandwidth too, so it works even on phones (or
 * cables) without USB 3. No colour stream: the pipeline only needs depth.
 *
 * With a segmentation model, the colour stream (424x240 RGB) is enabled too and aligned to
 * depth, so each depth pixel has a colour pixel for the model's labels.
 *
 * On cameras with an IMU (D435i, D455) the accelerometer and gyroscope streams are enabled
 * too. The accelerometer is filtered into a gravity "up" vector (floor tilt); the gyroscope is
 * integrated into the rotation between depth frames (odometry prediction) and also keeps the
 * up vector steady during head turns; accelerometer samples also feed a step counter
 * (walking speed for corridors). All of it is attached to each depth frame. librealsense
 * reports IMU data in the depth camera's axes (x right, y down, z forward).
 */
class RealSenseCamera(context: Context, private val onConnection: (Boolean) -> Unit) : DepthSource {
    companion object {
        const val WIDTH = 424
        const val HEIGHT = 240
        const val FPS = 30
        const val DEPTH_UNITS = 0.001f   // D455 default: 1 mm per unit
        const val ACCEL_FPS = 63         // D455 accelerometer runs at 63 or 250 Hz; 63 is plenty
        const val GYRO_FPS = 200         // D455 gyroscope runs at 200 or 400 Hz
        private const val TAG = "RealSense"
    }

    private val rsContext: RsContext
    private var pipeline: Pipeline? = null
    private var raw = ByteArray(0)
    private var intrinsics: FloatArray? = null
    var intrinsicsSource = "unknown"
        private set

    private val gravity = GravityFilter()
    private val steps = StepDetector()
    private val gyro = GyroIntegrator()
    private var imuEnabled = false
    private var gyroEnabled = false
    /** For the status line: whether the IMU is streaming and its current up vector. */
    val imuStatus: String
        get() = when {
            !imuEnabled -> "no IMU"
            else -> (gravity.up()?.let { "IMU up (%.2f, %.2f, %.2f)".format(it[0], it[1], it[2]) } ?: "IMU settling") +
                if (gyroEnabled) " + gyro" else " (no gyro)"
        }

    init {
        RsContext.init(context.applicationContext)
        rsContext = RsContext()
        rsContext.setDevicesChangedCallback(object : DeviceListener {
            override fun onDeviceAttach() = onConnection(true)
            override fun onDeviceDetach() = onConnection(false)
        })
    }

    fun isConnected(): Boolean = rsContext.queryDevices().use { it.deviceCount > 0 }

    /** Model name, e.g. "Intel RealSense D455", or null if it can't be read. */
    fun deviceName(): String? = try {
        rsContext.queryDevices().use { list ->
            if (list.deviceCount == 0) return null
            // Reflection: Device / CameraInfo signatures differ between wrapper versions.
            val device = list.javaClass.getMethod("createDevice", Int::class.javaPrimitiveType).invoke(list, 0)!!
            try {
                val info = Class.forName("com.intel.realsense.librealsense.CameraInfo")
                val name = info.getField("NAME").get(null)
                device.javaClass.getMethod("getInfo", info).invoke(device, name) as? String
            } finally {
                (device as? AutoCloseable)?.close()
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Can't read device name", t)
        null
    }

    /** Stream colour (aligned to depth) for segmentation. Set before [start]. */
    @Volatile var wantColor = false
    private var colorEnabled = false
    /** librealsense Align(DEPTH) processing block, via reflection (constructor varies by version). */
    private val align: Any? by lazy {
        try {
            Class.forName("com.intel.realsense.librealsense.Align")
                .getConstructor(StreamType::class.java).newInstance(StreamType.DEPTH)
        } catch (t: Throwable) {
            Log.w(TAG, "No Align block: colour can't be aligned to depth", t)
            null
        }
    }
    private var rgbRaw = ByteArray(0)

    override fun start() {
        gravity.reset()
        steps.reset()
        gyro.reset()
        intrinsics = null
        // Most to least capable: (colour) + accel + gyro at the D455's rates, any rate, accel
        // only, then depth only. A plain D435 has no IMU; a USB 2 link may refuse colour.
        val imuAttempts = listOf(
            Triple<Int?, Int?, String>(ACCEL_FPS, GYRO_FPS, "accel 63 Hz + gyro 200 Hz"),
            Triple(0, 0, "accel + gyro, any rate"),
            Triple(0, null, "accel only"),
            Triple(null, null, "no IMU"),
        )
        val colorOptions = if (wantColor && align != null) listOf(true, false) else listOf(false)
        var lastError: Throwable? = null
        for (color in colorOptions) for ((accelFps, gyroFps, label) in imuAttempts) {
            try {
                pipeline = startPipeline(accelFps, gyroFps, color)
                imuEnabled = accelFps != null
                gyroEnabled = gyroFps != null
                colorEnabled = color
                Log.i(TAG, "Streaming depth + $label" + if (color) " + colour" else "")
                return
            } catch (t: Throwable) {
                lastError = t
                Log.i(TAG, "Config '$label', colour=$color refused: ${t.message}")
            }
        }
        throw IllegalStateException("RealSense refused every stream configuration", lastError)
    }

    /** Null fps = stream off, 0 = any rate. */
    private fun startPipeline(accelFps: Int?, gyroFps: Int?, color: Boolean): Pipeline {
        val pipe = Pipeline()
        try {
            Config().use { cfg ->
                cfg.enableStream(StreamType.DEPTH, -1, WIDTH, HEIGHT, StreamFormat.Z16, FPS)
                if (color) cfg.enableStream(StreamType.COLOR, -1, WIDTH, HEIGHT, StreamFormat.valueOf("RGB8"), FPS)
                // Motion types looked up by name so this builds against wrapper versions without them.
                if (accelFps != null || gyroFps != null) {
                    val motion = StreamFormat.valueOf("MOTION_XYZ32F")
                    if (accelFps != null) cfg.enableStream(StreamType.valueOf("ACCEL"), -1, 0, 0, motion, accelFps)
                    if (gyroFps != null) cfg.enableStream(StreamType.valueOf("GYRO"), -1, 0, 0, motion, gyroFps)
                }
                pipe.start(cfg).close()
            }
        } catch (t: Throwable) {
            pipe.close()
            throw t
        }
        return pipe
    }

    override fun read(): DepthImage? {
        val pipe = pipeline ?: return null
        // Motion frames arrive in their own framesets at 63-400 Hz: keep reading, feeding the
        // gravity filter and gyro integrator, until a depth frame arrives.
        val deadline = System.nanoTime() + 1_000_000_000L
        while (System.nanoTime() < deadline) {
            pipe.waitForFrames(1000).use { frames ->
                if (imuEnabled) firstOrNull(frames, StreamType.valueOf("ACCEL"))?.use { readAccel(it) }
                if (gyroEnabled) firstOrNull(frames, StreamType.valueOf("GYRO"))?.use { readGyro(it) }
                val hasDepth = firstOrNull(frames, StreamType.DEPTH)?.also { it.close() } != null
                if (!hasDepth) return@use
                if (colorEnabled) {
                    val hasColor = firstOrNull(frames, StreamType.COLOR)?.also { it.close() } != null
                    if (hasColor) aligned(frames)?.use { set ->
                        val color = firstOrNull(set, StreamType.COLOR)
                        firstOrNull(set, StreamType.DEPTH)?.use { d ->
                            val rgb = color?.use { readRgb(it) }
                            return toDepthImage(d, rgb)
                        }
                        color?.close()
                    }
                }
                firstOrNull(frames, StreamType.DEPTH)?.use { return toDepthImage(it, null) }
            }
        }
        return null
    }

    /** Depending on the wrapper version, first() returns null or throws when a stream is missing. */
    private fun firstOrNull(frames: FrameSet, type: StreamType): Frame? =
        try { frames.first(type) } catch (_: Exception) { null }

    /** Colour re-projected to the depth camera's viewpoint and resolution (FrameSet.applyFilter(Align)). */
    private fun aligned(frames: FrameSet): FrameSet? = try {
        val a = align ?: return null
        val apply = frames.javaClass.methods.first { it.name == "applyFilter" && it.parameterCount == 1 }
        apply.invoke(frames, a) as FrameSet
    } catch (t: Throwable) {
        Log.w(TAG, "Align failed, continuing without colour", t)
        colorEnabled = false
        null
    }

    /** Packed RGB bytes of a colour frame (strips row padding). */
    private fun readRgb(frame: Frame): ByteArray {
        val video: VideoFrame = frame.`as`(Extension.VIDEO_FRAME)
        val w = video.width; val h = video.height; val stride = video.stride
        if (rgbRaw.size != stride * h) rgbRaw = ByteArray(stride * h)
        video.getData(rgbRaw)
        if (stride == w * 3) return rgbRaw.copyOf()
        val out = ByteArray(w * h * 3)
        for (v in 0 until h) System.arraycopy(rgbRaw, v * stride, out, v * w * 3, w * 3)
        return out
    }

    private fun toDepthImage(frame: Frame, rgb: ByteArray?): DepthImage {
        // `as` wraps the same native frame; only the original is closed.
        val video: VideoFrame = frame.`as`(Extension.VIDEO_FRAME)
        val w = video.width
        val h = video.height
        val strideBytes = video.stride
        if (raw.size != strideBytes * h) raw = ByteArray(strideBytes * h)
        video.getData(raw)

        val shorts = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val rowShorts = strideBytes / 2
        val depth = FloatArray(w * h)
        for (v in 0 until h) for (u in 0 until w) {
            depth[v * w + u] = (shorts.get(v * rowShorts + u).toInt() and 0xFFFF) * DEPTH_UNITS
        }
        val k = intrinsics ?: readIntrinsics(frame, w, h).also { intrinsics = it }
        val colour = rgb?.takeIf { it.size == w * h * 3 }  // aligned colour has depth's resolution
        return DepthImage(
            w, h, depth, k[0], k[1], k[2], k[3], gravity.up(), if (gyroEnabled) gyro.take() else null,
            rgb = colour,
            timestampMs = timestamp(frame),
            stepCount = if (imuEnabled) steps.steps else null,
        )
    }

    /**
     * Feeds one accelerometer sample to the gravity filter. Uses reflection because the
     * wrapper's motion-frame API (MotionFrame.getMotionData() -> Float3) varies by version.
     */
    private fun readAccel(frame: Frame) {
        try {
            val m = motionData(frame)
            gravity.add(m[0], m[1], m[2])
            steps.add(m[0], m[1], m[2], timestamp(frame), gravity.up())
        } catch (t: Throwable) {
            Log.w(TAG, "Can't read accelerometer, continuing without IMU", t)
            imuEnabled = false
        }
    }

    /** Feeds one gyroscope sample (rad/s) with its timestamp (ms) to the integrator. */
    private fun readGyro(frame: Frame) {
        try {
            val m = motionData(frame)
            val ts = timestamp(frame)
            gyro.add(m[0], m[1], m[2], ts)
            gravity.addGyro(m[0], m[1], m[2], ts)
        } catch (t: Throwable) {
            Log.w(TAG, "Can't read gyroscope, continuing without it", t)
            gyroEnabled = false
        }
    }

    /** Frame timestamp in ms (Frame.getTimestamp(), via reflection like the other frame metadata). */
    private fun timestamp(frame: Frame): Double = try {
        (frame.javaClass.getMethod("getTimestamp").invoke(frame) as Number).toDouble()
    } catch (_: Throwable) {
        System.nanoTime() / 1e6
    }

    /** x, y, z of a motion frame via MotionFrame.getMotionData() -> Float3 (fields or getters). */
    private fun motionData(frame: Frame): DoubleArray {
        val motion: Frame = frame.`as`(Extension.valueOf("MOTION_FRAME"))
        val data = motion.javaClass.getMethod("getMotionData").invoke(motion)!!
        fun component(name: String): Double {
            val cls = data.javaClass
            val value = try {
                cls.getField(name).get(data)
            } catch (_: NoSuchFieldException) {
                cls.getMethod("get" + name.uppercase()).invoke(data)
            }
            return (value as Number).toDouble()
        }
        return doubleArrayOf(component("x"), component("y"), component("z"))
    }

    override fun stop() {
        pipeline?.let {
            try { it.stop() } catch (t: Throwable) { Log.w(TAG, "stop", t) }
            it.close()
        }
        pipeline = null
    }

    /**
     * fx, fy, cx, cy of the depth stream. Read from the device via reflection because the
     * wrapper's intrinsics API differs between librealsense versions; falls back to the
     * nominal D455 depth field of view (87 x 58 degrees).
     */
    private fun readIntrinsics(frame: Any, w: Int, h: Int): FloatArray {
        try {
            val profile = frame.javaClass.getMethod("getProfile").invoke(frame)!!
            val ext = Class.forName("com.intel.realsense.librealsense.Extension")
            val videoProfileExt = ext.getField("VIDEO_PROFILE").get(null)
            val video = profile.javaClass.getMethod("as", ext).invoke(profile, videoProfileExt)!!
            val intr = video.javaClass.getMethod("getIntrinsic").invoke(video)!!
            fun get(name: String) = (intr.javaClass.getMethod(name).invoke(intr) as Number).toFloat()
            val k = floatArrayOf(get("getFx"), get("getFy"), get("getPpx"), get("getPpy"))
            if (k.all { it.isFinite() } && k[0] > 0 && k[1] > 0) {
                intrinsicsSource = "device"
                return k
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't read intrinsics from device, using nominal FOV", t)
        }
        intrinsicsSource = "nominal"
        val fx = (w / 2.0 / tan(Math.toRadians(87.0 / 2))).toFloat()
        val fy = (h / 2.0 / tan(Math.toRadians(58.0 / 2))).toFloat()
        return floatArrayOf(fx, fy, w / 2f, h / 2f)
    }
}
