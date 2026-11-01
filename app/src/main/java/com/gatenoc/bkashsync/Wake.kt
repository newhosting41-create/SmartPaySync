package com.gatenoc.bkashsync

import android.content.Context
import android.os.PowerManager

/**
 * Holds a partial WakeLock only for the duration of [block], with a hard timeout.
 * The CPU stays awake while SMS is being saved and uploaded, then the lock is released.
 */
object Wake {
    fun <T> hold(c: Context, timeoutMs: Long, block: () -> T): T {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SmartPaySync:work")
        lock.setReferenceCounted(false)
        lock.acquire(timeoutMs)
        try {
            return block()
        } finally {
            if (lock.isHeld) lock.release()
        }
    }
}
