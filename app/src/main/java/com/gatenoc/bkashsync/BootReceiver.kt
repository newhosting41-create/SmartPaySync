package com.gatenoc.bkashsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After a reboot or app update, bring the background service and the periodic worker back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val wanted = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!wanted || !AppStorage.isLoggedIn(context)) return
        SyncWorker.schedule(context)
        SyncForegroundService.start(context, retryNow = true)
    }
}
