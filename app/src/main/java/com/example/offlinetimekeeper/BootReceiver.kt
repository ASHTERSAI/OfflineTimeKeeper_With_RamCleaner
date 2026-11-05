package com.example.offlinetimekeeper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
            val lastSavedTime = prefs.getLong("last_time", 0L)

            // 1. Pulihkan Jam Terakhir Sebelum TV Mati
            if (lastSavedTime > 0 && System.currentTimeMillis() < lastSavedTime) {
                val targetTime = lastSavedTime + 30000L
                TimeHelper.restoreSystemTime(targetTime)
            }

            // 2. Pembersihan RAM Otomatis Saat Boot Selesai
            Thread {
                try {
                    // Jeda 5 detik agar inisialisasi modul sistem TV selesai
                    Thread.sleep(5000)
                    TimeHelper.clearRam(context)
                } catch (e: Exception) {
                    Log.e("TimeKeeper", "Gagal eksekusi auto clear RAM: ${e.message}")
                }
            }.start()

            // 3. Jalankan Service Penyimpan Jam Berkala
            val serviceIntent = Intent(context, TimeSaverService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
