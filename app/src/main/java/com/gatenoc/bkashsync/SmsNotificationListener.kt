package com.gatenoc.bkashsync

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional trigger. When a bKash-looking notification appears, run an inbox scan immediately.
 * The sender is still verified from the real SMS database, never from the notification text.
 */
class SmsNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return
        if (!AppStorage.isLoggedIn(this)) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        if (title.equals("bKash", ignoreCase = true) || text.contains("TrxID", ignoreCase = true)) {
            SyncForegroundService.start(applicationContext, retryNow = true)
        }
    }
}
