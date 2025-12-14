package com.sysdos.posmajooclone // PASTIKAN PACKAGE SESUAI

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        rvHistory = findViewById(R.id.rvHistory)
        btnBack = findViewById(R.id.btnBack)

        rvHistory.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { finish() }

        loadHistory()
    }

    private fun loadHistory() {
        RetrofitClient.instance.getTransactions().enqueue(object : Callback<TransactionResponse> {
            override fun onResponse(call: Call<TransactionResponse>, response: Response<TransactionResponse>) {
                if (response.isSuccessful) {
                    val list = response.body()?.data ?: emptyList()
                    adapter = HistoryAdapter(list) { item ->
                        showDetailDialog(item)
                    }
                    rvHistory.adapter = adapter
                }
            }
            override fun onFailure(call: Call<TransactionResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Gagal Load", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDetailDialog(item: TransactionItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_history_detail, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val tvInvoice = dialogView.findViewById<TextView>(R.id.tvInvoice)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDate)
        val tvCashier = dialogView.findViewById<TextView>(R.id.tvCashier)
        val tvMethod = dialogView.findViewById<TextView>(R.id.tvMethod)
        val tvItems = dialogView.findViewById<TextView>(R.id.tvItems)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotal)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val btnPrint = dialogView.findViewById<Button>(R.id.btnPrint) // Tombol Baru

        tvInvoice.text = item.invoice_number
        tvCashier.text = item.cashier_name
        tvMethod.text = item.payment_method
        tvTotal.text = formatRupiah(item.total_amount)

        // Format Tanggal Cantik
        var displayDate = item.created_at
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            displayDate = formatter.format(parser.parse(item.created_at)!!)
        } catch (e: Exception) {}
        tvDate.text = displayDate

        // Build String Detail Item
        val sb = StringBuilder()
        item.details?.forEach { detail ->
            val sub = detail.qty * detail.price_at_transaction
            sb.append("${detail.product?.name} x${detail.qty} = ${formatRupiah(sub)}\n")
        }
        tvItems.text = if (sb.isNotEmpty()) sb.toString() else "Tidak ada detail item."

        // KLIK TOMBOL CETAK ULANG
        btnPrint.setOnClickListener {
            printReprintBluetooth(item, displayDate)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // --- LOGIKA PRINT (KHUSUS REPRINT) ---
    private fun printReprintBluetooth(item: TransactionItem, formattedDate: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 1)
                return
            }
        }

        try {
            val connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection != null) {
                val printer = EscPosPrinter(connection, 203, 48f, 32)

                // --- MULAI BLOK AMAN ---
                try {
                    var text = """
                        [C]<b>MAJOO CLONE POS</b>
                        [C]Jl. Maju Mundur No. 1
                        [C]================================
                        [C]<font size='big'><b>*** REPRINT / COPY ***</b></font>
                        [C]================================
                        [L]<b>No:</b>[R]${item.invoice_number}
                        [L]<b>Kasir:</b>[R]${item.cashier_name}
                        [L]<b>Tgl:</b>[R]$formattedDate
                        [C]--------------------------------
                    """.trimIndent()

                    item.details?.forEach { detail ->
                        val sub = detail.qty * detail.price_at_transaction
                        text += "\n[L]${detail.qty}x ${detail.product?.name}[R]${formatRupiah(sub)}"
                    }

                    text += """
                        
                        [C]--------------------------------
                        [L]<b>TOTAL</b>[R]<b>${formatRupiah(item.total_amount)}</b>
                        [L]Metode[R]${item.payment_method}
                        [C]================================
                        [C]*** INI ADALAH SALINAN ***
                    """.trimIndent()

                    printer.printFormattedText(text)

                } finally {
                    // --- WAJIB: PUTUS KONEKSI ---
                    printer.disconnectPrinter()
                }
                // -----------------------

            } else {
                Toast.makeText(this, "Printer tidak ditemukan.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal Print: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatRupiah(number: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(number)
    }
}