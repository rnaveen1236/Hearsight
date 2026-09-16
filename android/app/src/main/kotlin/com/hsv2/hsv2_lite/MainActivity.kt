package com.hsv2.hsv2_lite

import java.io.File
import java.io.FileOutputStream

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val COMMAND_CHANNEL = "com.hsv2/command"
    private val TELEMETRY_CHANNEL = "com.hsv2/telemetry"
    private val USB_STATUS_CHANNEL = "com.hsv2/usb_status"

    private val ACTION_USB_PERMISSION =
        "com.hsv2.hsv2_lite.USB_PERMISSION"

    private val LOG_TAG = "HSV2Lite"

    // ============================================================
    // Flutter SurfaceTextures
    // ============================================================

    private var rgbTextureEntry:
            io.flutter.view.TextureRegistry.SurfaceTextureEntry? = null

    private var rgbSurface:
            android.view.Surface? = null

    private var depthTextureEntry:
            io.flutter.view.TextureRegistry.SurfaceTextureEntry? = null

    private var depthSurface:
            android.view.Surface? = null

    // ============================================================
    // Event / polling state
    // ============================================================

    private var eventSink:
            EventChannel.EventSink? = null

    private var usbStatusSink:
            EventChannel.EventSink? = null

    private val handler =
            Handler(Looper.getMainLooper())

    private var isPolling = false

    private lateinit var usbManager: UsbManager

    // ============================================================
    // Native library
    // ============================================================

    init {
        try {

            System.loadLibrary("hsv2_lite")

            Log.i(
                LOG_TAG,
                "Native hsv2_lite library loaded successfully"
            )

        } catch (e: UnsatisfiedLinkError) {

            Log.e(
                LOG_TAG,
                "Failed to load hsv2_lite native library: ${e.message}",
                e
            )
        }
    }

    // ============================================================
    // Native functions
    // ============================================================

    @Keep
    external fun startPipeline()

    @Keep
    external fun stopPipeline()

    @Keep
    external fun setStage(stage: Int)

    @Keep
    external fun pollTelemetry(): String

    @Keep
    private external fun initRealSenseWithFD(
        fd: Int,
        usbfsPath: String
    ): Boolean

    @Keep
    external fun setNativeWindow(
        surface: android.view.Surface
    )

    @Keep
    external fun releaseNativeWindow()

    @Keep
    external fun setDepthNativeWindow(
        surface: android.view.Surface
    )

    @Keep
    external fun releaseDepthNativeWindow()

    // ============================================================
    // QNN initialization
    // ============================================================

    @Keep
    private external fun initQnn(
        contextBinaryPath: String,
        modelLibraryPath: String
    ): Boolean

    // ============================================================
    // USB Broadcast Receiver
    // ============================================================

    private val usbReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {

                val action = intent.action

                if (ACTION_USB_PERMISSION == action) {

                    synchronized(this) {

                        val device: UsbDevice? =
                            intent.getParcelableExtra(
                                UsbManager.EXTRA_DEVICE
                            )

                        if (
                            intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                            )
                        ) {

                            device?.apply {

                                Log.i(
                                    LOG_TAG,
                                    "USB Permission granted for device: " +
                                            device.deviceName
                                )

                                connectToDevice(this)
                            }

                        } else {

                            Log.e(
                                LOG_TAG,
                                "USB Permission denied for device $device"
                            )

                            notifyUsbStatus(
                                "PERMISSION DENIED"
                            )
                        }
                    }

                } else if (
                    UsbManager.ACTION_USB_DEVICE_ATTACHED ==
                    action
                ) {

                    Log.i(
                        LOG_TAG,
                        "USB Device Attached"
                    )

                    checkUsbDevices()

                } else if (
                    UsbManager.ACTION_USB_DEVICE_DETACHED ==
                    action
                ) {

                    Log.i(
                        LOG_TAG,
                        "USB Device Detached"
                    )

                    notifyUsbStatus(
                        "DISCONNECTED"
                    )
                }
            }
        }

    // ============================================================
    // Flutter Engine
    // ============================================================

    override fun configureFlutterEngine(
        @NonNull flutterEngine: FlutterEngine
    ) {

        super.configureFlutterEngine(
            flutterEngine
        )

        usbManager =
            getSystemService(
                Context.USB_SERVICE
            ) as UsbManager

        // ========================================================
        // USB receiver
        // ========================================================

        val filter =
            IntentFilter(ACTION_USB_PERMISSION)

        filter.addAction(
            UsbManager.ACTION_USB_DEVICE_ATTACHED
        )

        filter.addAction(
            UsbManager.ACTION_USB_DEVICE_DETACHED
        )

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                usbReceiver,
                filter,
                Context.RECEIVER_EXPORTED
            )

        } else {

            registerReceiver(
                usbReceiver,
                filter
            )
        }

        // ========================================================
        // Prepare QNN files
        // ========================================================

        prepareQnnFiles()

        // ========================================================
        // Initialize QNN HTP
        // ========================================================

        initializeQnn()

        // ========================================================
        // Flutter Command Channel
        // ========================================================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            COMMAND_CHANNEL
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                "startPipeline" -> {

                    try {

                        startPipeline()

                    } catch (e: Exception) {

                        Log.e(
                            LOG_TAG,
                            "Native startPipeline failed",
                            e
                        )
                    }

                    result.success(null)
                }

                "stopPipeline" -> {

                    try {

                        stopPipeline()

                    } catch (e: Exception) {

                        Log.e(
                            LOG_TAG,
                            "Native stopPipeline failed",
                            e
                        )
                    }

                    result.success(null)
                }

                "setStage" -> {

                    val stage =
                        call.argument<Int>("stage") ?: 1

                    try {

                        setStage(stage)

                    } catch (e: Exception) {

                        Log.e(
                            LOG_TAG,
                            "Native setStage failed",
                            e
                        )
                    }

                    result.success(null)
                }

                // =================================================
                // RGB texture
                // =================================================

                "createTexture" -> {

                    rgbTextureEntry =
                        flutterEngine.renderer
                            .createSurfaceTexture()

                    val surfaceTexture =
                        rgbTextureEntry!!.surfaceTexture()

                    surfaceTexture.setDefaultBufferSize(
                        640,
                        480
                    )

                    rgbSurface =
                        android.view.Surface(
                            surfaceTexture
                        )

                    setNativeWindow(
                        rgbSurface!!
                    )

                    result.success(
                        rgbTextureEntry!!.id()
                    )
                }

                // =================================================
                // Depth texture
                // =================================================

                "createDepthTexture" -> {

                    depthTextureEntry =
                        flutterEngine.renderer
                            .createSurfaceTexture()

                    val surfaceTexture =
                        depthTextureEntry!!.surfaceTexture()

                    surfaceTexture.setDefaultBufferSize(
                        640,
                        480
                    )

                    depthSurface =
                        android.view.Surface(
                            surfaceTexture
                        )

                    setDepthNativeWindow(
                        depthSurface!!
                    )

                    result.success(
                        depthTextureEntry!!.id()
                    )
                }

                // =================================================
                // USB check
                // =================================================

                "checkUsb" -> {

                    checkUsbDevices()

                    result.success(null)
                }

                else -> {

                    result.notImplemented()
                }
            }
        }

        // ========================================================
        // Telemetry Event Channel
        // ========================================================

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            TELEMETRY_CHANNEL
        ).setStreamHandler(

            object : EventChannel.StreamHandler {

                override fun onListen(
                    arguments: Any?,
                    events: EventChannel.EventSink?
                ) {

                    eventSink = events

                    isPolling = true

                    startPolling()
                }

                override fun onCancel(
                    arguments: Any?
                ) {

                    eventSink = null

                    isPolling = false
                }
            }
        )

        // ========================================================
        // USB Status Event Channel
        // ========================================================

        EventChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            USB_STATUS_CHANNEL
        ).setStreamHandler(

            object : EventChannel.StreamHandler {

                override fun onListen(
                    arguments: Any?,
                    events: EventChannel.EventSink?
                ) {

                    usbStatusSink = events

                    checkUsbDevices()
                }

                override fun onCancel(
                    arguments: Any?
                ) {

                    usbStatusSink = null
                }
            }
        )

        Log.i(
            LOG_TAG,
            "MainActivity Flutter engine configured"
        )
    }

    // ============================================================
    // QNN file preparation
    // ============================================================

    private fun prepareQnnFiles() {

        try {

            val contextFile =
                prepareQnnContextBinary()

            val modelFile =
                getQnnModelLibraryPath()

            Log.i(
                LOG_TAG,
                "QNN context prepared: ${contextFile.absolutePath}"
            )

            Log.i(
                LOG_TAG,
                "QNN model library: ${modelFile.absolutePath}"
            )

            if (!contextFile.exists()) {

                Log.e(
                    LOG_TAG,
                    "QNN context binary does not exist"
                )
            }

            if (!modelFile.exists()) {

                Log.e(
                    LOG_TAG,
                    "QNN model library does not exist"
                )
            }

        } catch (e: Exception) {

            Log.e(
                LOG_TAG,
                "Failed to prepare QNN files",
                e
            )
        }
    }

    // ============================================================
    // Copy QNN HTP context binary from assets
    // ============================================================

    private fun prepareQnnContextBinary(): File {

        val qnnDir =
            File(
                filesDir,
                "qnn"
            )

        if (!qnnDir.exists()) {

            qnnDir.mkdirs()
        }

        val outputFile =
            File(
                qnnDir,
                "yolov8s_int8_sm8850.bin.SM8850.bin"
            )

        if (
            !outputFile.exists() ||
            outputFile.length() == 0L
        ) {

            Log.i(
                LOG_TAG,
                "Copying QNN context binary from assets..."
            )

            assets.open(
                "qnn/yolov8s_int8_sm8850.bin.SM8850.bin"
            ).use { input ->

                FileOutputStream(
                    outputFile
                ).use { output ->

                    val buffer =
                        ByteArray(8192)

                    var read: Int

                    while (
                        input.read(buffer)
                            .also { read = it } != -1
                    ) {

                        output.write(
                            buffer,
                            0,
                            read
                        )
                    }

                    output.flush()
                }
            }

            Log.i(
                LOG_TAG,
                "QNN context binary copied successfully"
            )

        } else {

            Log.i(
                LOG_TAG,
                "QNN context binary already exists"
            )
        }

        return outputFile
    }

    // ============================================================
    // QNN model shared library
    // ============================================================

    private fun getQnnModelLibraryPath(): File {

        val modelFile =
            File(
                applicationInfo.nativeLibraryDir,
                "libyolov8s_int8.so"
            )

        Log.i(
            LOG_TAG,
            "QNN model library path: ${modelFile.absolutePath}"
        )

        return modelFile
    }

    // ============================================================
    // Initialize QNN HTP
    // ============================================================

    private fun initializeQnn() {

        try {

            val contextBinary =
                prepareQnnContextBinary()

            val modelLibrary =
                getQnnModelLibraryPath()

            Log.i(
                LOG_TAG,
                "----------------------------------------"
            )

            Log.i(
                LOG_TAG,
                "Starting QNN HTP initialization"
            )

            Log.i(
                LOG_TAG,
                "Context: ${contextBinary.absolutePath}"
            )

            Log.i(
                LOG_TAG,
                "Context size: ${contextBinary.length()} bytes"
            )

            Log.i(
                LOG_TAG,
                "Model: ${modelLibrary.absolutePath}"
            )

            Log.i(
                LOG_TAG,
                "Model size: ${modelLibrary.length()} bytes"
            )

            if (!contextBinary.exists()) {

                Log.e(
                    LOG_TAG,
                    "QNN context binary NOT FOUND"
                )

                return
            }

            // QNN model is packaged as an Android native library.
            // Do not use File.exists() for the native-library path.
            // Native QNN loads it by SONAME using dlopen().
            Log.i(
                LOG_TAG,
                "QNN model library packaged: ${modelLibrary.absolutePath}"
            )

            val success =
                initQnn(
                    contextBinary.absolutePath,
                    "libyolov8s_int8.so"
                )

            if (success) {

                Log.i(
                    LOG_TAG,
                    "========================================"
                )

                Log.i(
                    LOG_TAG,
                    "QNN HTP INITIALIZATION SUCCESS"
                )

                Log.i(
                    LOG_TAG,
                    "========================================"
                )

            } else {

                Log.e(
                    LOG_TAG,
                    "========================================"
                )

                Log.e(
                    LOG_TAG,
                    "QNN HTP INITIALIZATION FAILED"
                )

                Log.e(
                    LOG_TAG,
                    "========================================"
                )
            }

        } catch (e: Exception) {

            Log.e(
                LOG_TAG,
                "QNN initialization exception",
                e
            )
        }
    }

    // ============================================================
    // Activity destroy
    // ============================================================

    override fun onDestroy() {

        try {

            stopPipeline()

        } catch (e: Exception) {

            Log.e(
                LOG_TAG,
                "Error stopping pipeline",
                e
            )
        }

        try {

            unregisterReceiver(
                usbReceiver
            )

        } catch (e: Exception) {

            Log.w(
                LOG_TAG,
                "USB receiver already unregistered"
            )
        }

        super.onDestroy()
    }

    // ============================================================
    // USB status
    // ============================================================

    private fun notifyUsbStatus(
        status: String
    ) {

        handler.post {

            usbStatusSink?.success(
                status
            )
        }
    }

    // ============================================================
    // Check USB devices
    // ============================================================

    private fun checkUsbDevices() {

        val deviceList =
            usbManager.deviceList

        var foundCamera = false

        for (device in deviceList.values) {

            // Intel RealSense Vendor ID
            // 0x8086 = 32902

            if (
                device.vendorId == 0x8086 ||
                device.vendorId == 32902
            ) {

                foundCamera = true

                Log.i(
                    LOG_TAG,
                    "Found Intel RealSense device"
                )

                if (
                    usbManager.hasPermission(
                        device
                    )
                ) {

                    connectToDevice(
                        device
                    )

                } else {

                    Log.i(
                        LOG_TAG,
                        "Requesting USB permission"
                    )

                    notifyUsbStatus(
                        "REQUESTING PERMISSION"
                    )

                    val flags =
                        if (
                            Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.S
                        ) {

                            PendingIntent.FLAG_MUTABLE

                        } else {

                            0
                        }

                    val intent =
                        Intent(
                            ACTION_USB_PERMISSION
                        )

                    intent.setPackage(
                        packageName
                    )

                    val permissionIntent =
                        PendingIntent.getBroadcast(
                            this,
                            0,
                            intent,
                            flags
                        )

                    usbManager.requestPermission(
                        device,
                        permissionIntent
                    )
                }

                break
            }
        }

        if (!foundCamera) {

            if (deviceList.isEmpty()) {

                notifyUsbStatus(
                    "DISCONNECTED (0 devices)"
                )

            } else {

                val vids =
                    deviceList.values
                        .joinToString {
                            it.vendorId.toString()
                        }

                notifyUsbStatus(
                    "DISCONNECTED (VIDs: $vids)"
                )
            }
        }
    }

    // ============================================================
    // Connect to D455
    // ============================================================

    private fun connectToDevice(
        device: UsbDevice
    ) {

        val connection =
            usbManager.openDevice(
                device
            )

        if (connection != null) {

            val fd =
                connection.fileDescriptor

            val usbfsPath =
                device.deviceName

            Log.i(
                LOG_TAG,
                "USB Device opened successfully. " +
                        "FD: $fd, Path: $usbfsPath"
            )

            try {

                val success =
                    initRealSenseWithFD(
                        fd,
                        usbfsPath
                    )

                if (success) {

                    notifyUsbStatus(
                        "CONNECTED"
                    )

                } else {

                    Log.e(
                        LOG_TAG,
                        "C++ initRealSenseWithFD failed"
                    )

                    notifyUsbStatus(
                        "OPEN FAILED"
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    LOG_TAG,
                    "Native initRealSenseWithFD call failed",
                    e
                )

                notifyUsbStatus(
                    "OPEN FAILED"
                )
            }

        } else {

            Log.e(
                LOG_TAG,
                "Failed to open USB device"
            )

            notifyUsbStatus(
                "OPEN FAILED"
            )
        }
    }

    // ============================================================
    // Telemetry polling
    // ============================================================

    private fun startPolling() {

        handler.post(
            object : Runnable {

                override fun run() {

                    if (isPolling) {

                        try {

                            val telemetryJson =
                                pollTelemetry()

                            if (
                                telemetryJson != "{}"
                            ) {

                                eventSink?.success(
                                    telemetryJson
                                )
                            }

                        } catch (e: Exception) {

                            Log.e(
                                LOG_TAG,
                                "Native pollTelemetry failed",
                                e
                            )
                        }

                        // Poll at approximately 10 Hz

                        handler.postDelayed(
                            this,
                            100
                        )
                    }
                }
            }
        )
    }
}
