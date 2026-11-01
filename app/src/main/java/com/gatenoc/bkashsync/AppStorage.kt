package com.gatenoc.bkashsync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Status values stored on every SMS record. */
object St {
    const val PENDING = "Pending"   // read from SMS, not sent to server yet
    const val SENT = "Sent"         // server accepted, waiting for customer to use it
    const val SUCCESS = "Success"   // customer used it on the website
    const val FLAGGED = "Flagged"   // server flagged it for admin review
    const val FAILED = "Failed"     // server rejected it (bad data / bad token)
}

/** All app data lives in its own preferences file. No other app's storage is touched. */
object AppStorage {
    private const val PREF = "smartpay_sync"
    private const val K_LOGGED = "logged_in"
    private const val K_ENDPOINT = "endpoint"
    private const val K_TOKEN = "device_token"
    private const val K_BG = "background_on"
    private const val K_CLEAN = "cleanup_hours"
    private const val K_RECORDS = "records"
    private const val K_SEEN_AT = "seen_at"
    private const val K_SEEN_TXT = "seen_text"
    private const val K_LAST_SYNC = "last_sync"
    private const val K_SERVER_MSG = "server_msg"
    private const val MAX_RECORDS = 500

    private fun p(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isLoggedIn(c: Context): Boolean = p(c).getBoolean(K_LOGGED, false)
    fun getEndpoint(c: Context): String = p(c).getString(K_ENDPOINT, "")?.trim() ?: ""
    fun getDeviceKey(c: Context): String = p(c).getString(K_TOKEN, "")?.trim() ?: ""

    fun saveLoginConfig(c: Context, endpoint: String, token: String) {
        p(c).edit()
            .putString(K_ENDPOINT, endpoint.trim())
            .putString(K_TOKEN, token.trim())
            .putBoolean(K_LOGGED, true)
            .apply()
    }

    fun logout(c: Context) {
        p(c).edit().putBoolean(K_LOGGED, false).apply()
    }

    fun isBackgroundOn(c: Context): Boolean = p(c).getBoolean(K_BG, true)
    fun setBackgroundOn(c: Context, on: Boolean) { p(c).edit().putBoolean(K_BG, on).apply() }

    fun getCleanupHours(c: Context): Int = p(c).getInt(K_CLEAN, 0)
    fun setCleanupHours(c: Context, hours: Int) { p(c).edit().putInt(K_CLEAN, hours).apply() }

    fun noteSeen(c: Context, text: String) {
        p(c).edit().putLong(K_SEEN_AT, System.currentTimeMillis()).putString(K_SEEN_TXT, text).apply()
    }
    fun seenAt(c: Context): Long = p(c).getLong(K_SEEN_AT, 0L)
    fun seenText(c: Context): String = p(c).getString(K_SEEN_TXT, "") ?: ""

    fun setLastSync(c: Context, ms: Long) { p(c).edit().putLong(K_LAST_SYNC, ms).apply() }
    fun lastSync(c: Context): Long = p(c).getLong(K_LAST_SYNC, 0L)

    fun setServerMsg(c: Context, msg: String) { p(c).edit().putString(K_SERVER_MSG, msg).apply() }
    fun serverMsg(c: Context): String = p(c).getString(K_SERVER_MSG, "") ?: ""

    @Synchronized
    fun getRecords(c: Context): MutableList<SmsRecord> {
        val raw = p(c).getString(K_RECORDS, "[]") ?: "[]"
        val out = mutableListOf<SmsRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    SmsRecord(
                        id = o.optString("id"),
                        sender = o.optString("sender", "bKash"),
                        senderNumber = o.optString("senderNumber", ""),
                        amountBdt = o.optDouble("amountBdt", 0.0),
                        feeBdt = o.optDouble("feeBdt", 0.0),
                        balanceAfter = o.optDouble("balanceAfter", 0.0),
                        trxId = o.optString("trxId", ""),
                        smsTimeStr = o.optString("smsTimeStr", ""),
                        message = o.optString("message", ""),
                        status = o.optString("status", St.PENDING),
                        time = o.optLong("time", System.currentTimeMillis()),
                        dateText = o.optString("dateText", ""),
                        source = o.optString("source", "Auto"),
                        serverNote = o.optString("serverNote", "")
                    )
                )
            }
        } catch (_: Exception) {
        }
        return out
    }

    @Synchronized
    private fun saveRecords(c: Context, list: List<SmsRecord>) {
        val arr = JSONArray()
        for (r in list.take(MAX_RECORDS)) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("sender", r.sender)
                    .put("senderNumber", r.senderNumber)
                    .put("amountBdt", r.amountBdt)
                    .put("feeBdt", r.feeBdt)
                    .put("balanceAfter", r.balanceAfter)
                    .put("trxId", r.trxId)
                    .put("smsTimeStr", r.smsTimeStr)
                    .put("message", r.message)
                    .put("status", r.status)
                    .put("time", r.time)
                    .put("dateText", r.dateText)
                    .put("source", r.source)
                    .put("serverNote", r.serverNote)
            )
        }
        p(c).edit().putString(K_RECORDS, arr.toString()).commit()
    }

    /** Returns true only if the transaction ID was not stored before (dedupe guard). */
    @Synchronized
    fun insertIfNew(c: Context, record: SmsRecord): Boolean {
        val list = getRecords(c)
        if (list.any { it.trxId.equals(record.trxId, ignoreCase = true) }) return false
        list.add(0, record)
        saveRecords(c, list)
        return true
    }

    @Synchronized
    fun updateStatus(c: Context, idOrTrx: String, status: String, note: String = "") {
        val list = getRecords(c)
        var changed = false
        for (r in list) {
            if (r.id == idOrTrx || r.trxId.equals(idOrTrx, ignoreCase = true)) {
                r.status = status
                r.serverNote = note
                changed = true
            }
        }
        if (changed) saveRecords(c, list)
    }

    fun findByTrx(c: Context, trx: String): SmsRecord? =
        getRecords(c).firstOrNull { it.trxId.equals(trx, ignoreCase = true) }

    /** Removes old Success records only. Pending / Sent / Failed are never auto-deleted. */
    @Synchronized
    fun runCleanup(c: Context) {
        val hours = getCleanupHours(c)
        if (hours <= 0) return
        val cutoff = System.currentTimeMillis() - hours * 3600_000L
        val list = getRecords(c)
        val kept = list.filter { !(it.status == St.SUCCESS && it.time < cutoff) }
        if (kept.size != list.size) saveRecords(c, kept)
    }

    /** Triple(success, waiting = sent + pending, failed = failed + flagged) */
    fun countByGroup(c: Context): Triple<Int, Int, Int> {
        var success = 0
        var waiting = 0
        var failed = 0
        for (r in getRecords(c)) {
            when (r.status) {
                St.SUCCESS -> success++
                St.SENT, St.PENDING -> waiting++
                St.FAILED, St.FLAGGED -> failed++
            }
        }
        return Triple(success, waiting, failed)
    }
}
