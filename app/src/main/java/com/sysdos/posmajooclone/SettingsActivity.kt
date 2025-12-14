package com.sysdos.posmajooclone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etIp = findViewById<EditText>(R.id.etIpAddress)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // 1. Ambil IP yang tersimpan (atau default jika belum ada)
        val prefs = getSharedPreferences("POS_PREFS", Context.MODE_PRIVATE)
        val currentIp = prefs.getString("BASE_URL", "http://192.168.1.8:8080/")
        etIp.setText(currentIp)

        // 2. Tombol Simpan
        btnSave.setOnClickListener {
            var newIp = etIp.text.toString().trim()
            if (newIp.isNotEmpty()) {
                // Pastikan format URL valid (harus ada http:// dan diakhiri /)
                if (!newIp.startsWith("http")) newIp = "http://$newIp"
                if (!newIp.endsWith("/")) newIp = "$newIp/"

                // Simpan ke memori HP
                prefs.edit().putString("BASE_URL", newIp).apply()

                // Reset Retrofit agar membaca IP baru
                RetrofitClient.reset()

                Toast.makeText(this, "IP Tersimpan! Silakan Login ulang.", Toast.LENGTH_LONG).show()

                // Restart ke Halaman Login
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                Toast.makeText(this, "IP tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { finish() }
    }
}