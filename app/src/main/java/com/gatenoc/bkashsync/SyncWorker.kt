package com.gatenoc.bkashsync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Safety net: runs every ~15 minutes even if the foreground service was killed by the phone. */
class SyncWorker(c: Context, p: WorkerParameters) : Worker(c, p) {

    override fun doWork(): Result {
        val app = applicationContext
        if (!AppStorage.isLoggedIn(app)) return Result.success()
        return try {
            Wake.hold(app, 120_000L) { SyncEngine.syncAll(app) }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(c.applicationContext).enqueueUniquePeriodicWork(
                "smartpay_periodic", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}
