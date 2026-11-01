package com.gatenoc.bkashsync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * Single place for all sending logic. Used by the SMS receiver, the foreground service,
 * the periodic worker and the app buttons. @Synchronized keeps two paths from sending the same SMS.
 */
object SyncEngine {

    private fun configured(c: Context): Boolean =
        AppStorage.getEndpoint(c).startsWith("https://") && AppStorage.getDeviceKey(c).length >= 16

    /** Sends one record. Returns true if the server accepted it. */
    @Synchronized
    fun uploadOne(c: Context, r: SmsRecord): Boolean {
        val app = c.applicationContext
        if (!configured(app)) {
            AppStorage.updateStatus(app, r.id, St.PENDING, "Server not set up yet")
            return false
        }
        val res = try {
            ApiClient.uploadSms(AppStorage.getEndpoint(app), AppStorage.getDeviceKey(app), r)
        } catch (e: Exception) {
            ApiResult(0, false, "error", e.message ?: "error")
        }
        val accepted = res.success || res.httpCode in 200..299
        when {
            accepted && res.status.equals("flagged", ignoreCase = true) -> {
                AppStorage.updateStatus(app, r.id, St.FLAGGED, "Flagged for admin review")
            }
            accepted -> {
                AppStorage.updateStatus(app, r.id, St.SENT, "On server, waiting for customer")
                AppStorage.setLastSync(app, System.currentTimeMillis())
            }
            res.httpCode in 400..499 -> {
                AppStorage.updateStatus(app, r.id, St.FAILED, "HTTP ${res.httpCode}: ${res.message}")
            }
            else -> {
                // network down or server error: keep Pending, it will be retried
                AppStorage.updateStatus(app, r.id, St.PENDING, res.message.ifEmpty { "Will retry" })
            }
        }
        AppStorage.noteSeen(app, "Sent: ${r.trxId} (${if (accepted) "ok" else "retry later"})")
        NotificationHelper.refresh(app)
        return accepted
    }

    /** Reads the phone's SMS inbox (last 2 hours) and picks up any official bKash payment SMS not stored yet. */
    @Synchronized
    fun scanInbox(c: Context) {
        val app = c.applicationContext
        if (!AppStorage.isLoggedIn(app)) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return

        val since = System.currentTimeMillis() - 2 * 3600_000L
        val cursor = (try {
            app.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date >= ?",
                arrayOf(since.toString()),
                "date DESC"
            )
        } catch (e: Exception) {
            null
        }) ?: return

        var checked = 0
        cursor.use { cur ->
            while (cur.moveToNext() && checked < 50) {
                checked++
                val address = cur.getString(0) ?: ""
                val body = cur.getString(1) ?: ""
                if (!SmsParser.isStrictOfficialBkashHeader(address)) continue
                val rec = SmsParser.parseIncomingOrManual(address, body, "Inbox") ?: continue
                if (AppStorage.insertIfNew(app, rec)) {
                    AppStorage.noteSeen(app, "Found in inbox: ${rec.trxId}")
                    uploadOne(app, rec)
                }
            }
        }
    }

    /** Sends everything still Pending (network was down, server was down, etc.). */
    @Synchronized
    fun retryPending(c: Context) {
        val app = c.applicationContext
        if (!configured(app)) return
        for (r in AppStorage.getRecords(app).filter { it.status == St.PENDING }) {
            uploadOne(app, r)
        }
    }

    /** Asks the server which Sent records were used on the website, and updates them to Success. */
    @Synchronized
    fun pollStatuses(c: Context) {
        val app = c.applicationContext
        if (!configured(app)) return
        val sent = AppStorage.getRecords(app).filter { it.status == St.SENT }.take(50)
        if (sent.isEmpty()) return
        val map = ApiClient.checkStatuses(
            AppStorage.getEndpoint(app), AppStorage.getDeviceKey(app), sent.map { it.trxId }
        ) ?: return
        for (r in sent) {
            when (map[r.trxId.uppercase()]) {
                "used" -> {
                    AppStorage.updateStatus(app, r.id, St.SUCCESS, "Used on website")
                    AppStorage.noteSeen(app, "Used on website: ${r.trxId}")
                }
                "flagged" -> AppStorage.updateStatus(app, r.id, St.FLAGGED, "Flagged for admin review")
            }
        }
        NotificationHelper.refresh(app)
    }

    /** One full pass. Safe to call from any thread. */
    fun syncAll(c: Context) {
        val app = c.applicationContext
        scanInbox(app)
        retryPending(app)
        pollStatuses(app)
        AppStorage.runCleanup(app)
    }
}
