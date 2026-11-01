package com.gatenoc.bkashsync

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ApiResult(
    val httpCode: Int,
    val success: Boolean,
    val status: String,
    val message: String,
    val json: JSONObject? = null
)

/** Talks to bkash_sms_sync.php. The Device Token in X-Device-Token is the only credential. */
object ApiClient {

    private fun postJson(endpoint: String, token: String, payload: JSONObject): ApiResult {
        if (endpoint.isBlank() || token.length < 16) {
            return ApiResult(0, false, "error", "Server URL or Device Token missing")
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Device-Token", token)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""

            val json = try { JSONObject(text) } catch (_: Exception) { null }
            val ok = json?.optBoolean("success", false) ?: (code in 200..299)
            val status = json?.optString("status", "") ?: ""
            val msg = json?.optString("message", "") ?: ""
            ApiResult(code, ok, status, if (msg.isNotEmpty()) msg else "HTTP $code", json)
        } catch (e: Exception) {
            ApiResult(0, false, "network_error", e.localizedMessage ?: "Could not connect to server")
        } finally {
            conn?.disconnect()
        }
    }

    fun pingServer(endpoint: String, token: String): ApiResult =
        postJson(endpoint, token, JSONObject().put("action", "ping"))

    fun uploadSms(endpoint: String, token: String, record: SmsRecord): ApiResult =
        postJson(
            endpoint, token,
            JSONObject()
                .put("sender", "bKash")
                .put("raw_sms", record.message)
                .put("local_event_id", record.id)
        )

    /** Asks the server which TrxIDs were used. Returns trxId -> "used" | "unused" | "flagged" | "missing". */
    fun checkStatuses(endpoint: String, token: String, trxIds: List<String>): Map<String, String>? {
        val arr = JSONArray()
        trxIds.forEach { arr.put(it) }
        val res = postJson(endpoint, token, JSONObject().put("action", "status").put("trx_ids", arr))
        if (!res.success) return null
        val items = res.json?.optJSONObject("items") ?: return null
        val out = HashMap<String, String>()
        val keys = items.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k.uppercase()] = items.optString(k)
        }
        return out
    }
}
