package com.example.offlinetimekeeper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Executors

class TimeSaverService : Service() {
    private var saveTimer: Timer? = null
    private val CHANNEL_ID = "TimeKeeperChannel"
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val stepHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var baseSavedTimeMillis: Long = 0L
    private var currentDayOffset: Int = 0
    private val MAX_STEP_DAYS = 30
    private val STEP_INTERVAL_MS = 4000L // 4 detik
    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    private val FALLBACK_BASE_TIME = 1750809600000L // 25 Juni 2025 00:00:00 UTC jika belum ada simpanan

    // Runnable pemantau koneksi & Auto-Step +1 hari setiap 4 detik saat ada WiFi/Sinyal tapi tanpa Internet
    private val networkDateStepperRunnable = object : Runnable {
        override fun run() {
            executor.execute {
                val hasSignal = isConnectedToLocalNetwork()
                val hasInternet = if (hasSignal) isInternetValidatedOrActive() else false

                if (hasSignal && !hasInternet) {
                    // Terhubung ke Wi-Fi/LAN/Seluler, tapi TIDAK ADA internet -> Mulai maju +1 hari tiap 4 detik
                    val prefs = getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
                    if (!TimeHelper.isSteppingActive) {
                        val saved = prefs.getLong("last_time", 0L)
                        baseSavedTimeMillis = if (saved > 1735689600000L) {
                            saved
                        } else if (System.currentTimeMillis() > 1735689600000L) {
                            val now = System.currentTimeMillis()
                            prefs.edit().putLong("last_time", now).apply()
                            now
                        } else {
                            FALLBACK_BASE_TIME
                        }
                        currentDayOffset = 0
                        TimeHelper.isSteppingActive = true
                        TimeHelper.currentStepDayOffset = 0
                        Log.d("TimeKeeper", "Sinyal terdeteksi tanpa internet. Kunci tanggal dasar: $baseSavedTimeMillis")
                    }

                    // Tambah +1 hari setiap siklus 4 detik; jika lewat 30 hari, ulang kembali ke tanggal dasar (offset 0)
                    currentDayOffset++
                    if (currentDayOffset > MAX_STEP_DAYS) {
                        currentDayOffset = 0
                        Log.d("TimeKeeper", "Mentok 30 hari ke depan tanpa internet, mengulang ke tanggal dasar awal.")
                    }
                    TimeHelper.currentStepDayOffset = currentDayOffset

                    val targetStepTime = baseSavedTimeMillis + (currentDayOffset * ONE_DAY_MS)
                    TimeHelper.restoreSystemTime(targetStepTime)

                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateLabel = sdf.format(Date(targetStepTime))
                    updateNotificationText("Mencari internet... Geser tanggal H+$currentDayOffset ($dateLabel)")
                    Log.d("TimeKeeper", "Auto-Step Tanggal: H+$currentDayOffset -> $dateLabel")

                } else if (hasSignal && hasInternet) {
                    // Koneksi Internet Pulih / Aktif!
                    if (TimeHelper.isSteppingActive) {
                        TimeHelper.isSteppingActive = false
                        currentDayOffset = 0
                        TimeHelper.currentStepDayOffset = 0
                        updateNotificationText("Internet terhubung! Menyinkronkan jam online...")
                        Log.d("TimeKeeper", "Internet pulih! Menghentikan Auto-Step tanggal & menyinkronkan waktu.")
                        triggerOnlineSync()
                    } else {
                        // Simpan waktu valid saat internet aktif
                        val now = System.currentTimeMillis()
                        if (now > 1735689600000L) {
                            getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
                                .edit().putLong("last_time", now).apply()
                        }
                    }
                } else {
                    // Tidak terhubung ke jaringan Wi-Fi / LAN / Seluler sama sekali
                    if (TimeHelper.isSteppingActive) {
                        TimeHelper.isSteppingActive = false
                        currentDayOffset = 0
                        TimeHelper.currentStepDayOffset = 0
                        // Kembalikan ke tanggal dasar simpanan terakhir
                        if (baseSavedTimeMillis > 1735689600000L) {
                            TimeHelper.restoreSystemTime(baseSavedTimeMillis)
                        }
                        updateNotificationText("Memantau jam sistem & sinkronisasi otomatis")
                    }
                }

                // Jadwalkan pengecekan berikutnya dalam 4 detik
                stepHandler.postDelayed(this, STEP_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Offline Time Keeper Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Offline Time Keeper & RAM Cleaner")
                .setContentText("Memantau jam sistem & sinkronisasi otomatis")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .build()

            startForeground(1, notification)
        }

        registerNetworkListener()
        stepHandler.post(networkDateStepperRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        saveTimer?.cancel()
        saveTimer = Timer()
        // Simpan waktu lokal setiap 3 menit HANYA jika tidak sedang dalam mode stepping +1 hari
        saveTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (!TimeHelper.isSteppingActive) {
                    val now = System.currentTimeMillis()
                    if (now > 1735689600000L) {
                        val prefs = getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
                        prefs.edit().putLong("last_time", now).apply()
                    }
                }
            }
        }, 0, 3 * 60 * 1000L)

        // Coba sinkronisasi online langsung saat service baru aktif
        triggerOnlineSync()

        return START_STICKY
    }

    // Cek apakah perangkat terhubung ke sinyal Wi-Fi, LAN (Ethernet), atau Seluler
    private fun isConnectedToLocalNetwork(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                @Suppress("DEPRECATION")
                info != null && info.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    // Cek apakah internet benar-benar tersambung (HTTP 204 check atau NTP/HTTP Date)
    private fun isInternetValidatedOrActive(): Boolean {
        // Uji koneksi langsung dengan timeout singkat (2 detik) agar siklus 4 detik tetap presisi
        if (SntpHelper.hasActiveInternet(2000)) {
            return true
        }
        // Fallback: cek apakah sistem Android sudah menandai NET_CAPABILITY_VALIDATED
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Abaikan
        }
        return false
    }

    private fun updateNotificationText(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java)
                val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Offline Time Keeper & RAM Cleaner")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .build()
                manager?.notify(1, notification)
            } catch (e: Exception) {
                // Abaikan jika gagal update notifikasi
            }
        }
    }

    private fun registerNetworkListener() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("TimeKeeper", "Sinyal jaringan terdeteksi! Memeriksa koneksi waktu online...")
                    triggerOnlineSync()
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e("TimeKeeper", "Gagal register network callback: ${e.message}")
        }
    }

    private fun triggerOnlineSync() {
        Thread {
            try {
                val ntpServers = listOf("pool.ntp.org", "time.google.com", "id.pool.ntp.org")
                var onlineTime: Long? = null

                for (server in ntpServers) {
                    onlineTime = SntpHelper.getNetworkTime(server, 3000)
                    if (onlineTime != null) {
                        break
                    }
                }

                if (onlineTime != null && onlineTime > 1735689600000L) {
                    TimeHelper.isSteppingActive = false
                    currentDayOffset = 0
                    TimeHelper.currentStepDayOffset = 0
                    TimeHelper.restoreSystemTime(onlineTime)
                    val prefs = getSharedPreferences("TimePrefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_time", onlineTime).apply()
                    updateNotificationText("Memantau jam sistem & sinkronisasi otomatis")
                    Log.d("TimeKeeper", "Jam TV berhasil disinkronkan ke waktu online!")
                }
            } catch (e: Exception) {
                Log.e("TimeKeeper", "Sinkronisasi waktu online gagal: ${e.message}")
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        saveTimer?.cancel()
        stepHandler.removeCallbacks(networkDateStepperRunnable)
        executor.shutdownNow()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        super.onDestroy()
    }
}
