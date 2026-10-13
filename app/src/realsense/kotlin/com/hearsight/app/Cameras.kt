package com.hearsight.app

import android.content.Context
import android.util.Log
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.DepthSource
import com.hearsight.navcore.GravityFilter
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
 * Intel RealSense D4xx over USB-OTG using Intel's Android wrapper.
 *
 * 424x240 depth @ 30 fps fits in USB 2.0 bandwidth too, so it works even on phones (or
 * cables) without USB 3. No colour stream: the pipeline only needs depth.
 *
 * On cameras with an IMU (D435i, D455) the accelerometer stream is enabled too and filtered
 * into a gravity "up" vector attached to each depth frame, so the floor's tilt is known.
 * librealsense reports IMU data in the depth camera's axes (x right, y down, z forward).
 */
class RealSenseCamera(context: Context, private val onConnection: (Boolean) -> Unit) : DepthSource {
    companion object {
        const val WIDTH = 424
        const val HEIGHT = 240
        const val FPS = 30
        const val DEPTH_UNITS = 0.001f   // D4xx default: 1 mm per unit
        private const val TAG = "RealSense"
    }

    private val rsContext: RsContext
    private var pipeline: Pipeline? = null
    private var raw = ByteArray(0)
    private var intrinsics: FloatArray? = null
    var intrinsicsSource = "unknown"
        private set

    private val gravity = GravityFilter()
    private var imuEnabled = false
    /** For the status line: whether the IMU is streaming and its current up vector. */
    val imuStatus: String
        get() = when {
            !imuEnabled -> "no IMU"
            else -> gravity.up()?.let { "IMU up (%.2f, %.2f, %.2f)".format(it[0], it[1], it[2]) } ?: "IMU settling"
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

    override fun start() {
        gravity.reset()
        intrinsics = null
        // Try depth + accelerometer; cameras without an IMU (plain D435) refuse that config.
        pipeline = try {
            startPipeline(withImu = true).also { imuEnabled = true }
        } catch (t: Throwable) {
            Log.i(TAG, "No accelerometer stream, depth only: ${t.message}")
            imuEnabled = false
            startPipeline(withImu = false)
        }
    }

    private fun startPipeline(withImu: Boolean): Pipeline {
        val pipe = Pipeline()
        try {
            Config().use { cfg ->
                cfg.enableStream(StreamType.DEPTH, -1, WIDTH, HEIGHT, StreamFormat.Z16, FPS)
                if (withImu) {
                    // Looked up by name so this still builds against wrapper versions without them.
                    cfg.enableStream(
                        StreamType.valueOf("ACCEL"), -1, 0, 0, StreamFormat.valueOf("MOTION_XYZ32F"), 0,
                    )
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
        // Motion frames arrive in their own framesets at 63-250 Hz: keep reading, feeding the
        // gravity filter, until a depth frame arrives.
        val deadline = System.nanoTime() + 1_000_000_000L
        while (System.nanoTime() < deadline) {
            pipe.waitForFrames(1000).use { frames ->
                if (imuEnabled) firstOrNull(frames, StreamType.valueOf("ACCEL"))?.use { readAccel(it) }
                firstOrNull(frames, StreamType.DEPTH)?.use { return toDepthImage(it) }
            }
        }
        return null
    }

    /** Depending on the wrapper version, first() returns null or throws when a stream is missing. */
    private fun firstOrNull(frames: FrameSet, type: StreamType): Frame? =
        try { frames.first(type) } catch (_: Exception) { null }

    private fun toDepthImage(frame: Frame): DepthImage {
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
        return DepthImage(w, h, depth, k[0], k[1], k[2], k[3], gravity.up())
    }

    /**
     * Feeds one accelerometer sample to the gravity filter. Uses reflection because the
     * wrapper's motion-frame API (MotionFrame.getMotionData() -> Float3) varies by version.
     */
    private fun readAccel(frame: Frame) {
        try {
            val ext = Extension.valueOf("MOTION_FRAME")
            val motion: Frame = frame.`as`(ext)
            val data = motion.javaClass.getMethod("getMotionData").invoke(motion) ?: return
            fun component(name: String): Double {
                val cls = data.javaClass
                val value = try {
                    cls.getField(name).get(data)
                } catch (_: NoSuchFieldException) {
                    cls.getMethod("get" + name.uppercase()).invoke(data)
                }
                return (value as Number).toDouble()
            }
            gravity.add(component("x"), component("y"), component("z"))
        } catch (t: Throwable) {
            Log.w(TAG, "Can't read accelerometer, continuing without IMU", t)
            imuEnabled = false
        }
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
     * nominal D435/D455 depth field of view (87 x 58 degrees).
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
