package com.sysdos.posmajooclone // SESUAIKAN

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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
data class RekapData(
    val total_sales: Double,      // Total Omzet
    val total_cash_sales: Double  // Total Uang Tunai Saja
)

// --- PRODUK ---
data class ProductResponse(val data: List<Product>)
data class Product(val id: Int, val name: String, val price: Double, val stock: Int, val category: Category?)
data class Category(val id: Int, val name: String)

// --- CHECKOUT ---
data class CheckoutRequest(
    val shift_id: Int,
    val invoice_number: String,
    val subtotal: Double,       // Baru
    val discount_amount: Double,// Baru
    val tax_amount: Double,     // Baru
    val total_amount: Double,   // Grand Total
    val cashier_name: String,
    val payment_method: String,
    val details: List<CheckoutDetail>
)
data class CheckoutDetail(val product_id: Int, val qty: Int, val price_at_transaction: Double)

// --- HISTORY (MODEL INI YANG BIKIN ERROR KALAU TIDAK LENGKAP) ---
data class TransactionResponse(val data: List<TransactionItem>)

data class TransactionItem(
    val id: Int,
    val invoice_number: String,
    val total_amount: Double,
    val cashier_name: String,   // WAJIB ADA
    val payment_method: String, // WAJIB ADA
    val created_at: String,
    val details: List<HistoryDetail>
)

data class HistoryDetail(
    val id: Int,
    val qty: Int,
    val price_at_transaction: Double,
    val product: Product // Relasi Nama Produk
)
data class SettingResponse(
    val id: Int,
    val tax_rate: Double,
    val is_tax_enabled: Boolean,
    val is_discount_enabled: Boolean
)



// --- API INTERFACE ---
interface ApiService {
    @POST("/login") fun loginUser(@Body request: LoginRequest): Call<LoginResponse>

    // Shift
    @GET("/shift/check/{user_id}") fun checkShift(@Path("user_id") userId: Int): Call<CheckShiftResponse>
    @POST("/shift/start") fun startShift(@Body request: StartShiftRequest): Call<StartShiftResponse>
    @POST("/shift/end") fun endShift(@Body request: EndShiftRequest): Call<EndShiftResponse>

    // Transaksi
    @GET("/products") fun getProducts(): Call<ProductResponse>
    @POST("/checkout") fun createTransaction(@Body request: CheckoutRequest): Call<Any>
    @GET("/transactions") fun getTransactions(): Call<TransactionResponse>
    @GET("/settings") fun getSettings(): Call<SettingResponse>
}

object RetrofitClient {
    private const val BASE_URL = "http://127.0.0.1:8080/"
    val instance: ApiService by lazy {
        Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}