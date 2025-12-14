package com.sysdos.posmajooclone

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryAdapter(
    private var list: List<TransactionItem>,
    private val onItemClick: (TransactionItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvInv: TextView = v.findViewById(R.id.tvInvoice)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
        val tvTotal: TextView = v.findViewById(R.id.tvTotal)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Pastikan nama layout xml benar: item_history
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.tvInv.text = item.invoice_number
        holder.tvTotal.text = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(item.total_amount)

        // Format Tanggal
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            val date = parser.parse(item.created_at)
            holder.tvDate.text = formatter.format(date!!)
        } catch (e: Exception) {
            holder.tvDate.text = item.created_at
        }

        // Warna Status Pembayaran
        holder.tvStatus.text = item.payment_method
        if (item.payment_method == "TUNAI") {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")) // Hijau
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#2196F3")) // Biru
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = list.size
}