package com.gatenoc.bkashsync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (isAdded) {
                render()
                ui.postDelayed(this, 4000)
            }
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val act = requireActivity() as MainActivity
        v.findViewById<View>(R.id.cardSuccess).setOnClickListener { act.navigate(R.id.nav_payments) }
        v.findViewById<View>(R.id.cardWaiting).setOnClickListener { act.navigate(R.id.nav_payments) }
        v.findViewById<View>(R.id.cardFailed).setOnClickListener { act.navigate(R.id.nav_payments) }

        v.findViewById<View>(R.id.btnSend).setOnClickListener {
            val app = requireContext().applicationContext
            if (!AppStorage.isLoggedIn(app)) {
                toast("Set up the server first (Settings)")
                return@setOnClickListener
            }
            toast("Sending...")
            Thread {
                try {
                    Wake.hold(app, 120_000L) { SyncEngine.syncAll(app) }
                } catch (_: Exception) {
                }
                ui.post { if (isAdded) render() }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        ui.postDelayed(tick, 4000)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun render() {
        val v = view ?: return
        val ctx = requireContext()
        val sms = hasSmsPermission(ctx)
        val configured = AppStorage.isLoggedIn(ctx)
        val bg = AppStorage.isBackgroundOn(ctx)
        val active = sms && configured && bg

        v.findViewById<MaterialCardView>(R.id.heroCard).setCardBackgroundColor(
            ContextCompat.getColor(ctx, if (active) R.color.success else R.color.danger)
        )
        v.findViewById<TextView>(R.id.tvHeroState).text = if (active) "Running" else "Not running"
        v.findViewById<TextView>(R.id.tvHeroSub).text = when {
            !sms -> "SMS permission is missing. Open Settings > Permissions."
            !configured -> "Server is not set up. Open Settings > Server."
            !bg -> "Background mode is off. Turn it on in Settings."
            else -> "Payment SMS are captured and sent automatically."
        }

        val (success, waiting, failed) = AppStorage.countByGroup(ctx)
        v.findViewById<TextView>(R.id.tvSuccess).text = success.toString()
        v.findViewById<TextView>(R.id.tvWaiting).text = waiting.toString()
        v.findViewById<TextView>(R.id.tvFailed).text = failed.toString()

        val box = v.findViewById<LinearLayout>(R.id.statusBox)
        box.removeAllViews()
        val online = isOnline(ctx)
        box.addView(statusRow(ctx, "Internet", if (online) "Connected" else "Offline", if (online) 0 else 1))

        val server = AppStorage.serverMsg(ctx)
        box.addView(
            statusRow(
                ctx, "Server",
                if (server.isEmpty()) "Not tested" else server,
                if (server == "OK") 0 else if (server.isEmpty()) 1 else 2
            )
        )
        val last = AppStorage.lastSync(ctx)
        box.addView(statusRow(ctx, "Last sync", if (last == 0L) "Never" else dateTime(last), if (last == 0L) 1 else 0))

        val seen = AppStorage.seenAt(ctx)
        box.addView(statusRow(ctx, "Last SMS seen", if (seen == 0L) "None yet" else dateTime(seen), if (seen == 0L) 1 else 0))
    }
}
