package com.gatenoc.bkashsync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = if (AppStorage.isLoggedIn(this)) R.id.nav_dashboard else R.id.nav_settings
        }

        requestNeededPermissions()
        SyncWorker.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        // Starting an already-running service is harmless; this brings it back if it was stopped.
        SyncForegroundService.start(this)
    }

    /** Switches the bottom tab (and therefore the screen). */
    fun navigate(id: Int) {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = id
    }

    private fun show(id: Int) {
        val f: Fragment = when (id) {
            R.id.nav_payments -> PaymentsFragment()
            R.id.nav_manual -> ManualFragment()
            R.id.nav_settings -> SettingsFragment()
            else -> DashboardFragment()
        }
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commitAllowingStateLoss()
    }

    private fun requestNeededPermissions() {
        val need = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }
}
