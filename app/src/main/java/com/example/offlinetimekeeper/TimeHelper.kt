package com.example.offlinetimekeeper

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeHelper {
    // Penanda apakah proses stepping +1 hari sedang berjalan agar tidak menimpa simpanan waktu dasar
    @Volatile
    var isSteppingActive: Boolean = false

    @Volatile
    var currentStepDayOffset: Int = 0

    fun restoreSystemTime(timeMillis: Long): Boolean {
        // Metode 1: Via SystemClock API (Izin SET_TIME via ADB)
        try {
            val success = SystemClock.setCurrentTimeMillis(timeMillis)
            if (success) {
                Log.d("TimeKeeper", "Berhasil set jam via SystemClock API")
                return true
            }
        } catch (e: Exception) {
            Log.w("TimeKeeper", "SystemClock gagal: ${e.message}")
        }

        // Metode 2: Via Root Shell (Format MMddHHmmyyyy.ss & epoch fallback)
        try {
            val sdf = SimpleDateFormat("MMddHHmmyyyy.ss", Locale.US)
            val dateStr = sdf.format(Date(timeMillis))
            val epochSec = timeMillis / 1000L
            val cmd = "date $dateStr || toybox date $dateStr || date -s @$epochSec ; am broadcast -a android.intent.action.TIME_SET"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d("TimeKeeper", "Berhasil set jam via Root su date")
                return true
            }
        } catch (e: Exception) {
            Log.e("TimeKeeper", "Root shell gagal: ${e.message}")
        }
        return false
    }

    fun clearRam(context: Context) {
        // 1. Matikan aplikasi pihak ketiga di latar belakang
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            for (packageInfo in packages) {
                val pkgName = packageInfo.packageName
                if (pkgName != context.packageName && (packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                    am.killBackgroundProcesses(pkgName)
                }
            }
            Log.d("TimeKeeper", "Pembersihan background processes selesai")
        } catch (e: Exception) {
            Log.e("TimeKeeper", "Gagal kill background processes: ${e.message}")
        }

        // 2. Jika TV Root, bersihkan buffer & kernel cache memori
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "sync && echo 3 > /proc/sys/vm/drop_caches"))
            process.waitFor()
            Log.d("TimeKeeper", "Kernel RAM cache berhasil dibersihkan via Root")
        } catch (e: Exception) {
            // Lewati jika bukan perangkat root
        }
    }
}
