package com.gatenoc.bkashsync

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class SmsRecord(
    val id: String,
    val sender: String,
    val senderNumber: String,
    val amountBdt: Double,
    val feeBdt: Double,
    val balanceAfter: Double,
    val trxId: String,
    val smsTimeStr: String,
    val message: String,
    var status: String,
    val time: Long,
    val dateText: String,
    val source: String,
    var serverNote: String = ""
)

object SmsParser {
    private val OFFICIAL_SENDERS = setOf("bkash", "16216")

    private val IGNORED_PHRASES = listOf(
        "Cash-Out", "Cash out", "Payment to", "Your bKash verification",
        "<#> NEVER share", "Never Share", "Your One Time Password", "Your security code",
        "Send Money", "Add Money", "Mobile Recharge", "Received Recharge",
        "Your bKash Mobile Recharge", "Bill successfully paid", "Your Digital Loan",
        "Savings", "Priyo Numbers", "Congratulations"
    )

    private val STRICT_REGEX = Regex(
        """^You have received Tk\s*([\d,]+(?:\.\d{2})?)\s+from\s+(01\d{9})\.?(?:\s+Ref\s+[^\r\n]*?\.)?\s+(?:Fee|Commission)\s+Tk\s*([\d,]+(?:\.\d{2})?)\.\s+Balance\s+Tk\s*([\d,]+(?:\.\d{2})?)\.\s+TrxID\s+([A-Z0-9]{10})\s+at\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val FALLBACK_TRX = Regex("""TrxID\s+([A-Z0-9]{8,12})""", RegexOption.IGNORE_CASE)
    private val FALLBACK_AMOUNT = Regex("""received\s+Tk\s*([\d,]+(?:\.\d{2})?)""", RegexOption.IGNORE_CASE)
    private val FALLBACK_FROM = Regex("""from\s+(01\d{9})""", RegexOption.IGNORE_CASE)
    private val FALLBACK_TIME = Regex("""(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})""")

    /** Only the official bKash sender (or 16216) is accepted from the live SMS stream. */
    fun isStrictOfficialBkashHeader(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val clean = address.trim()
        if (clean.startsWith("+") || clean.startsWith("880") || clean.startsWith("01") || clean.length > 10) return false
        return OFFICIAL_SENDERS.contains(clean.lowercase(Locale.ROOT))
    }

    fun isIgnoredMessage(body: String): Boolean {
        val clean = body.trim()
        return IGNORED_PHRASES.any { clean.startsWith(it, ignoreCase = true) } ||
            clean.contains("verification code", ignoreCase = true)
    }

    fun parseIncomingOrManual(rawAddress: String, rawBody: String, source: String = "Auto"): SmsRecord? {
        if (source != "Manual" && !isStrictOfficialBkashHeader(rawAddress)) return null

        val normalized = rawBody.replace(Regex("""\r?\n"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (normalized.isEmpty() || isIgnoredMessage(normalized)) return null

        val now = System.currentTimeMillis()
        val dateText = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(now))
        val eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").take(20)

        val strict = STRICT_REGEX.matchEntire(normalized)
        if (strict != null) {
            val g = strict.groupValues
            return SmsRecord(
                id = eventId,
                sender = "bKash",
                senderNumber = g[2],
                amountBdt = g[1].replace(",", "").toDoubleOrNull() ?: 0.0,
                feeBdt = g[3].replace(",", "").toDoubleOrNull() ?: 0.0,
                balanceAfter = g[4].replace(",", "").toDoubleOrNull() ?: 0.0,
                trxId = g[5].uppercase(Locale.ROOT),
                smsTimeStr = g[6],
                message = normalized,
                status = St.PENDING,
                time = now,
                dateText = dateText,
                source = source
            )
        }

        // Lenient fallback: a real "received" message that the strict pattern did not match.
        if (normalized.contains("You have received Tk", ignoreCase = true) &&
            normalized.contains("TrxID", ignoreCase = true)
        ) {
            val trx = FALLBACK_TRX.find(normalized)?.groupValues?.get(1)?.uppercase(Locale.ROOT) ?: return null
            val amount = FALLBACK_AMOUNT.find(normalized)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            val from = FALLBACK_FROM.find(normalized)?.groupValues?.get(1) ?: ""
            val smsTime = FALLBACK_TIME.find(normalized)?.groupValues?.get(1)
                ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(now))
            return SmsRecord(
                id = eventId,
                sender = "bKash",
                senderNumber = from,
                amountBdt = amount,
                feeBdt = 0.0,
                balanceAfter = 0.0,
                trxId = trx,
                smsTimeStr = smsTime,
                message = normalized,
                status = St.PENDING,
                time = now,
                dateText = dateText,
                source = source
            )
        }
        return null
    }
}
