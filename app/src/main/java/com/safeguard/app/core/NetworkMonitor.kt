package com.safeguard.app.core

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

/**
 * Measures the *real* state of the connection so the dashboard can show
 * genuine latency / loss / throughput numbers next to the simulated ones.
 *
 * Everything here runs off the UI thread and is cancellation-aware, so the
 * Instant-Release path can tear it down in milliseconds.
 */
class NetworkMonitor(private val context: Context) {

    data class Pulse(val latencyMs: Int, val lossPercent: Float, val jitterMs: Int, val reachable: Boolean)

    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // --------------------------------------------------------------- network

    fun isOnline(): Boolean = try {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (t: Throwable) {
        false
    }

    fun activeTransport(): String = try {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return "none"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    } catch (t: Throwable) {
        "none"
    }

    fun isWifiConnected(): Boolean = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifi.isWifiEnabled && wifi.connectionInfo?.networkId != -1
    } catch (t: Throwable) {
        false
    }

    /** Real LTE/NR signal level in dBm when the platform exposes it. */
    fun realSignalDbm(): Int? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            telephony?.signalStrength?.cellSignalStrengths?.firstOrNull()?.dbm
        } else null
    } catch (t: Throwable) {
        null
    }

    /** Default gateway, parsed from /proc/net/route (world-readable, no root). */
    fun gatewayIp(): String? = try {
        BufferedReader(FileReader("/proc/net/route")).useLines { lines ->
            lines.drop(1)
                .map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { parts -> parts.size > 2 && parts[1] == "00000000" }
                ?.let { parts -> parts[2].chunked(2).reversed().joinToString(".") { it.toInt(16).toString() } }
        }
    } catch (t: Throwable) {
        null
    }

    // ------------------------------------------------------------ measurement

    /**
     * ICMP-style probe: uses the system `ping` binary (works unrooted), with a
     * TCP-handshake fallback because some OEM ROMs deny ICMP to apps.
     */
    suspend fun pulse(samples: Int = 3, timeoutMs: Int = 1200): Pulse = withContext(Dispatchers.IO) {
        val host = gatewayIp()?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: "8.8.8.8"
        val timings = mutableListOf<Int>()
        var lost = 0

        repeat(samples) {
            val measured = icmpOnce(host, timeoutMs) ?: tcpOnce("1.1.1.1", 53, timeoutMs)
            if (measured != null) timings += measured else lost++
        }

        val total = samples.coerceAtLeast(1)
        val avg = if (timings.isEmpty()) 0 else timings.average().toInt()
        val jitter = if (timings.size < 2) 0 else (timings.max() - timings.min()).coerceAtLeast(0)
        Pulse(
            latencyMs = avg,
            lossPercent = lost * 100f / total,
            jitterMs = jitter,
            reachable = timings.isNotEmpty()
        )
    }

    private fun icmpOnce(host: String, timeoutMs: Int): Int? = try {
        val process = ProcessBuilder("ping", "-c", "1", "-W", "${timeoutMs / 1000 + 1}", host)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("time=([0-9.]+)").find(output)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
    } catch (t: Throwable) {
        null
    }

    private fun tcpOnce(host: String, port: Int, timeoutMs: Int): Int? = try {
        val start = System.nanoTime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
        }
        ((System.nanoTime() - start) / 1_000_000L).toInt()
    } catch (t: Throwable) {
        null
    }

    /**
     * DNS saturation: fires [count] resolver queries with unique hostnames so
     * the system resolver cache cannot absorb them. Returns how many were
     * actually issued. This is genuinely wasteful on purpose — it is the
     * "soft jam" the user asked for — but it is bounded per burst.
     */
    suspend fun dnsBurst(count: Int, coroutineFriendly: Boolean = true): Int = withContext(Dispatchers.IO) {
        var issued = 0
        val suffixes = listOf("sg-probe.net", "cdn-test.invalid", "analytics-edge.net")
        repeat(count) {
            val host = "${Random.nextInt(0xFFFFF)}.${suffixes[it % suffixes.size]}"
            try {
                InetAddress.getAllByName(host) // failure == cache/insert miss, which is the point
                issued++
            } catch (t: Throwable) {
                issued++
            }
        }
        issued
    }

    /**
     * Uplink hog: pushes small UDP datagrams at the resolver port to consume
     * radio uplink slots. Returns bytes sent. Always closes its socket so the
     * release path cannot leak a live handle.
     */
    suspend fun uplinkBurst(payloadBytes: Int = 512, packets: Int = 4): Long = withContext(Dispatchers.IO) {
        var sent = 0L
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 250
                val target = InetAddress.getByName("1.1.1.1")
                val payload = ByteArray(payloadBytes)
                repeat(packets) {
                    val packet = DatagramPacket(payload, payload.size, target, 53)
                    socket.send(packet)
                    sent += payload.size
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "uplink burst: ${t.message}")
        }
        sent
    }

    // ------------------------------------------------------ geo / environment

    /**
     * Indoor heuristic: a strong Wi-Fi association or a coarse network fix means
     * we are probably under a roof. No maps, no network round-trip.
     */
    fun estimateIndoor(): Boolean = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val rssi = wifi.connectionInfo?.rssi ?: -127
        val wifiGood = wifi.isWifiEnabled && rssi > -72

        val lastKnown = try {
            locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (t: SecurityException) {
            null
        }
        val fixAccurate = lastKnown?.accuracy?.let { it < 25f } == true
        wifiGood && !fixAccurate
    } catch (t: Throwable) {
        false
    }

    fun hasLocationPermission(): Boolean = try {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    companion object {
        private const val TAG = "NetworkMonitor"
    }
}
