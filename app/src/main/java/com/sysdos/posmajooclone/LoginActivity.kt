package com.sysdos.posmajooclone

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSettings = findViewById<Button>(R.id.btnSettings) // <-- Tombol Baru

        // 1. LOGIKA TOMBOL SETTING
        btnSettings.setOnClickListener {
            // Pindah ke Halaman SettingsActivity
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 2. LOGIKA LOGIN
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Isi semua data!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = LoginRequest(email, password)

            // PENTING: Gunakan .api(this) agar IP ikut settingan
            RetrofitClient.api(this).loginUser(request).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        val user = response.body()?.user
                        Toast.makeText(applicationContext, "Halo, ${user?.name}!", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        intent.putExtra("USER_ID", user?.id)
                        intent.putExtra("USER_NAME", user?.name)
                        intent.putExtra("USER_ROLE", user?.role)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(applicationContext, "Login Gagal! Cek Email/Pass", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(applicationContext, "Error Server: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}