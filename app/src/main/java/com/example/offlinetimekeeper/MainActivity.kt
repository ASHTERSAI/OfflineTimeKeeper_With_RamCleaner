package com.example.offlinetimekeeper

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val uiHandler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView

    private val uiRefreshRunnable = object : Runnable {
        override fun run() {
            if (::statusView.isInitialized) {
                updateStatusText(statusView)
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(64, 64, 64, 64)
        }

        val titleView = TextView(this).apply {
            text = "Offline Time Keeper & RAM Cleaner"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusView = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#BBBBBB"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 48)
        }

        val btnSaveNow = Button(this).apply {
            text = "Simpan Waktu Sekarang"
            setOnClickListener {
                val now = System.currentTimeMillis()
                TimeHelper.isSteppingActive = false
                TimeHelper.currentStepDayOffset = 0
                getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
                    .edit().putLong("last_time", now).apply()
                updateStatusText(statusView)
                Toast.makeText(this@MainActivity, "Waktu dasar lokal berhasil disimpan!", Toast.LENGTH_SHORT).show()
            }
        }

        val btnSyncOnline = Button(this).apply {
            text = "Sinkronkan Jam Online Sekarang"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Menghubungi server waktu...", Toast.LENGTH_SHORT).show()
                Thread {
                    val ntpTime = SntpHelper.getNetworkTime("pool.ntp.org", 4000)
                        ?: SntpHelper.getNetworkTime("time.google.com", 4000)

                    runOnUiThread {
                        if (ntpTime != null) {
                            TimeHelper.isSteppingActive = false
                            TimeHelper.currentStepDayOffset = 0
                            TimeHelper.restoreSystemTime(ntpTime)
                            getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
                                .edit().putLong("last_time", ntpTime).apply()
                            updateStatusText(statusView)
                            Toast.makeText(this@MainActivity, "Jam berhasil disinkronkan ke waktu online!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Gagal mendapatkan waktu online (Cek Internet)", Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }

        val btnClearRam = Button(this).apply {
            text = "Bersihkan RAM Sekarang"
            setOnClickListener {
                TimeHelper.clearRam(this@MainActivity)
                Toast.makeText(this@MainActivity, "RAM berhasil dibebaskan!", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(titleView)
        layout.addView(statusView)
        layout.addView(btnSaveNow)
        layout.addView(btnSyncOnline)
        layout.addView(btnClearRam)
        setContentView(layout)

        updateStatusText(statusView)

        val serviceIntent = Intent(this, TimeSaverService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(uiRefreshRunnable)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(uiRefreshRunnable)
        super.onPause()
    }

    private fun updateStatusText(tv: TextView) {
        val prefs = getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
        val lastTime = prefs.getLong("last_time", 0L)
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
        val lastStr = if (lastTime > 0) sdf.format(Date(lastTime)) else "Belum ada"
        val nowStr = sdf.format(Date(System.currentTimeMillis()))

        val modeStr = if (TimeHelper.isSteppingActive) {
            "Mencari Internet: Auto-Step Tanggal H+${TimeHelper.currentStepDayOffset} / 30 (Siklus 4 Detik)"
        } else {
            "Auto-Restore Offline + Auto-Step +1 Hari (30 Hari) + Auto-Sync Online + RAM Cleaner Aktif"
        }

        tv.text = "Jam Sistem: $nowStr
Tanggal Dasar Tersimpan: $lastStr

Status: $modeStr"
    }
}
