package com.gatenoc.bkashsync

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Use when the phone was offline or the SMS was missed. Paste the full bKash SMS. */
class ManualFragment : Fragment(R.layout.fragment_manual) {

    private val ui = Handler(Looper.getMainLooper())

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val et = v.findViewById<EditText>(R.id.etSms)

        v.findViewById<View>(R.id.btnPaste).setOnClickListener {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
            if (text.isBlank()) toast("Clipboard is empty") else et.setText(text)
        }
        v.findViewById<View>(R.id.btnClear).setOnClickListener {
            et.setText("")
            v.findViewById<View>(R.id.resultCard).visibility = View.GONE
        }
        v.findViewById<View>(R.id.btnSendManual).setOnClickListener {
            send(v, et.text.toString().trim())
        }
    }

    private fun showResult(v: View, msg: String, level: Int) {
        v.findViewById<View>(R.id.resultCard).visibility = View.VISIBLE
        val tv = v.findViewById<TextView>(R.id.tvResult)
        tv.text = msg
        tv.setTextColor(levelColor(requireContext(), level))
    }

    private fun send(v: View, text: String) {
        val app = requireContext().applicationContext
        if (text.isEmpty()) {
            showResult(v, "Paste the full SMS first.", 2)
            return
        }
        val parsed = SmsParser.parseIncomingOrManual("bKash", text, "Manual")
        if (parsed == null) {
            showResult(v, "This is not a bKash \"money received\" SMS. Copy the full message and try again.", 2)
            return
        }

        val btn = v.findViewById<View>(R.id.btnSendManual)
        btn.isEnabled = false
        showResult(v, "Sending...", 1)

        Thread {
            var msg = "Saved in Pending. It will be sent automatically."
            var level = 1
            try {
                val inserted = AppStorage.insertIfNew(app, parsed)
                val target = AppStorage.findByTrx(app, parsed.trxId)
                if (target == null) {
                    msg = "Something went wrong. Try again."
                    level = 2
                } else if (!inserted && target.status == St.SUCCESS) {
                    msg = "Already used on the website (${target.trxId})."
                    level = 0
                } else if (!inserted && target.status == St.SENT) {
                    msg = "Already on the server (${target.trxId})."
                    level = 0
                } else {
                    Wake.hold(app, 60_000L) { SyncEngine.uploadOne(app, target) }
                    when (AppStorage.findByTrx(app, parsed.trxId)?.status) {
                        St.SENT -> { msg = "Sent to server. Waiting for the customer."; level = 0 }
                        St.SUCCESS -> { msg = "Already used on the website."; level = 0 }
                        St.FLAGGED -> { msg = "Sent, but flagged for review."; level = 1 }
                        St.FAILED -> {
                            msg = "Server rejected it: " + (AppStorage.findByTrx(app, parsed.trxId)?.serverNote ?: "")
                            level = 2
                        }
                        else -> { }
                    }
                }
            } catch (e: Exception) {
                msg = "Error: ${e.message}"
                level = 2
            }
            val finalMsg = msg
            val finalLevel = level
            ui.post {
                if (isAdded) {
                    btn.isEnabled = true
                    showResult(v, finalMsg, finalLevel)
                }
            }
        }.start()
    }
}
