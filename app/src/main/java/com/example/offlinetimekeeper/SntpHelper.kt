package com.example.offlinetimekeeper

import android.os.SystemClock
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

object SntpHelper {
    private const val NTP_PACKET_SIZE = 48
    private const val NTP_PORT = 123
    private const val NTP_MODE_CLIENT = 3
    private const val NTP_VERSION = 3
    private const val TRANSMIT_TIME_OFFSET = 40
    private const val OFFSET_1900_TO_1970 = 2208988800L

    fun getNetworkTime(host: String = "pool.ntp.org", timeout: Int = 3000): Long? {
        // 1. Coba lewat NTP Server (UDP 123)
        val ntpTime = queryNtp(host, timeout)
        if (ntpTime != null && ntpTime > 1735689600000L) {
            return ntpTime
        }

        // 2. Fallback via HTTP/HTTPS Date Header (Anti Blokir ISP & bebas kendala SSL jika HTTP)
        Log.w("TimeKeeper", "NTP UDP gagal/diblokir ISP, fallback via HTTP/HTTPS Date...")
        return getHttpNetworkTime(timeout)
    }

    // Pengecekan cepat apakah internet benar-benar aktif (HTTP 204 / ICMP / DNS)
    fun hasActiveInternet(timeoutMs: Int = 2000): Boolean {
        val checkUrls = listOf(
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://clients3.google.com/generate_204",
            "http://cp.cloudflare.com/generate_204"
        )
        for (urlStr in checkUrls) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Android")
                    setRequestProperty("Connection", "close")
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = false
                    connect()
                }
                val code = conn.responseCode
                if (code == 204 || code == 200) {
                    return true
                }
            } catch (e: Exception) {
                // Lanjut ke URL cadangan berikutnya
            } finally {
                conn?.disconnect()
            }
        }
        return false
    }

    private fun queryNtp(host: String, timeout: Int): Long? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = timeout
            val address = InetAddress.getByName(host)
            val buffer = ByteArray(NTP_PACKET_SIZE)
            buffer[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()

            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()
            writeTimeStamp(buffer, TRANSMIT_TIME_OFFSET, requestTime)

            val request = DatagramPacket(buffer, buffer.size, address, NTP_PORT)
            socket.send(request)

            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val responseTicks = SystemClock.elapsedRealtime()

            val transmitTime = readTimeStamp(buffer, TRANSMIT_TIME_OFFSET)
            val roundTripTime = responseTicks - requestTicks
            transmitTime + (roundTripTime / 2)
        } catch (e: Exception) {
            Log.e("TimeKeeper", "Query NTP ke $host gagal: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }

    private fun getHttpNetworkTime(timeout: Int): Long? {
        val targets = listOf(
            "http://connectivitycheck.gstatic.com/generate_204",
            "http://www.google.com",
            "https://www.google.com",
            "https://1.1.1.1",
            "https://www.cloudflare.com"
        )
        for (urlStr in targets) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = timeout
                conn.readTimeout = timeout
                conn.instanceFollowRedirects = false
                conn.connect()

                val dateHeader = conn.date
                if (dateHeader > 1735689600000L) {
                    Log.d("TimeKeeper", "Berhasil ambil waktu online via HTTP Date ($urlStr): $dateHeader")
                    return dateHeader
                }
            } catch (e: Exception) {
                Log.e("TimeKeeper", "HTTP time request ke $urlStr gagal: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun readTimeStamp(buffer: ByteArray, offset: Int): Long {
        var seconds = 0L
        for (i in 0..3) {
            val b = buffer[offset + i].toLong() and 0xff
            seconds = (seconds shl 8) or b
        }
        var fraction = 0L
        for (i in 4..7) {
            val b = buffer[offset + i].toLong() and 0xff
            fraction = (fraction shl 8) or b
        }
        return (seconds - OFFSET_1900_TO_1970) * 1000L + (fraction * 1000L / 0x100000000L)
    }

    private fun writeTimeStamp(buffer: ByteArray, offset: Int, time: Long) {
        val seconds = time / 1000L + OFFSET_1900_TO_1970
        val fraction = ((time % 1000L) * 0x100000000L) / 1000L
        var temp = seconds
        for (i in 3 downTo 0) {
            buffer[offset + i] = (temp and 0xff).toByte()
            temp = temp ushr 8
        }
        temp = fraction
        for (i in 7 downTo 4) {
            buffer[offset + i] = (temp and 0xff).toByte()
            temp = temp ushr 8
        }
    }
}
