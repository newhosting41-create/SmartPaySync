package com.gatenoc.bkashsync

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup

class PaymentsFragment : Fragment(R.layout.fragment_payments) {

    private val adapter = SmsAdapter()
    private var filter = 0 // 0 = Success, 1 = Waiting, 2 = Not sent / failed
    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (isAdded) {
                load()
                ui.postDelayed(this, 4000)
            }
        }
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        val rv = v.findViewById<RecyclerView>(R.id.rvItems)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        v.findViewById<ChipGroup>(R.id.chipGroup).setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.chipWaiting -> 1
                R.id.chipFailed -> 2
                else -> 0
            }
            load()
        }
    }

    override fun onResume() {
        super.onResume()
        load()
        ui.postDelayed(tick, 4000)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    private fun load() {
        val v = view ?: return
        val list = AppStorage.getRecords(requireContext()).filter {
            when (filter) {
                0 -> it.status == St.SUCCESS
                1 -> it.status == St.SENT || it.status == St.PENDING
                else -> it.status == St.FAILED || it.status == St.FLAGGED
            }
        }
        adapter.submit(list)
        val empty = v.findViewById<TextView>(R.id.tvEmpty)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when (filter) {
            0 -> "No used payments yet."
            1 -> "Nothing waiting. All sent."
            else -> "No failed items. Good!"
        }
    }
}

class SmsAdapter : RecyclerView.Adapter<SmsAdapter.VH>() {

    private val items = mutableListOf<SmsRecord>()

    fun submit(list: List<SmsRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val amount: TextView = v.findViewById(R.id.tvAmount)
        val trx: TextView = v.findViewById(R.id.tvTrx)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val time: TextView = v.findViewById(R.id.tvTime)
        val note: TextView = v.findViewById(R.id.tvNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sms, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.amount.text = money(r.amountBdt)
        h.trx.text = "TrxID  ${r.trxId}"
        h.status.text = statusLabel(r.status)
        h.status.setTextColor(levelColor(h.itemView.context, statusLevel(r.status)))
        h.time.text = if (r.smsTimeStr.isNotEmpty()) r.smsTimeStr else dateTime(r.time)
        h.note.text = r.serverNote
        h.note.visibility = if (r.serverNote.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun getItemCount(): Int = items.size
}
