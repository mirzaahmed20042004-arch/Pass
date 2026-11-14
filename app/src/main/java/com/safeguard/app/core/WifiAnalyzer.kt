package com.safeguard.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Wi-Fi channel analyser built on the public scan-results API.
 *
 * Tells you which 2.4 GHz channel is crowded in your area and which one to pick
 * in the router — the legal way to make your own Wi-Fi faster instead of trying
 * to interfere with anyone else's.
 */
class WifiAnalyzer(private val context: Context) {

    data class Network(val ssid: String, val channel: Int, val rssi: Int, val band: String, val secure: Boolean)

    data class Report(
        val networks: List<Network>,
        val channelLoad: Map<Int, Int>,
        val recommended2g: Int,
        val sameChannelAsMine: Int,
        val ok: Boolean,
        val message: String
    )

    @SuppressLint("MissingPermission")
    suspend fun analyse(): Report = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext emptyReport("No Wi-Fi hardware on this device")

        runCatching { wifi.startScan() }
        delay(1500) // give the firmware a moment to publish fresh results

        val results = runCatching { wifi.scanResults }.getOrElse {
            return@withContext emptyReport("Scan blocked — grant Location permission and turn Wi-Fi on")
        }
        if (results.isEmpty()) {
            return@withContext emptyReport("No networks visible yet — turn Wi-Fi on and tap again")
        }

        val parsed = results.mapNotNull { r ->
            val freq = r.frequency
            val channel = when {
                freq in 2400..2500 -> (freq - 2407) / 5
                freq in 4900..5900 -> (freq - 5000) / 5
                else -> 0
            }
            if (channel <= 0) null else Network(
                ssid = r.SSID?.takeIf { it.isNotBlank() } ?: "(hidden)",
                channel = channel,
                rssi = r.level,
                band = if (freq < 2500) "2.4 GHz" else "5 GHz",
                secure = r.capabilities?.contains("WPA") == true
            )
        }.sortedByDescending { it.rssi }

        val twoG = parsed.filter { it.band == "2.4 GHz" }
        val load = twoG.groupingBy { it.channel }.eachCount()
        // Classic non-overlapping channels: 1, 6, 11
        val best = listOf(1, 6, 11).minByOrNull { ch ->
            // overlapping neighbours count as half a network
            (load[ch] ?: 0) * 2 + (load[ch - 1] ?: 0) + (load[ch + 1] ?: 0)
        } ?: 1

        val myChannel = twoG.maxByOrNull { it.rssi }?.channel ?: 0
        val sameAsMine = twoG.count { it.channel == myChannel }

        Report(
            networks = parsed,
            channelLoad = load,
            recommended2g = best,
            sameChannelAsMine = sameAsMine,
            ok = true,
            message = "${parsed.size} networks visible · ${twoG.size} on 2.4 GHz"
        )
    }

    private fun emptyReport(message: String) = Report(
        networks = emptyList(),
        channelLoad = emptyMap(),
        recommended2g = 1,
        sameChannelAsMine = 0,
        ok = false,
        message = message
    )
}
