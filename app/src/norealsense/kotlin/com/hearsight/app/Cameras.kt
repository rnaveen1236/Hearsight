package com.hearsight.app

import android.content.Context
import com.hearsight.navcore.DepthImage
import com.hearsight.navcore.DepthSource

/**
 * Built without app/libs/librealsense.aar: only demo mode is available.
 * Run scripts/build_librealsense_aar.sh and rebuild to enable the camera.
 */
object Cameras {
    const val AVAILABLE = false

    fun realSense(context: Context, onConnection: (Boolean) -> Unit): RealSenseCamera = RealSenseCamera()
}

class RealSenseCamera : DepthSource {
    val intrinsicsSource = "n/a"
    fun isConnected() = false
    override fun start() {
        throw IllegalStateException("App built without librealsense.aar")
    }
    override fun read(): DepthImage? = null
    override fun stop() {}
}
