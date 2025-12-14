package com.sysdos.posmajooclone

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.NumberFormat
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val chart = findViewById<BarChart>(R.id.barChart)
        val btnBack = findViewById<Button>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        loadData(chart)
    }

    private fun loadData(chart: BarChart) {
        RetrofitClient.api(this).getDashboard().enqueue(object : Callback<DashboardResponse> {
            override fun onResponse(call: Call<DashboardResponse>, response: Response<DashboardResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.data
                    setupChart(chart, list)
                } else {
                    Toast.makeText(applicationContext, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<DashboardResponse>, t: Throwable) {
                Toast.makeText(applicationContext, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupChart(chart: BarChart, data: List<ChartData>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        data.forEachIndexed { index, item ->
            // PERBAIKAN: Tambahkan .toFloat() pada item.total
            entries.add(BarEntry(index.toFloat(), item.total.toFloat()))

            // Ambil tanggal saja
            val day = try { item.date.split("-")[2] } catch (e: Exception) { item.date }
            labels.add(day)
        }

        val dataSet = BarDataSet(entries, "Omzet Harian")
        dataSet.color = Color.parseColor("#2196F3") // Warna Biru
        dataSet.valueTextSize = 12f

        // Format angka jadi Rupiah di atas batang
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return NumberFormat.getNumberInstance(Locale("in", "ID")).format(value)
            }
        }

        val barData = BarData(dataSet)
        chart.data = barData
        chart.description.isEnabled = false
        chart.legend.isEnabled = false

        // Konfigurasi Sumbu X (Tanggal)
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chart.xAxis.granularity = 1f

        // Konfigurasi Sumbu Y (Angka Kiri & Kanan)
        chart.axisRight.isEnabled = false
        chart.axisLeft.axisMinimum = 0f

        chart.animateY(1000) // Animasi naik
        chart.invalidate() // Refresh
    }
}