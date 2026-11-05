#!/system/bin/sh
# ==============================================================================
# SKRIP AUTO RESTORE JAM, AUTO-STEP +1 HARI & BEBASKAN RAM ANDROID TV
# ==============================================================================

SAVE_FILE="/sdcard/tv_last_time.txt"
EPOCH_FILE="/sdcard/tv_last_epoch.txt"
MAX_DAYS=30
DAY_SECONDS=86400
CURRENT_OFFSET=0
IS_STEPPING=0
BASE_EPOCH=1750809600 # Default fallback: 25 Juni 2025 00:00:00 UTC
LAST_SAVE_TICK=0

# Tunggu sampai TV selesai booting
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
done

# 1. Pulihkan jam terakhir jika waktu mereset ke masa lalu
CURRENT_YEAR=$(date +%Y)
if [ "$CURRENT_YEAR" -lt 2025 ]; then
    if [ -f "$SAVE_FILE" ]; then
        SAVED_TIME=$(cat "$SAVE_FILE")
        if [ -n "$SAVED_TIME" ]; then
            date "$SAVED_TIME" >/dev/null 2>&1 || toybox date "$SAVED_TIME" >/dev/null 2>&1
        fi
    fi
fi

# Simpan epoch awal jika sudah valid (>= 2025)
if [ "$(date +%Y)" -ge 2025 ]; then
    date +%s > "$EPOCH_FILE"
    date +%m%d%H%M%Y.%S > "$SAVE_FILE"
fi

# 2. Bebaskan RAM 5 detik setelah menyala
sleep 5
sync
echo 3 > /proc/sys/vm/drop_caches

# Fungsi cek apakah Wi-Fi / LAN / Seluler terhubung (memiliki route jaringan)
has_network_signal() {
    ip route 2>/dev/null | grep -q -E "wlan|eth|rmnet|default"
    return $?
}

# Fungsi cek apakah ada koneksi internet aktif
has_internet() {
    ping -c 1 -W 2 8.8.8.8 >/dev/null 2>&1 && return 0
    ping -c 1 -W 2 1.1.1.1 >/dev/null 2>&1 && return 0
    return 1
}

# 3. Loop Pemantauan Koneksi, Auto-Step +1 Hari (4 Detik, Maks 30 Hari), & Auto-Save
while true; do
    if has_network_signal; then
        if has_internet; then
            # Internet Tersambung: Hentikan stepping & simpan waktu terbaru setiap ~3 menit
            if [ "$IS_STEPPING" -eq 1 ]; then
                IS_STEPPING=0
                CURRENT_OFFSET=0
            fi

            YEAR_NOW=$(date +%Y)
            if [ "$YEAR_NOW" -ge 2025 ]; then
                NOW_EPOCH=$(date +%s)
                if [ $((NOW_EPOCH - LAST_SAVE_TICK)) -ge 180 ] || [ "$LAST_SAVE_TICK" -eq 0 ]; then
                    echo "$NOW_EPOCH" > "$EPOCH_FILE"
                    date +%m%d%H%M%Y.%S > "$SAVE_FILE"
                    LAST_SAVE_TICK=$NOW_EPOCH
                fi
            fi
            sleep 4
        else
            # Terhubung ke Wi-Fi/Sinyal tapi TIDAK ADA internet -> Majukan tanggal +1 hari tiap 4 detik
            if [ "$IS_STEPPING" -eq 0 ]; then
                if [ -f "$EPOCH_FILE" ]; then
                    BASE_EPOCH=$(cat "$EPOCH_FILE")
                elif [ "$(date +%Y)" -ge 2025 ]; then
                    BASE_EPOCH=$(date +%s)
                    echo "$BASE_EPOCH" > "$EPOCH_FILE"
                fi
                IS_STEPPING=1
                CURRENT_OFFSET=0
            fi

            CURRENT_OFFSET=$((CURRENT_OFFSET + 1))
            if [ "$CURRENT_OFFSET" -gt "$MAX_DAYS" ]; then
                # Mentok 30 hari ke depan tanpa internet, ulang ke tanggal dasar awal (offset 0)
                CURRENT_OFFSET=0
            fi

            TARGET_EPOCH=$((BASE_EPOCH + (CURRENT_OFFSET * DAY_SECONDS)))
            # Ubah tanggal sistem menggunakan epoch (@timestamp) atau format MMddHHmmyyyy.ss
            date -s "@$TARGET_EPOCH" >/dev/null 2>&1 || toybox date "@$TARGET_EPOCH" >/dev/null 2>&1 || {
                FMT_DATE=$(date -d "@$TARGET_EPOCH" +%m%d%H%M%Y.%S 2>/dev/null)
                [ -n "$FMT_DATE" ] && date "$FMT_DATE" >/dev/null 2>&1
            }
            am broadcast -a android.intent.action.TIME_SET >/dev/null 2>&1

            sleep 4
        fi
    else
        # Tidak ada sinyal Wi-Fi/LAN sama sekali -> kembalikan ke tanggal dasar jika habis stepping
        if [ "$IS_STEPPING" -eq 1 ]; then
            IS_STEPPING=0
            CURRENT_OFFSET=0
            if [ -f "$SAVE_FILE" ]; then
                SAVED_TIME=$(cat "$SAVE_FILE")
                [ -n "$SAVED_TIME" ] && date "$SAVED_TIME" >/dev/null 2>&1
            fi
        fi
        sleep 4
    fi
done
