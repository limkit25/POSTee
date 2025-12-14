package com.sysdos.posmajooclone

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.Part

// ==========================================
// 1. SEMUA MODEL DATA (DITARUH DI LUAR INTERFACE)
// ==========================================

// --- PRODUK & KATEGORI ---
data class Category(val id: Int, val name: String)

data class Product(
    val id: Int,
    val name: String,
    val price: Double,
    val stock: Int,
    val image_url: String?,
    val category: Category?
)

// INI YANG BIKIN ERROR KEMARIN (SEKARANG SUDAH DILUAR)
data class ProductResponse(val data: List<Product>)

// --- LOGIN ---
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val token: String, val user: UserData)
data class UserData(val id: Int, val name: String, val email: String, val role: String)

// --- SHIFT ---
data class CheckShiftResponse(val has_open_shift: Boolean, val shift: ShiftData?)
data class ShiftData(val id: Int, val initial_cash: Double)
data class StartShiftRequest(val user_id: Int, val initial_cash: Double)
data class StartShiftResponse(val shift: ShiftData)
data class EndShiftRequest(val shift_id: Int)
data class EndShiftResponse(val rekap: RekapData)
data class RekapData(val total_sales: Double, val total_cash_sales: Double)

// --- SETTING ---
data class SettingResponse(val id: Int, val tax_rate: Double, val is_tax_enabled: Boolean, val is_discount_enabled: Boolean)

// --- TRANSAKSI (REQUEST) ---
data class CheckoutDetail(val product_id: Int, val qty: Int, val price_at_transaction: Double)

data class CheckoutRequest(
    val shift_id: Int?,
    val invoice_number: String,
    val subtotal: Double,
    val discount_amount: Double,
    val tax_amount: Double,
    val total_amount: Double,
    val cashier_name: String,
    val payment_method: String,
    val details: List<CheckoutDetail>
)

// --- TRANSAKSI (RESPONSE STRUK) ---
data class TransactionResponse(
    val message: String,
    val transaction_id: Int,
    val invoice_number: String,
    val total_amount: Double,
    val subtotal: Double,
    val discount_amount: Double,
    val tax_amount: Double,
    val created_at: String
)

// --- HISTORY (RIWAYAT) ---
data class HistoryResponse(val data: List<TransactionItem>) // Wrapper untuk history

data class HistoryDetail(
    val id: Int,
    val qty: Int,
    val price_at_transaction: Double,
    val product: Product?
)

data class TransactionItem(
    val id: Int,
    val invoice_number: String,
    val total_amount: Double,
    val subtotal: Double,
    val discount_amount: Double,
    val tax_amount: Double,
    val cashier_name: String,
    val payment_method: String,
    val created_at: String,
    val details: List<HistoryDetail>
)
data class SyncShiftResponse(
    val total_cash: Double,
    val total_non_cash: Double
)
data class ChartData(val date: String, val total: Double)
data class DashboardResponse(val data: List<ChartData>)

// ==========================================
// 2. INTERFACE API
// ==========================================
interface ApiService {
    @POST("/login")
    fun loginUser(@Body request: LoginRequest): Call<LoginResponse>

    // PRODUK (Pastikan return Call<ProductResponse>)
    @GET("/products")
    fun getProducts(): Call<ProductResponse>

    // SHIFT
    @GET("/shift/check/{user_id}")
    fun checkShift(@Path("user_id") userId: Int): Call<CheckShiftResponse>

    @POST("/shift/start")
    fun startShift(@Body request: StartShiftRequest): Call<StartShiftResponse>

    @POST("/shift/end")
    fun endShift(@Body request: EndShiftRequest): Call<EndShiftResponse>

    // TRANSAKSI
    @POST("/checkout")
    fun createTransaction(@Body request: CheckoutRequest): Call<TransactionResponse>

    @GET("/transactions")
    fun getTransactions(): Call<HistoryResponse>

    // SETTING
    @GET("/settings")
    fun getSettings(): Call<SettingResponse>

    @GET("/shift/sync/{id}")
    fun syncShift(@retrofit2.http.Path("id") shiftId: Int): Call<SyncShiftResponse>

    @GET("/api/dashboard")
    fun getDashboard(): Call<DashboardResponse>

    // TAMBAHAN: EDIT PRODUK
    @Multipart
    @POST("/api/products/edit/{id}")
    fun editProduct(
        @Path("id") id: Int,
        @Part("name") name: RequestBody,
        @Part("price") price: RequestBody,
        @Part("stock") stock: RequestBody,
        @Part("category_id") catId: RequestBody,
        @Part image: MultipartBody.Part? // Bisa Null jika tidak ganti gambar
    ): Call<Any>
}

// ==========================================
// 3. RETROFIT CLIENT
// ==========================================
// GANTI object RetrofitClient dengan class ini:
object RetrofitClient {
    private var retrofit: Retrofit? = null

    // Fungsi ini akan dipanggil oleh Activity untuk minta koneksi
    fun api(context: android.content.Context): ApiService {
        if (retrofit == null) {
            // Ambil IP dari Settingan User
            val prefs = context.getSharedPreferences("POS_PREFS", android.content.Context.MODE_PRIVATE)
            val baseUrl = prefs.getString("BASE_URL", "http://192.168.1.8:8080/")!! // Default IP

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }

    // Fungsi untuk memaksa reset (saat ganti IP)
    fun reset() {
        retrofit = null
    }
}