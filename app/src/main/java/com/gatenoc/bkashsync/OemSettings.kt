package com.gatenoc.bkashsync

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the phone's own auto-start / background screen when we know its name.
 * Names differ between phone versions, so each one is tried safely.
 * If none opens, the normal App info page is shown.
 */
object OemSettings {

    private val candidates = listOf(
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.transsion.phonemaster", "com.cyou.quick.business.ui.activity.AutoStartActivity"),
        ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.permission.AutoStartActivity")
    )

    /** true = a vendor screen opened; false = App info was opened instead. */
    fun openAutostart(c: Context): Boolean {
        for (cn in candidates) {
            try {
                c.startActivity(Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:" + c.packageName)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(i)
        } catch (_: Exception) {
        }
        return false
    }
}
