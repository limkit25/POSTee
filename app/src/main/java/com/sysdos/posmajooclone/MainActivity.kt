package com.sysdos.posmajooclone // PASTIKAN PACKAGE SESUAI

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.sysdos.posmajooclone.TransactionResponse
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // --- UI COMPONENTS ---
    private lateinit var rvProducts: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private lateinit var tvTotalAmount: TextView
    private lateinit var btnCheckout: Button
    private lateinit var btnHistory: Button
    private lateinit var btnOptionMenu: ImageButton
    private lateinit var llCategories: LinearLayout

    // --- DATA ---
    private var fullProductList: List<Product> = listOf()
    private val cartList = mutableListOf<Product>()

    // --- STATE SHIFT & USER ---
    private var cashierName: String = "Kasir"
    private var userId: Int = 0
    private var currentShiftId: Int = 0
    private var currentInitialCash: Double = 0.0

    // --- SETTING DARI BACKEND (PAJAK & DISKON) ---
    private var globalTaxRate: Double = 0.0
    private var isTaxEnabled: Boolean = false
    private var isDiscountEnabled: Boolean = false

    // --- VARIABEL HITUNGAN TRANSAKSI SEMENTARA ---
    private var tempSubtotal: Double = 0.0
    private var tempDiscount: Double = 0.0
    private var tempTax: Double = 0.0
    private var tempGrandTotal: Double = 0.0

    // --- HITUNGAN LOKAL (Untuk Preview Laporan) ---
    private var localCashSales: Double = 0.0
    private var localNonCashSales: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. TANGKAP DATA LOGIN
        cashierName = intent.getStringExtra("USER_NAME") ?: "Admin"
        userId = intent.getIntExtra("USER_ID", 0)

        // 2. INISIALISASI VIEW (PENTING: Semua findViewById harus di sini)
        tvTotalAmount = findViewById(R.id.tvTotalAmount)
        btnCheckout = findViewById(R.id.btnCheckout)
        btnHistory = findViewById(R.id.btnHistory)
        btnOptionMenu = findViewById(R.id.btnLogout)
        rvProducts = findViewById(R.id.rvProducts)
        llCategories = findViewById(R.id.llCategories)

        // 3. SETUP RECYCLERVIEW
        val spanCount = calculateNoOfColumns(this, 170f)
        rvProducts.layoutManager = GridLayoutManager(this, spanCount)
        productAdapter = ProductAdapter(listOf(),
            { product -> addToCart(product) },
            { product -> removeFromCart(product) }
        )
        rvProducts.adapter = productAdapter

        // 4. SETUP LOGIC
        setupButtons()
        checkOpenShift()

        // PANGGIL SETTING TERAKHIR
        fetchSettings()
    }

    // VERSI AMAN: Kalau server error/kosong, pakai default
    private fun fetchSettings() {
        RetrofitClient.api(this).getSettings().enqueue(object : Callback<SettingResponse> {
            override fun onResponse(call: Call<SettingResponse>, response: Response<SettingResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val config = response.body()!!
                    globalTaxRate = config.tax_rate
                    isTaxEnabled = config.is_tax_enabled
                    isDiscountEnabled = config.is_discount_enabled
                } else {
                    // Kalau gagal ambil, pakai default (Mati semua biar aman)
                    globalTaxRate = 0.0
                    isTaxEnabled = false
                    isDiscountEnabled = false
                }
            }
            override fun onFailure(call: Call<SettingResponse>, t: Throwable) {
                // Kalau koneksi putus, pakai default juga
                globalTaxRate = 0.0
                isTaxEnabled = false
                isDiscountEnabled = false
            }
        })
    }

    private fun setupButtons() {
        btnOptionMenu.setOnClickListener { showLogoutOptionsDialog() }
        btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }

        btnCheckout.setOnClickListener {
            if (cartList.isEmpty()) Toast.makeText(this, "Keranjang kosong!", Toast.LENGTH_SHORT).show()
            else showCartDialog() // BUKA DIALOG KERANJANG BARU
        }

        tvTotalAmount.setOnClickListener {
            if (cartList.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Hapus Keranjang?")
                    .setPositiveButton("YA") { _, _ -> cartList.clear(); updateTotalUI() }
                    .setNegativeButton("Batal", null).show()
            }
        }
    }

    // ==========================================
    // BAGIAN KERANJANG & HITUNG-HITUNGAN
    // ==========================================

    private fun showCartDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cart_list, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // Bind View
        val rvCart = dialogView.findViewById<RecyclerView>(R.id.rvCartItems)
        val tvSub = dialogView.findViewById<TextView>(R.id.tvCartSubtotal)
        val etDisc = dialogView.findViewById<EditText>(R.id.etDiscountInput)
        val tvTaxLbl = dialogView.findViewById<TextView>(R.id.tvTaxLabel)
        val tvTaxVal = dialogView.findViewById<TextView>(R.id.tvTaxValue)
        val tvGrand = dialogView.findViewById<TextView>(R.id.tvCartGrandTotal)
        val llDisc = dialogView.findViewById<LinearLayout>(R.id.llDiscountContainer)
        val llTax = dialogView.findViewById<LinearLayout>(R.id.llTaxContainer)

        // Setup List
        val groupedList = cartList.groupBy { it.id }.map { (_, items) -> CartItemDisplay(items.first().name, items.first().price, items.size) }
        rvCart.layoutManager = LinearLayoutManager(this)
        rvCart.adapter = CartAdapter(groupedList)

        // VISIBILITY BERDASARKAN SETTING BACKEND
        llTax.visibility = if (isTaxEnabled) View.VISIBLE else View.GONE
        llDisc.visibility = if (isDiscountEnabled) View.VISIBLE else View.GONE
        tvTaxLbl.text = "Pajak ($globalTaxRate%)"

        // FUNGSI HITUNG REALTIME
        fun recalculate() {
            tempSubtotal = cartList.sumOf { it.price }

            // Ambil input diskon (hanya jika enabled)
            val inputDisc = if (isDiscountEnabled && etDisc.text.isNotEmpty()) etDisc.text.toString().toDoubleOrNull() ?: 0.0 else 0.0
            tempDiscount = inputDisc

            // Hitung Pajak: (Subtotal - Diskon) * Rate%
            val taxableAmount = if ((tempSubtotal - tempDiscount) > 0) (tempSubtotal - tempDiscount) else 0.0
            tempTax = if (isTaxEnabled) (taxableAmount * (globalTaxRate / 100)) else 0.0

            tempGrandTotal = tempSubtotal - tempDiscount + tempTax

            // Update UI
            tvSub.text = formatRupiah(tempSubtotal)
            tvTaxVal.text = formatRupiah(tempTax)
            tvGrand.text = formatRupiah(tempGrandTotal)
        }

        recalculate() // Hitung awal

        // Listener jika user mengetik diskon
        etDisc.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { recalculate() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        dialogView.findViewById<Button>(R.id.btnCartBack).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnCartPay).setOnClickListener {
            dialog.dismiss()
            showPaymentDialog() // Lanjut ke Pembayaran membawa nilai tempGrandTotal
        }
        dialog.show()
    }

    private fun showPaymentDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create(); dialog.setCancelable(false)

        // --- BINDING VIEW ---
        val etCash = dialogView.findViewById<EditText>(R.id.etCashReceived)
        val tvChange = dialogView.findViewById<TextView>(R.id.tvChange)
        val btnProcess = dialogView.findViewById<Button>(R.id.btnProcessPayment)
        val layoutCash = dialogView.findViewById<LinearLayout>(R.id.layoutCashInput)
        val rgMethod = dialogView.findViewById<RadioGroup>(R.id.rgPaymentMethod)

        // VIEW RINCIAN
        val tvSub = dialogView.findViewById<TextView>(R.id.tvDetailSubtotal)
        val tvTax = dialogView.findViewById<TextView>(R.id.tvDetailTax)
        val llTax = dialogView.findViewById<LinearLayout>(R.id.layoutDetailTax)

        // View Diskon (Baru)
        val tvDisc = dialogView.findViewById<TextView>(R.id.tvDetailDiscount)
        val llDisc = dialogView.findViewById<LinearLayout>(R.id.layoutDetailDiscount)

        val tvTotal = dialogView.findViewById<TextView>(R.id.tvDialogTotal)

        // --- ISI DATA KE VIEW ---
        val totalToPay = tempGrandTotal

        // 1. Subtotal
        tvSub.text = formatRupiah(tempSubtotal)

        // 2. Diskon (Cek kalau ada diskon)
        if (tempDiscount > 0) {
            llDisc.visibility = View.VISIBLE
            tvDisc.text = "- ${formatRupiah(tempDiscount)}" // Tambah minus di depan
        } else {
            llDisc.visibility = View.GONE
        }

        // 3. Pajak (Cek kalau ada pajak)
        if (tempTax > 0) {
            llTax.visibility = View.VISIBLE
            tvTax.text = formatRupiah(tempTax)
        } else {
            llTax.visibility = View.GONE
        }

        // 4. Total Besar
        tvTotal.text = formatRupiah(totalToPay)


        // --- LOGIKA PEMBAYARAN ---
        rgMethod.setOnCheckedChangeListener { _, id ->
            if (id == R.id.rbCash) {
                layoutCash.visibility = View.VISIBLE; btnProcess.isEnabled = false; btnProcess.setBackgroundColor(Color.GRAY)
            } else {
                layoutCash.visibility = View.GONE; btnProcess.isEnabled = true; btnProcess.setBackgroundColor(Color.parseColor("#4CAF50"))
            }
        }

        fun setCash(v: Double) { etCash.setText(v.toLong().toString()); etCash.setSelection(etCash.text.length) }
        dialogView.findViewById<Button>(R.id.btnUangPas).setOnClickListener { setCash(totalToPay) }
        dialogView.findViewById<Button>(R.id.btn50k).setOnClickListener { setCash(50000.0) }
        dialogView.findViewById<Button>(R.id.btn100k).setOnClickListener { setCash(100000.0) }

        etCash.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val cash = s.toString().replace(".","").toDoubleOrNull() ?: 0.0
                val change = cash - totalToPay
                tvChange.text = formatRupiah(change)
                if (change >= 0) { btnProcess.isEnabled = true; btnProcess.setBackgroundColor(Color.parseColor("#4CAF50")) }
                else { btnProcess.isEnabled = false; btnProcess.setBackgroundColor(Color.GRAY) }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnProcess.setOnClickListener {
            val method = dialogView.findViewById<RadioButton>(rgMethod.checkedRadioButtonId).text.toString()
            val cashIn = if(method=="TUNAI") etCash.text.toString().replace(".","").toDoubleOrNull() ?: totalToPay else totalToPay
            dialog.dismiss()
            sendTransactionToServer(method, cashIn, cashIn - totalToPay)
        }
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun sendTransactionToServer(method: String, cash: Double, change: Double) {
        // Siapkan detail belanja
        val details = cartList.groupBy { it.id }.map {
            CheckoutDetail(it.key, it.value.size, it.value.first().price)
        }

        // Buat nomor invoice dummy (akan ditimpa backend sebenarnya, tapi butuh dikirim)
        val inv = "INV-${System.currentTimeMillis()}"

        // Siapkan Request
        val req = CheckoutRequest(
            shift_id = currentShiftId,
            invoice_number = inv,
            subtotal = tempSubtotal,
            discount_amount = tempDiscount,
            tax_amount = tempTax,
            total_amount = tempGrandTotal,
            cashier_name = cashierName,
            payment_method = method,
            details = details
        )

        // Panggil Retrofit
        // ERROR MERAH BIASANYA DISINI JIKA ApiService BELUM DIUPDATE
        RetrofitClient.api(this).createTransaction(req).enqueue(object : Callback<TransactionResponse> {
            override fun onResponse(call: Call<TransactionResponse>, response: Response<TransactionResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val trxData = response.body()!! // Data lengkap (termasuk pajak/diskon dari server)

                    // Update Shift Lokal
                    if (method == "TUNAI") {
                        localCashSales += tempGrandTotal
                    } else {
                        localNonCashSales += tempGrandTotal
                    }

                    // Panggil Dialog Struk
                    showReceiptDialog(trxData, method, cash, change)
                } else {
                    Toast.makeText(applicationContext, "Gagal Transaksi: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<TransactionResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Error Server: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ==========================================
    // BAGIAN PRINTING (STRUK LENGKAP)
    // ==========================================

    private fun showReceiptDialog(data: TransactionResponse, method: String, cash: Double, change: Double) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_receipt, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        // Binding ID (Harus sama persis dengan dialog_receipt.xml)
        val tvInvoice = dialogView.findViewById<TextView>(R.id.tvRcInvoice)
        val tvCashier = dialogView.findViewById<TextView>(R.id.tvRcCashier)
        val tvItems = dialogView.findViewById<TextView>(R.id.tvRcItems)

        val tvSub = dialogView.findViewById<TextView>(R.id.tvRcSubtotal)
        val tvDisc = dialogView.findViewById<TextView>(R.id.tvRcDiscount)
        val layoutDisc = dialogView.findViewById<LinearLayout>(R.id.layoutRcDiscount)
        val tvTax = dialogView.findViewById<TextView>(R.id.tvRcTax)
        val layoutTax = dialogView.findViewById<LinearLayout>(R.id.layoutRcTax)

        val tvTotal = dialogView.findViewById<TextView>(R.id.tvRcTotal)
        val tvPayLabel = dialogView.findViewById<TextView>(R.id.tvRcPayLabel)
        val tvPay = dialogView.findViewById<TextView>(R.id.tvRcPay)
        val tvChange = dialogView.findViewById<TextView>(R.id.tvRcChange)

        val btnClosePrint = dialogView.findViewById<Button>(R.id.btnRcClose)

        // Isi Data
        tvInvoice.text = data.invoice_number
        tvCashier.text = cashierName // Nama kasir dari login
        tvTotal.text = formatRupiah(data.total_amount)

        // Subtotal, Pajak, Diskon (Dari Response Server)
        tvSub.text = formatRupiah(data.subtotal)

        if (data.discount_amount > 0) {
            layoutDisc.visibility = View.VISIBLE
            tvDisc.text = "- ${formatRupiah(data.discount_amount)}"
        } else {
            layoutDisc.visibility = View.GONE
        }

        if (data.tax_amount > 0) {
            layoutTax.visibility = View.VISIBLE
            tvTax.text = formatRupiah(data.tax_amount)
        } else {
            layoutTax.visibility = View.GONE
        }

        // Info Bayar
        if (method == "TUNAI") {
            tvPayLabel.text = "Tunai:"
            tvPay.text = formatRupiah(cash)
        } else {
            tvPayLabel.text = "Bayar via:"
            tvPay.text = method
        }
        tvChange.text = formatRupiah(change)

        // List Item (Ambil dari keranjang saat ini karena detail item tidak dikirim balik full oleh server standar)
        val sb = StringBuilder()
        cartList.groupBy { it.name }.forEach { (name, items) ->
            val totalItemPrice = items.size * items.first().price
            sb.append("${items.size}x $name .. ${formatRupiah(totalItemPrice)}\n")
        }
        tvItems.text = sb.toString()

        // Tombol Tutup & Print
        btnClosePrint.setOnClickListener {
            printStrukBluetooth(data, method, cash, change) // Fungsi print

            dialog.dismiss()

            // Reset Transaksi setelah struk ditutup
            cartList.clear()
            tempSubtotal = 0.0
            tempDiscount = 0.0
            tempTax = 0.0
            tempGrandTotal = 0.0
            updateTotalUI() // Pastikan fungsi ini ada untuk reset tampilan dashboard
            Toast.makeText(this, "Transaksi Selesai", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun printStrukBluetooth(data: TransactionResponse, method: String, cash: Double, change: Double) {
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
                    [C]Jl. Teknologi No. 10
                    [C]================================
                    [L]<b>No:</b>[R]${data.invoice_number}
                    [L]<b>Kasir:</b>[R]$cashierName
                    [L]<b>Waktu:</b>[R]${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())}
                    [C]--------------------------------
                """.trimIndent()

                // Loop Barang
                cartList.groupBy { it.name }.forEach { (name, items) ->
                    text += "\n[L]${items.size}x $name[R]${formatRupiah(items.size * items.first().price)}"
                }

                text += "\n[C]--------------------------------"
                text += "\n[L]Subtotal[R]${formatRupiah(data.subtotal)}"

                if (data.discount_amount > 0) {
                    text += "\n[L]Diskon[R]-${formatRupiah(data.discount_amount)}"
                }
                if (data.tax_amount > 0) {
                    text += "\n[L]Pajak[R]${formatRupiah(data.tax_amount)}"
                }

                text += """
                    
                    [L]<b>TOTAL</b>[R]<b>${formatRupiah(data.total_amount)}</b>
                    [L]Bayar ($method)[R]${formatRupiah(cash)}
                    [L]Kembali[R]${formatRupiah(change)}
                    [C]================================
                    [C]Terima Kasih
                    [C]Barang yang dibeli tidak dapat
                    [C]ditukar/dikembalikan.
                """.trimIndent()

                printer.printFormattedText(text)
                printer.disconnectPrinter()
            } else {
                Toast.makeText(this, "Printer tidak terhubung", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal Print: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // HELPERS & SHIFT
    // ==========================================
    private fun addToCart(p: Product) { cartList.add(p); updateTotalUI() }
    private fun removeFromCart(p: Product) { if (cartList.contains(p)) { cartList.remove(p); updateTotalUI(); Toast.makeText(this, "Dihapus", Toast.LENGTH_SHORT).show() } }

    private fun checkOpenShift() { RetrofitClient.api(this).checkShift(userId).enqueue(object : Callback<CheckShiftResponse> { override fun onResponse(call: Call<CheckShiftResponse>, response: Response<CheckShiftResponse>) { if (response.isSuccessful && response.body()?.has_open_shift == true) { currentShiftId = response.body()!!.shift!!.id; currentInitialCash = response.body()!!.shift!!.initial_cash; loadProducts() } else showStartShiftDialog() }; override fun onFailure(call: Call<CheckShiftResponse>, t: Throwable) {} }) }
    private fun showStartShiftDialog() { val input = EditText(this); input.inputType = InputType.TYPE_CLASS_NUMBER; val container = FrameLayout(this); val p = FrameLayout.LayoutParams(-1, -2); p.leftMargin=50; p.rightMargin=50; input.layoutParams=p; container.addView(input); AlertDialog.Builder(this).setTitle("Buka Toko").setView(container).setPositiveButton("BUKA"){_,_-> if(input.text.isNotEmpty()) startShiftOnServer(input.text.toString().toDouble())}.setNegativeButton("LOGOUT"){_,_-> performLogout()}.setCancelable(false).show() }
    private fun startShiftOnServer(m: Double) { RetrofitClient.api(this).startShift(StartShiftRequest(userId, m)).enqueue(object : Callback<StartShiftResponse> { override fun onResponse(call: Call<StartShiftResponse>, response: Response<StartShiftResponse>) { if(response.isSuccessful) { currentShiftId = response.body()!!.shift.id; currentInitialCash = m; loadProducts() } }; override fun onFailure(call: Call<StartShiftResponse>, t: Throwable) {} }) }
    private fun showLogoutOptionsDialog() {
        val options = arrayOf("Dashboard (Grafik)", "Tutup Toko (Rekap)", "Ganti Akun", "Batal")

        AlertDialog.Builder(this)
            .setTitle("Menu Kasir")
            // Perhatikan: kita beri nama "dialog" di sini
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> startActivity(android.content.Intent(this, DashboardActivity::class.java))
                    1 -> fetchRecapFromServer()
                    2 -> performLogout()
                    3 -> dialog.dismiss() // Maka di sini juga harus "dialog", bukan "d"
                }
            }
            .show()
    }
    private fun fetchShiftRecapFromServer() {
        // Tampilkan Loading kecil biar user tahu sedang proses
        val loading = android.app.ProgressDialog(this)
        loading.setMessage("Menghitung Rekap...")
        loading.setCancelable(false)
        loading.show()

        RetrofitClient.api(this).syncShift(currentShiftId).enqueue(object : Callback<SyncShiftResponse> {
            override fun onResponse(call: Call<SyncShiftResponse>, response: Response<SyncShiftResponse>) {
                loading.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!

                    // Update data lokal dengan data server yang PASTI BENAR
                    localCashSales = data.total_cash
                    localNonCashSales = data.total_non_cash

                    // Baru tampilkan dialog
                    showShiftRecapDialog(localCashSales, localNonCashSales)
                } else {
                    Toast.makeText(applicationContext, "Gagal sinkron data shift", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SyncShiftResponse>, t: Throwable) {
                loading.dismiss()
                Toast.makeText(applicationContext, "Error Koneksi: ${t.message}", Toast.LENGTH_SHORT).show()

                // Opsional: Tetap buka dialog dengan data lokal seadanya jika offline
                showShiftRecapDialog(localCashSales, localNonCashSales)
            }
        })
    }
    private fun showShiftRecapDialog(c: Double, n: Double) { val v = LayoutInflater.from(this).inflate(R.layout.dialog_shift_recap, null); val d = AlertDialog.Builder(this).setView(v).create(); d.setCancelable(false); v.findViewById<TextView>(R.id.tvShiftInitial).text=formatRupiah(currentInitialCash); v.findViewById<TextView>(R.id.tvShiftCashSales).text=formatRupiah(c); v.findViewById<TextView>(R.id.tvShiftTotalCash).text=formatRupiah(currentInitialCash+c); v.findViewById<TextView>(R.id.tvShiftNonCash).text=formatRupiah(n); v.findViewById<Button>(R.id.btnShiftBack).setOnClickListener{d.dismiss()}; v.findViewById<Button>(R.id.btnShiftClose).setOnClickListener{d.dismiss(); endShiftOnServer()}; v.findViewById<Button>(R.id.btnPrintRekap).setOnClickListener { printRekapBluetooth(c, n) }; d.show() }
    private fun endShiftOnServer() { RetrofitClient.api(this).endShift(EndShiftRequest(currentShiftId)).enqueue(object : Callback<EndShiftResponse> { override fun onResponse(call: Call<EndShiftResponse>, response: Response<EndShiftResponse>) { if(response.isSuccessful) performLogout() }; override fun onFailure(call: Call<EndShiftResponse>, t: Throwable) {} }) }
    private fun performLogout() { startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }); finish() }

    private fun printRekapBluetooth(c: Double, n: Double) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1); return }
        try { val conn = BluetoothPrintersConnections.selectFirstPaired(); if (conn != null) { val p = EscPosPrinter(conn, 203, 48f, 32); try { p.printFormattedText("[C]<b>LAPORAN TUTUP SHIFT</b>\n[C]================================\n[L]Kasir:[R]$cashierName\n[L]Waktu:[R]${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())}\n[C]--------------------------------\n[L]<b>1. UANG FISIK</b>\n[L]Modal:[R]${formatRupiah(currentInitialCash)}\n[L]Tunai:[R]${formatRupiah(c)}\n[R]----------------\n[L]<b>TOTAL LACI:</b>[R]<b>${formatRupiah(currentInitialCash+c)}</b>\n[C]--------------------------------\n[L]<b>2. BANK (NON-TUNAI)</b>\n[L]QRIS/Kartu:[R]${formatRupiah(n)}\n[C]--------------------------------\n[L]<b>TOTAL OMZET:</b>[R]<b>${formatRupiah(c+n)}</b>\n[C]================================") } finally { p.disconnectPrinter() } } } catch (e: Exception) {}
    }
    private fun updateTotalUI() {
        val total = cartList.sumOf { it.price }
        tvTotalAmount.text = formatRupiah(total)
        btnCheckout.text = if (cartList.isNotEmpty()) "BAYAR (${cartList.size})" else "BAYAR"
    }
    private fun setupCategoryTabs() {
        llCategories.removeAllViews()
        val categories = fullProductList.map { it.category?.name }.distinct().filterNotNull().toMutableList()
        categories.add(0, "SEMUA")

        for (catName in categories) {
            val btn = Button(this)
            btn.text = catName
            btn.textSize = 12f
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.setMargins(0, 0, 16, 0)
            btn.layoutParams = params
            btn.setBackgroundColor(Color.LTGRAY)
            btn.setTextColor(Color.BLACK)

            btn.setOnClickListener {
                if (catName == "SEMUA") productAdapter.updateData(fullProductList)
                else productAdapter.updateData(fullProductList.filter { it.category?.name == catName })

                // Visual Feedback
                for (i in 0 until llCategories.childCount) {
                    val child = llCategories.getChildAt(i) as Button
                    child.setBackgroundColor(Color.LTGRAY); child.setTextColor(Color.BLACK)
                }
                btn.setBackgroundColor(Color.parseColor("#2196F3")); btn.setTextColor(Color.WHITE)
            }
            llCategories.addView(btn)
        }
    }

    private fun loadProducts() {
        RetrofitClient.api(this).getProducts().enqueue(object : Callback<ProductResponse> {
            override fun onResponse(call: Call<ProductResponse>, response: Response<ProductResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    // Ambil list dari dalam wrapper "data"
                    fullProductList = response.body()!!.data

                    productAdapter.updateData(fullProductList)
                    setupCategoryTabs()
                } else {
                    Toast.makeText(applicationContext, "Gagal ambil produk", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ProductResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Error Koneksi: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    // --- FUNGSI TAMBAHAN: AMBIL REKAP DARI SERVER ---
    private fun fetchRecapFromServer() {
        // Tampilkan Loading agar user tahu proses sedang berjalan
        val loading = android.app.ProgressDialog(this)
        loading.setMessage("Sinkronisasi Data Shift...")
        loading.setCancelable(false)
        loading.show()

        // Panggil API Sync
        RetrofitClient.api(this).syncShift(currentShiftId).enqueue(object : Callback<SyncShiftResponse> {
            override fun onResponse(call: Call<SyncShiftResponse>, response: Response<SyncShiftResponse>) {
                loading.dismiss()
                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!

                    // Update Data Lokal dengan Data Server yang Akurat
                    localCashSales = data.total_cash
                    localNonCashSales = data.total_non_cash

                    // Tampilkan Dialog Rekap
                    showShiftRecapDialog(localCashSales, localNonCashSales)
                } else {
                    Toast.makeText(applicationContext, "Gagal sinkron: ${response.code()}", Toast.LENGTH_SHORT).show()
                    // Jika gagal, tetap tampilkan dialog pakai data lokal seadanya
                    showShiftRecapDialog(localCashSales, localNonCashSales)
                }
            }

            override fun onFailure(call: Call<SyncShiftResponse>, t: Throwable) {
                loading.dismiss()
                Toast.makeText(applicationContext, "Koneksi Error: ${t.message}", Toast.LENGTH_SHORT).show()
                // Jika offline, tetap tampilkan dialog
                showShiftRecapDialog(localCashSales, localNonCashSales)
            }
        })
    }
    private fun calculateNoOfColumns(c: Context, w: Float): Int { val dm = c.resources.displayMetrics; val cols = (dm.widthPixels/dm.density/w).toInt(); return if(cols<2) 2 else cols }
    private fun formatRupiah(n: Double) = NumberFormat.getCurrencyInstance(Locale("in", "ID")).format(n)
}