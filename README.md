# 📺 Offline Time Keeper & RAM Cleaner v2.1 (All-in-One Edition)

Pembaruan lengkap untuk perangkat **Android TV** dan **STB Android** yang mengalami masalah reset tanggal/jam ke setelan pabrik akibat baterai CMOS/RTC yang melemah, sekaligus mengatasi masalah penurunan performa pada perangkat dengan RAM terbatas.

Versi ini menggabungkan pemulihan waktu offline saat booting, pembersihan RAM otomatis, serta sinkronisasi waktu online anti-blokir via NTP dan fallback HTTPS.

---

### 🔄 Alur Kerja Sistem (Bertahap & Mandiri):

1. **Langkah 1 (Saat Booting / Kondisi Offline):**
   * Saat TV menyala dari kondisi mati total, aplikasi langsung memulihkan jam ke **catatan waktu terakhir yang tersimpan** (+30 detik).
   * **Tujuannya:** Menyelamatkan validitas sertifikat keamanan SSL/TLS agar jaringan Wi-Fi atau kabel LAN tidak memblokir akses internet akibat tahun yang kedaluwarsa.

2. **Langkah 2 (Pembersihan RAM Otomatis):**
   * Berjalan otomatis di latar belakang 5 detik setelah TV menyala.
   * Menghentikan beban aplikasi pihak ketiga dan membersihkan cache memori agar antarmuka TV langsung ringan dan bebas lag.

3. **Langkah 3 (Begitu Sinyal Internet Masuk):**
   * Aplikasi mendeteksi jaringan via `NetworkCallback` dan meminta waktu resmi ke server NTP internasional (`pool.ntp.org` / `time.google.com`).
   * **Anti-Blokir ISP:** Jika port NTP UDP 123 diblokir provider/router, aplikasi otomatis beralih membaca header tanggal dari HTTPS Google / Cloudflare (Port 443).
   * Jam TV langsung disinkronkan ke jam dan tanggal asli yang presisi hingga satuan detik.

4. **Langkah 4 (Pencatatan Berkala):**
   * Waktu resmi terkini otomatis disimpan ke penyimpanan internal TV setiap 3 menit sekali sebagai cadangan untuk siklus booting berikutnya.

---

### ✨ Fitur Utama:

* **Hybrid Restore & Anti-Block Sync:** Menggabungkan pemulihan offline saat booting dengan sinkronisasi online ganda (NTP UDP + HTTP Date port 443).
* **Auto & Manual RAM Cleaner:** Pembersihan RAM otomatis 5 detik setelah menyala serta tombol manual di layar TV.
* **Auto-Save Berkala:** Mencatat tanggal & jam sistem ke memori TV setiap 3 menit di latar belakang.
* **Proteksi Waktu Rusak:** Mencegah pencatatan waktu jika jam sistem masih berada di tahun pabrik (di bawah 2025).
* **Dual Execution Mode:** Mendukung izin standar `SET_TIME` via ADB (Non-Root) maupun hak akses Superuser (`su`) otomatis jika TV sudah di-root.
* **Remote TV Friendly:** Tampilan antarmuka berlatar gelap yang nyaman dinavigasi menggunakan D-pad remote TV.

---

### 📥 File Unduhan:
* **`OfflineTimeKeeper_With_RamCleaner_Project.apk`**: File instalasi aplikasi siap pasang di Android TV / Android Box.

---

### 🛠️ Petunjuk Pemasangan (Hanya 1 Kali):

1. Salin file **`OfflineTimeKeeper_With_RamCleaner_Project.apk`** ke flashdisk, lalu pasang di TV Anda menggunakan File Manager.
2. Buka aplikasi **Offline Time Keeper & RAM Cleaner** di layar TV.
3. **Pemberian Izin Sistem:**
   * **TV Non-Root:** Hubungkan TV ke laptop via ADB, lalu ketik perintah:
     ```bash
     adb shell pm grant com.example.offlinetimekeeper android.permission.SET_TIME
     ```
   * **TV Root (Magisk):** Tidak membutuhkan ADB, aplikasi otomatis meminta izin Superuser (`su`).
4. Klik tombol **Simpan Waktu Sekarang** (pastikan jam TV saat itu sudah sesuai).
5. Selesai! TV kini dapat digunakan, dimatikan, dan dihidupkan kembali secara otomatis.
