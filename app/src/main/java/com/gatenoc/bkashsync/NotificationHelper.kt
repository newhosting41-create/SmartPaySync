package com.gatenoc.bkashsync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "smartpay_service"
    const val NOTIF_ID = 7601

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Payment sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps SmartPay Sync running in the background"
                setShowBadge(false)
            }
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun build(c: Context, line: String): Notification {
        ensureChannel(c)
        val (success, waiting, failed) = AppStorage.countByGroup(c)
        val big = "$line\nSuccess: $success  |  Waiting: $waiting  |  Failed: $failed"
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(c, CHANNEL_ID)
            .setContentTitle("SmartPay Sync is running")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setSmallIcon(R.drawable.ic_notify)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    /** Updates the ongoing notification. Does nothing when background mode is off. */
    fun refresh(c: Context, line: String? = null) {
        if (!AppStorage.isBackgroundOn(c) || !AppStorage.isLoggedIn(c)) return
        try {
            val text = line ?: AppStorage.seenText(c).ifEmpty { "Watching for bKash payment SMS" }
            c.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(c, text))
        } catch (_: Exception) {
        }
    }
}
