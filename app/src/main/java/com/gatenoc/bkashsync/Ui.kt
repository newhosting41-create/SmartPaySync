package com.gatenoc.bkashsync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Fragment.toast(msg: String) {
    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun hasSmsPermission(c: Context): Boolean =
    ContextCompat.checkSelfPermission(c, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

fun isOnline(c: Context): Boolean {
    val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    return caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

/** 0 = good (green), 1 = warning (amber), 2 = problem (red), other = normal text */
fun levelColor(c: Context, level: Int): Int = ContextCompat.getColor(
    c,
    when (level) {
        0 -> R.color.success
        1 -> R.color.warn
        2 -> R.color.danger
        else -> R.color.text
    }
)

fun money(d: Double): String = String.format(Locale.US, "Tk %,.2f", d)

fun dateTime(ms: Long): String = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(ms))

fun statusLabel(s: String): String = when (s) {
    St.SUCCESS -> "Success (used on website)"
    St.SENT -> "On server, waiting for customer"
    St.PENDING -> "Not sent yet"
    St.FLAGGED -> "Flagged for review"
    St.FAILED -> "Failed"
    else -> s
}

fun statusLevel(s: String): Int = when (s) {
    St.SUCCESS -> 0
    St.SENT, St.PENDING, St.FLAGGED -> 1
    else -> 2
}

/** One label + value line used in the status cards. */
fun statusRow(c: Context, label: String, value: String, level: Int): View {
    val row = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, c.dp(7), 0, c.dp(7))
    }
    val l = TextView(c).apply {
        text = label
        textSize = 14f
        setTextColor(ContextCompat.getColor(c, R.color.muted))
    }
    val v = TextView(c).apply {
        text = value
        textSize = 14f
        gravity = Gravity.END
        setTextColor(levelColor(c, level))
        setTypeface(typeface, Typeface.BOLD)
    }
    row.addView(l, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(v, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
    return row
}
