package com.sysdos.posmajooclone

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EditProductActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_product)

        val etName = findViewById<EditText>(R.id.etName)
        val etPrice = findViewById<EditText>(R.id.etPrice)
        val etStock = findViewById<EditText>(R.id.etStock)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        // 1. Ambil Data dari Intent (Dikirim dari Menu Utama)
        val id = intent.getIntExtra("ID", 0)
        val name = intent.getStringExtra("NAME") ?: ""
        val price = intent.getDoubleExtra("PRICE", 0.0)
        val stock = intent.getIntExtra("STOCK", 0)
        val catId = intent.getIntExtra("CAT_ID", 1) // Default Kategori 1 dulu

        // 2. Tampilkan di Form
        etName.setText(name)
        etPrice.setText(price.toInt().toString())
        etStock.setText(stock.toString())

        btnCancel.setOnClickListener { finish() }

        // 3. Simpan Perubahan
        btnSave.setOnClickListener {
            val newName = etName.text.toString()
            val newPrice = etPrice.text.toString()
            val newStock = etStock.text.toString()

            if (newName.isEmpty() || newPrice.isEmpty() || newStock.isEmpty()) {
                Toast.makeText(this, "Data tidak boleh kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Buat RequestBody untuk teks
            val rbName = newName.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbPrice = newPrice.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbStock = newStock.toRequestBody("text/plain".toMediaTypeOrNull())
            val rbCat = catId.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            // Panggil API (Image null dulu karena edit gambar agak rumit, kita fokus text dulu)
            RetrofitClient.api(this).editProduct(id, rbName, rbPrice, rbStock, rbCat, null)
                .enqueue(object : Callback<Any> {
                    override fun onResponse(call: Call<Any>, response: Response<Any>) {
                        if (response.isSuccessful) {
                            Toast.makeText(applicationContext, "Produk Berhasil Diupdate!", Toast.LENGTH_SHORT).show()
                            finish() // Kembali ke menu utama
                        } else {
                            Toast.makeText(applicationContext, "Gagal Update: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    override fun onFailure(call: Call<Any>, t: Throwable) {
                        Toast.makeText(applicationContext, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }
}