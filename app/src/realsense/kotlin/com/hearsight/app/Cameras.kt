package com.hearsight.app

import android.content.Context
import android.util.Log
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.DepthSource
import com.intel.realsense.librealsense.Config
import com.intel.realsense.librealsense.DeviceListener
import com.intel.realsense.librealsense.Extension
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
        val pipe = Pipeline()
        Config().use { cfg ->
            cfg.enableStream(StreamType.DEPTH, -1, WIDTH, HEIGHT, StreamFormat.Z16, FPS)
            pipe.start(cfg).close()
        }
        pipeline = pipe
        intrinsics = null
    }

    override fun read(): DepthImage? {
        val pipe = pipeline ?: return null
        pipe.waitForFrames(1000).use { frames ->
            frames.first(StreamType.DEPTH).use { frame ->
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
                return DepthImage(w, h, depth, k[0], k[1], k[2], k[3])
            }
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
