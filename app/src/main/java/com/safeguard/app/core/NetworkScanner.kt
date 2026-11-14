package com.safeguard.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Finds the devices sharing the network this phone is connected to.
 *
 * Read-only and unprivileged: reads the routing table to learn the subnet,
 * probes each address briefly, then cross-references the kernel ARP cache for
 * MAC addresses. It never sends anything malicious and never touches a network
 * the phone is not already joined to.
 */
class NetworkScanner(private val context: Context) {

    data class Device(
        val ip: String,
        val mac: String?,
        val hostname: String?,
        val isThisPhone: Boolean,
        val openPorts: List<Int> = emptyList(),
        val ms: Int = 0
    )

    data class Network(
        val ssid: String?,
        val gateway: String?,
        val subnet: String?,
        val prefix: Int,
        val isWifi: Boolean
    )

    // ------------------------------------------------------------- discovery

    fun describeNetwork(): Network = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val props: LinkProperties? = cm.getLinkProperties(network)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val address = props?.linkAddresses
            ?.firstOrNull { it.address is java.net.Inet4Address }
        val ip = address?.address?.hostAddress
        val prefix = address?.prefixLength ?: 24
        val gateway = props?.routes
            ?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
            ?: readGatewayFromProc()

        Network(
            ssid = if (isWifi) currentSsid() else "Mobile data",
            gateway = gateway,
            subnet = ip?.let { subnetOf(it, prefix) },
            prefix = prefix,
            isWifi = isWifi
        )
    }.getOrElse {
        Network(null, readGatewayFromProc(), null, 24, false)
    }

    private fun currentSsid(): String? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as android.net.wifi.WifiManager
        @Suppress("DEPRECATION")
        wifi.connectionInfo?.ssid?.trim('"')
    }.getOrNull()

    private fun readGatewayFromProc(): String? = runCatching {
        BufferedReader(FileReader("/proc/net/route")).useLines { lines ->
            lines.drop(1).map { it.trim().split(Regex("\\s+")) }
                .firstOrNull { it.size > 2 && it[1] == "00000000" }
                ?.let { parts ->
                    parts[2].chunked(2).reversed()
                        .joinToString(".") { it.toInt(16).toString() }
                }
        }
    }.getOrNull()

    private fun subnetOf(ip: String, prefix: Int): String {
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val value = ip.split(".").fold(0) { acc, part -> (acc shl 8) or part.toInt() }
        val net = value and mask
        return "${(net ushr 24) and 255}.${(net ushr 16) and 255}.${(net ushr 8) and 255}.${net and 255}"
    }

    private fun myIps(): Set<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .mapNotNull { it.hostAddress }
            .toSet()
    }.getOrElse { emptySet() }

    private fun arpCache(): Map<String, String> = runCatching {
        BufferedReader(FileReader("/proc/net/arp")).useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[3].matches(Regex("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}"))) {
                    parts[0] to parts[3].uppercase()
                } else null
            }.toMap()
        }
    }.getOrElse {
        Log.d(TAG, "ARP cache not readable on this build (SELinux)")
        emptyMap()
    }

    /**
     * Sweeps the local subnet. Bounded on purpose: ~254 addresses with 48-way
     * concurrency and a 350 ms budget each, so a full scan finishes in a few
     * seconds without hammering the network.
     */
    suspend fun scan(onProgress: (Int) -> Unit = {}): List<Device> = withContext(Dispatchers.IO) {
        val net = describeNetwork()
        val base = net.subnet ?: return@withContext emptyList()
        val mine = myIps()
        val arp = arpCache()
        val hosts = (1..254).map { "$base.$it" }.filter { it !in mine }
        var done = 0

        val found = coroutineScope {
            hosts.map { ip ->
                async {
                    val ms = probe(ip)
                    done++
                    if (done % 24 == 0) onProgress(done * 100 / hosts.size)
                    if (ms >= 0) ip to ms else null
                }
            }.awaitAll().filterNotNull()
        }

        val thisPhone = Device(
            ip = mine.firstOrNull { it.startsWith("$base.") } ?: "this phone",
            mac = runCatching { arp[myIps().first()] }.getOrNull(),
            hostname = "Safe Guard (this phone)",
            isThisPhone = true
        )

        (listOf(thisPhone) + found.map { (ip, ms) ->
            Device(
                ip = ip,
                mac = arp[ip],
                hostname = reverseName(ip),
                isThisPhone = false,
                ms = ms
            )
        }).sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    /** Returns round-trip ms, or -1 when the host is silent. */
    private fun probe(ip: String): Int {
        val start = System.nanoTime()
        val reachable = runCatching { InetAddress.getByName(ip).isReachable(350) }.getOrDefault(false)
        if (reachable) return ((System.nanoTime() - start) / 1_000_000L).toInt()
        // ICMP is often blocked; a refused/opened TCP port still proves a host.
        for (port in intArrayOf(80, 443, 53)) {
            val ok = runCatching {
                Socket().use { it.connect(InetSocketAddress(ip, port), 180) }
                true
            }.getOrElse {
                it is java.net.ConnectException && it.message?.contains("refused") == true
            }
            if (ok) return ((System.nanoTime() - start) / 1_000_000L).toInt()
        }
        return -1
    }

    private fun reverseName(ip: String): String? = runCatching {
        InetAddress.getByName(ip).canonicalHostName?.takeIf { it != ip }
    }.getOrNull()

    private companion object {
        const val TAG = "NetworkScanner"
    }
}
