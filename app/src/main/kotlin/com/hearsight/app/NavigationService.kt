package com.hearsight.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Foreground service that keeps guidance running with the screen off or the phone in a pocket:
 * an ongoing notification (with a Stop button) and a partial wake lock keep the CPU, camera
 * and feedback going. The work itself is done by [NavController].
 */
class NavigationService : Service() {
    companion object {
        private const val ACTION_START = "com.hearsight.app.action.START"
        private const val ACTION_STOP = "com.hearsight.app.action.STOP"
        private const val EXTRA_DEMO = "demo"
        private const val EXTRA_OUTDOOR = "outdoor"
        private const val CHANNEL = "guidance"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "NavigationService"

        fun start(context: Context, demo: Boolean, outdoor: Boolean) {
            val intent = Intent(context, NavigationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DEMO, demo)
                .putExtra(EXTRA_OUTDOOR, outdoor)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, NavigationService::class.java).setAction(ACTION_STOP))
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val demo = intent.getBooleanExtra(EXTRA_DEMO, false)
                val outdoor = intent.getBooleanExtra(EXTRA_OUTDOOR, false)
                NavController.init(this)
                // Must be in the foreground within a few seconds of startForegroundService().
                goForeground(demo)
                val error = NavController.start(demo, outdoor)
                if (error != null) {
                    NavController.report(error)
                    shutdown()
                } else {
                    acquireWakeLock()
                }
            }
            ACTION_STOP -> shutdown()
            else -> shutdown()  // restarted by the system without a command: nothing to resume
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        NavController.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun shutdown() {
        NavController.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(demo: Boolean) {
        val notification = buildNotification(demo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The camera is a connected USB device. Demo mode has none, which Android 14+
            // rejects for that type, so it is declared as a special use instead.
            val type = if (demo && Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
            try {
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                Log.w(TAG, "Foreground type $type refused, retrying as special use", e)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    throw e
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(demo: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Guidance", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while HearSight is guiding"
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, NavigationService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("HearSight is guiding")
            .setContentText(if (demo) "Demo mode (simulated camera)" else "Works with the screen off. Tap Stop to end.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HearSight:guidance")
            .apply { acquire(4 * 60 * 60 * 1000L) }  // safety cap: 4 hours
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
