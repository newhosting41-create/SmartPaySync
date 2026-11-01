package com.gatenoc.bkashsync

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Foreground service (dataSync). Keeps the app alive with a persistent notification and:
 *  - watches the SMS database with a ContentObserver and syncs right after a change
 *  - runs a loop every 15 s: inbox scan, retry Pending, check "used" status
 */
class SyncForegroundService : Service() {

    companion object {
        const val ACTION_RETRY_NOW = "com.gatenoc.bkashsync.RETRY_NOW"

        fun start(c: Context, retryNow: Boolean = false) {
            if (!AppStorage.isLoggedIn(c) || !AppStorage.isBackgroundOn(c)) return
            val i = Intent(c, SyncForegroundService::class.java)
            if (retryNow) i.action = ACTION_RETRY_NOW
            try {
                c.startForegroundService(i)
            } catch (_: Exception) {
                // Android may refuse from the background; the next receiver / app open will start it.
            }
        }

        fun stop(c: Context) {
            AppStorage.setBackgroundOn(c, false)
            try {
                c.stopService(Intent(c, SyncForegroundService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    private var loop: ScheduledExecutorService? = null
    private var observer: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        val first = NotificationHelper.build(this, "Starting...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NotificationHelper.NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotificationHelper.NOTIF_ID, first)
        }

        // Create the loop first, the observer may fire at any time after registration.
        loop = Executors.newSingleThreadScheduledExecutor()
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                loop?.schedule(Runnable { runSafely() }, 800, TimeUnit.MILLISECONDS)
            }
        }
        try {
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, observer!!)
        } catch (_: Exception) {
        }
        // A single exception inside scheduleWithFixedDelay would stop the schedule forever,
        // so runSafely() catches everything.
        loop!!.scheduleWithFixedDelay(Runnable { runSafely() }, 2, 15, TimeUnit.SECONDS)
    }

    private fun runSafely() {
        try {
            Wake.hold(this, 90_000L) { SyncEngine.syncAll(applicationContext) }
        } catch (_: Exception) {
        }
        NotificationHelper.refresh(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RETRY_NOW) {
            loop?.execute { runSafely() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            observer?.let { contentResolver.unregisterContentObserver(it) }
        } catch (_: Exception) {
        }
        loop?.shutdownNow()
        loop = null
        super.onDestroy()
        // If the system killed us but background mode is still on, come back up.
        if (AppStorage.isLoggedIn(this) && AppStorage.isBackgroundOn(this)) start(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
