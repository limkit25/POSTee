package com.sysdos.posmajooclone

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
        // PERBAIKAN DISINI: Gunakan HistoryResponse, bukan TransactionResponse
        RetrofitClient.api(this).getTransactions().enqueue(object : Callback<HistoryResponse> {
            override fun onResponse(call: Call<HistoryResponse>, response: Response<HistoryResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    // Ambil list dari dalam wrapper .data
                    val list = response.body()!!.data

                    adapter = HistoryAdapter(list) { item ->
                        showDetailDialog(item)
                    }
                    rvHistory.adapter = adapter
                } else {
                    Toast.makeText(applicationContext, "Gagal Load: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<HistoryResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Error Koneksi: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showDetailDialog(item: TransactionItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_history_detail, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // --- BINDING VIEW STANDAR ---
        val tvInvoice = dialogView.findViewById<TextView>(R.id.tvInvoice)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDate)
        val tvCashier = dialogView.findViewById<TextView>(R.id.tvCashier)
        val tvMethod = dialogView.findViewById<TextView>(R.id.tvMethod)
        val tvItems = dialogView.findViewById<TextView>(R.id.tvItems)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotal)

        // --- BINDING VIEW BARU (PAJAK & DISKON) ---
        val tvSub = dialogView.findViewById<TextView>(R.id.tvHistorySubtotal)

        // Layout container (agar bisa dimunculkan/disembunyikan)
        val layoutDisc = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutHistoryDiscount)
        val tvDisc = dialogView.findViewById<TextView>(R.id.tvHistoryDiscount)

        val layoutTax = dialogView.findViewById<android.widget.LinearLayout>(R.id.layoutHistoryTax)
        val tvTax = dialogView.findViewById<TextView>(R.id.tvHistoryTax)

        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        val btnPrint = dialogView.findViewById<Button>(R.id.btnPrint)

        // --- ISI DATA ---
        tvInvoice.text = item.invoice_number
        tvCashier.text = item.cashier_name
        tvMethod.text = item.payment_method
        tvTotal.text = formatRupiah(item.total_amount)

        // 1. Subtotal
        tvSub.text = formatRupiah(item.subtotal)

        // 2. Diskon (Cek Logic)
        if (item.discount_amount > 0) {
            layoutDisc.visibility = android.view.View.VISIBLE // MUNCULKAN
            tvDisc.text = "- ${formatRupiah(item.discount_amount)}"
        } else {
            layoutDisc.visibility = android.view.View.GONE // SEMBUNYIKAN
        }

        // 3. Pajak (Cek Logic)
        if (item.tax_amount > 0) {
            layoutTax.visibility = android.view.View.VISIBLE // MUNCULKAN
            tvTax.text = formatRupiah(item.tax_amount)
        } else {
            layoutTax.visibility = android.view.View.GONE // SEMBUNYIKAN
        }

        // Format Tanggal
        var displayDate = item.created_at
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            displayDate = formatter.format(parser.parse(item.created_at)!!)
        } catch (e: Exception) {}
        tvDate.text = displayDate

        // Detail Item
        val sb = StringBuilder()
        item.details.forEach { detail ->
            val sub = detail.qty * detail.price_at_transaction
            val pName = detail.product?.name ?: "Item"
            sb.append("$pName x${detail.qty} = ${formatRupiah(sub)}\n")
        }
        tvItems.text = if (sb.isNotEmpty()) sb.toString() else "Tidak ada detail item."

        // Tombol Cetak Ulang (Reprint)
        btnPrint.setOnClickListener {
            printReprintBluetooth(item, displayDate)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun printReprintBluetooth(item: TransactionItem, formattedDate: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
                return
            }
        }
        try {
            val connection = BluetoothPrintersConnections.selectFirstPaired()
            if (connection != null) {
                val printer = EscPosPrinter(connection, 203, 48f, 32)
                var text = """
                    [C]<b>POS MAJOO CLONE</b>
                    [C]Jl. Maju Mundur No. 1
                    [C]================================
                    [C]<font size='big'><b>*** COPY ***</b></font>
                    [C]================================
                    [L]<b>No:</b>[R]${item.invoice_number}
                    [L]<b>Kasir:</b>[R]${item.cashier_name}
                    [L]<b>Tgl:</b>[R]$formattedDate
                    [C]--------------------------------
                """.trimIndent()

                item.details.forEach { detail ->
                    val sub = detail.qty * detail.price_at_transaction
                    val pName = detail.product?.name ?: "Item"
                    text += "\n[L]${detail.qty}x $pName[R]${formatRupiah(sub)}"
                }

                text += "\n[C]--------------------------------"
                text += "\n[L]Subtotal[R]${formatRupiah(item.subtotal)}"
                if (item.discount_amount > 0) text += "\n[L]Diskon[R]-${formatRupiah(item.discount_amount)}"
                if (item.tax_amount > 0) text += "\n[L]Pajak[R]${formatRupiah(item.tax_amount)}"

                text += """
                    
                    [L]<b>TOTAL</b>[R]<b>${formatRupiah(item.total_amount)}</b>
                    [L]Metode[R]${item.payment_method}
                    [C]================================
                    [C]*** SALINAN TRANSAKSI ***
                """.trimIndent()

                printer.printFormattedText(text)
                printer.disconnectPrinter()
            } else {
                Toast.makeText(this, "Printer tidak ditemukan.", Toast.LENGTH_SHORT).show()
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