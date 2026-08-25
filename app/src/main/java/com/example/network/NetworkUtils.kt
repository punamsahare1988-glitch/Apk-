package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale
import kotlin.random.Random

object NetworkUtils {

    /**
     * Gets the best active local IPv4 address (e.g. Wi-Fi 192.168.x.x, hotspot, or eth0)
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            // First check Wi-Fi Manager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                val ip = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    wifiIp and 0xff,
                    wifiIp shr 8 and 0xff,
                    wifiIp shr 16 and 0xff,
                    wifiIp shr 24 and 0xff
                )
                if (ip != "0.0.0.0") return ip
            }

            // Iterate network interfaces (works for Hotspot, Wi-Fi Direct, Ethernet, etc.)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hotspotIp: String? = null
            var wifiEthIp: String? = null

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        val name = networkInterface.name.lowercase(Locale.US)
                        if (name.contains("wlan") || name.contains("eth") || name.contains("en")) {
                            wifiEthIp = hostAddress
                        } else if (name.contains("ap") || name.contains("tether") || name.contains("rndis")) {
                            hotspotIp = hostAddress
                        } else if (wifiEthIp == null) {
                            wifiEthIp = hostAddress
                        }
                    }
                }
            }

            return wifiEthIp ?: hotspotIp ?: "192.168.1.100"
        } catch (e: Exception) {
            return "192.168.1.100"
        }
    }

    /**
     * Checks if device has an active Wi-Fi or local network connection
     */
    fun isConnectedToLocalNetwork(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
    }

    /**
     * Generates a random 4-digit or 6-digit numeric PIN
     */
    fun generateRandomPin(digits: Int = 4): String {
        val min = if (digits == 4) 1000 else 100000
        val max = if (digits == 4) 9999 else 999999
        return Random.nextInt(min, max + 1).toString()
    }

    /**
     * Formats bytes into human readable string (KB, MB, GB)
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    /**
     * Formats bitrate into human readable string (Kbps, Mbps)
     */
    fun formatBitrate(kbps: Long): String {
        if (kbps < 1000) return "$kbps Kbps"
        val mbps = kbps / 1000.0
        return String.format(Locale.US, "%.1f Mbps", mbps)
    }
}
