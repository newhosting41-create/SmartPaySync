package com.gatenoc.bkashsync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (isAdded) {
                renderInfo()
                ui.postDelayed(this, 4000)
            }
        }
    }
    private val cleanupHours = intArrayOf(0, 24, 168)

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val etEndpoint = v.findViewById<EditText>(R.id.etEndpoint)
        val etToken = v.findViewById<EditText>(R.id.etToken)
        etEndpoint.setText(AppStorage.getEndpoint(ctx))
        etToken.setText(AppStorage.getDeviceKey(ctx))

        v.findViewById<View>(R.id.btnSave).setOnClickListener {
            val ep = etEndpoint.text.toString().trim()
            val tk = etToken.text.toString().trim()
            when {
                !ep.startsWith("https://") -> toast("Server URL must start with https://")
                tk.length < 16 -> toast("Device Token looks too short")
                else -> {
                    AppStorage.saveLoginConfig(ctx, ep, tk)
                    AppStorage.setBackgroundOn(ctx, true)
                    v.findViewById<SwitchMaterial>(R.id.swBackground).isChecked = true
                    SyncForegroundService.start(ctx, retryNow = true)
                    SyncWorker.schedule(ctx)
                    toast("Saved")
                    renderInfo()
                }
            }
        }

        v.findViewById<View>(R.id.btnTest).setOnClickListener {
            val app = ctx.applicationContext
            toast("Testing connection...")
            Thread {
                val r = ApiClient.pingServer(AppStorage.getEndpoint(app), AppStorage.getDeviceKey(app))
                AppStorage.setServerMsg(app, if (r.success) "OK" else "Failed: ${r.message}")
                ui.post {
                    if (isAdded) {
                        toast(if (r.success) "Connected" else r.message)
                        renderInfo()
                    }
                }
            }.start()
        }

        v.findViewById<View>(R.id.btnDisconnect).setOnClickListener {
            AppStorage.logout(ctx)
            SyncForegroundService.stop(ctx)
            etEndpoint.setText("")
            etToken.setText("")
            v.findViewById<SwitchMaterial>(R.id.swBackground).isChecked = false
            toast("Disconnected")
            renderInfo()
        }

        val sw = v.findViewById<SwitchMaterial>(R.id.swBackground)
        sw.isChecked = AppStorage.isBackgroundOn(ctx)
        sw.setOnCheckedChangeListener { _, on ->
            if (on) {
                AppStorage.setBackgroundOn(ctx, true)
                SyncForegroundService.start(ctx, retryNow = true)
            } else {
                SyncForegroundService.stop(ctx)
            }
            renderInfo()
        }

        v.findViewById<View>(R.id.btnPermission).setOnClickListener {
            val need = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
            ActivityCompat.requestPermissions(requireActivity(), need.toTypedArray(), 1)
        }

        v.findViewById<View>(R.id.btnBattery).setOnClickListener {
            try {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                i.data = Uri.parse("package:" + ctx.packageName)
                startActivity(i)
            } catch (_: Exception) {
                toast("Open phone Settings > Battery and set Unrestricted")
            }
        }

        v.findViewById<View>(R.id.btnAutostart).setOnClickListener {
            if (!OemSettings.openAutostart(ctx)) toast("Opened App info. Find Auto-start there.")
        }

        val spinner = v.findViewById<Spinner>(R.id.spCleanup)
        spinner.adapter = ArrayAdapter(
            ctx, android.R.layout.simple_spinner_dropdown_item,
            listOf("Keep forever", "Delete used records after 1 day", "Delete used records after 7 days")
        )
        val current = cleanupHours.indexOf(AppStorage.getCleanupHours(ctx)).coerceAtLeast(0)
        spinner.setSelection(current, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                AppStorage.setCleanupHours(ctx, cleanupHours[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onResume() {
        super.onResume()
        renderInfo()
        ui.postDelayed(tick, 4000)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun renderInfo() {
        val v = view ?: return
        val ctx = requireContext()

        val perm = v.findViewById<LinearLayout>(R.id.permBox)
        perm.removeAllViews()
        val sms = hasSmsPermission(ctx)
        perm.addView(statusRow(ctx, "SMS (receive + read)", if (sms) "Granted" else "Missing", if (sms) 0 else 2))
        val notif = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        perm.addView(statusRow(ctx, "Notifications", if (notif) "Allowed" else "Blocked", if (notif) 0 else 1))
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        perm.addView(statusRow(ctx, "Battery", if (unrestricted) "Unrestricted" else "Restricted", if (unrestricted) 0 else 1))
        val bg = AppStorage.isBackgroundOn(ctx)
        perm.addView(statusRow(ctx, "Background mode", if (bg) "On" else "Off", if (bg) 0 else 2))

        val diag = v.findViewById<LinearLayout>(R.id.diagBox)
        diag.removeAllViews()
        val seen = AppStorage.seenAt(ctx)
        val seenLine = if (seen == 0L) "None yet" else dateTime(seen) + "\n" + AppStorage.seenText(ctx)
        diag.addView(statusRow(ctx, "Last SMS seen", seenLine, if (seen == 0L) 1 else 0))
        val server = AppStorage.serverMsg(ctx)
        diag.addView(statusRow(ctx, "Server", if (server.isEmpty()) "Not tested" else server, if (server == "OK") 0 else 1))
        val last = AppStorage.lastSync(ctx)
        diag.addView(statusRow(ctx, "Last successful sync", if (last == 0L) "Never" else dateTime(last), if (last == 0L) 1 else 0))

        v.findViewById<TextView>(R.id.tvSetupHint).visibility =
            if (AppStorage.isLoggedIn(ctx)) View.GONE else View.VISIBLE
    }
}
