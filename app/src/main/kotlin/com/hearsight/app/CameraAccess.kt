package com.hearsight.app

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

/**
 * Access to the RealSense camera. Android needs two grants before an app may open it:
 *
 * 1. The **CAMERA** runtime permission: the D455 is a USB video-class (UVC) camera, and since
 *    Android 9 the system refuses USB access to cameras for apps without it.
 * 2. **USB permission** for the device itself, asked through [UsbManager.requestPermission]
 *    (or granted when the user ticks "Always open" after plugging the camera in).
 */
object CameraAccess {
    /** Intel Corporation, the RealSense vendor (also in res/xml/device_filter.xml). */
    const val INTEL_VENDOR_ID = 0x8086
    private const val ACTION_USB_PERMISSION = "com.hearsight.app.USB_PERMISSION"

    enum class State(val label: String) {
        NO_CAMERA_PERMISSION("needs camera permission"),
        NO_DEVICE("RealSense not plugged in"),
        NO_USB_PERMISSION("needs USB permission"),
        GRANTED("granted"),
    }

    fun hasCameraPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** The plugged-in RealSense, if any. */
    fun device(context: Context): UsbDevice? =
        usb(context).deviceList.values.firstOrNull { it.vendorId == INTEL_VENDOR_ID }

    fun state(context: Context): State {
        if (!hasCameraPermission(context)) return State.NO_CAMERA_PERMISSION
        val d = device(context) ?: return State.NO_DEVICE
        return if (usb(context).hasPermission(d)) State.GRANTED else State.NO_USB_PERMISSION
    }

    /**
     * Asks Android for USB permission for [device]; [onResult] runs on the main thread with
     * the user's answer. Returns immediately (true) if permission is already held.
     */
    fun requestUsbPermission(context: Context, device: UsbDevice, onResult: (Boolean) -> Unit) {
        val manager = usb(context)
        if (manager.hasPermission(device)) {
            onResult(true)
            return
        }
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION_USB_PERMISSION) return
                app.unregisterReceiver(this)
                onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
            }
        }
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        // Explicit (package-bound) intent; mutable because the system adds the device and
        // the result as extras.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(app.packageName)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        manager.requestPermission(device, PendingIntent.getBroadcast(app, 0, intent, flags))
    }

    private fun usb(context: Context) = context.getSystemService(UsbManager::class.java)
}
