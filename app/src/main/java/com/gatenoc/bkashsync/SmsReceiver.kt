package com.gatenoc.bkashsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

/**
 * Fires the moment an SMS arrives, even when the app is closed.
 * Order matters: the foreground service starts first so the process stays alive,
 * then the SMS is saved and uploaded on a background thread with goAsync() + WakeLock.
 */
class SmsReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext
        if (!AppStorage.isLoggedIn(app)) return

        var sender = ""
        val body = StringBuilder()
        val msgs = try { Telephony.Sms.Intents.getMessagesFromIntent(intent) } catch (_: Exception) { null }
        if (!msgs.isNullOrEmpty()) {
            for (m in msgs) {
                if (sender.isEmpty()) sender = m.displayOriginatingAddress ?: m.originatingAddress ?: ""
                body.append(m.displayMessageBody ?: m.messageBody ?: "")
            }
        } else {
            val pdus = intent.extras?.get("pdus") as? Array<*> ?: return
            for (pdu in pdus) {
                val m = SmsMessage.createFromPdu(pdu as? ByteArray ?: continue) ?: continue
                if (sender.isEmpty()) sender = m.displayOriginatingAddress ?: ""
                body.append(m.displayMessageBody ?: "")
            }
        }

        if (!SmsParser.isStrictOfficialBkashHeader(sender)) return
        val record = SmsParser.parseIncomingOrManual(sender, body.toString(), "Auto") ?: return

        // 1) Keep the process alive first.
        SyncForegroundService.start(app)

        // 2) Save (dedupe). Stop here if this TrxID was already handled by the inbox scan.
        if (!AppStorage.insertIfNew(app, record)) return
        AppStorage.noteSeen(app, "Payment SMS received: ${record.trxId}")
        NotificationHelper.refresh(app)

        // 3) Upload on a background thread; goAsync keeps the receiver alive until finish().
        val pending = goAsync()
        Thread {
            try {
                Wake.hold(app, 60_000L) { SyncEngine.uploadOne(app, record) }
            } catch (_: Exception) {
                // Stays Pending; the service retry loop will send it.
            } finally {
                pending.finish()
            }
        }.start()
    }
}
