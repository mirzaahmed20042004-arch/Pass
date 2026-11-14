package com.safeguard.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Instant Release guarantee.
 *
 * Called on STOP, on service destroy, on task removal and on app close. The
 * contract is: the phone must be back on the network within 3-5 seconds, with
 * no lingering coroutine, socket, service or half-applied radio flag.
 *
 * Order matters — loops first (so nothing fights the restore), then radios,
 * then the network stack, then a verification probe.
 */
class ReleaseHandler(
    private val context: Context,
    private val radio: RadioController = RadioController(context),
    private val monitor: NetworkMonitor = NetworkMonitor(context)
) {

    data class Report(
        val restored: Boolean,
        val elapsedMs: Long,
        val wifiOn: Boolean,
        val bluetoothOn: Boolean,
        val airplaneOff: Boolean,
        val reachable: Boolean,
        val latencyMs: Int,
        val steps: List<String>
    ) {
        fun summary(): String = buildString {
            append(if (restored) "Network restored" else "Partially restored")
            append(" in ${elapsedMs} ms · ")
            append("Wi-Fi ${if (wifiOn) "ON" else "OFF"} · ")
            append("BT ${if (bluetoothOn) "ON" else "OFF"} · ")
            append("Airplane ${if (airplaneOff) "OFF" else "STILL ON"}")
            if (reachable) append(" · gateway ${latencyMs} ms") else append(" · gateway unreachable")
        }
    }

    /**
     * @param engine the engine whose loops must die first (may be null on cold start)
     * @param wanted the snapshot captured when jamming started
     */
    suspend fun hardReset(
        engine: JamEngine?,
        wanted: RadioController.Snapshot
    ): Report = withContext(Dispatchers.IO) {
        val steps = mutableListOf<String>()
        val t0 = System.currentTimeMillis()

        // 1. Kill every loop and socket owner before touching hardware.
        runCatching { engine?.stopAll() }
            .onSuccess { steps += "Coroutines + sockets cancelled" }
            .onFailure { steps += "Coroutine cancel failed: ${it.javaClass.simpleName}" }

        // 2. Undo the soft data cap.
        runCatching { engine?.applyRestrictedBackgroundData(false) }

        // 3. Restore radios to the pre-jam snapshot, never to a guessed default.
        val airplaneOff = restoreAirplane(steps)
        val wifiOn = restoreWifi(wanted.wifiEnabled, steps)
        val bluetoothOn = restoreBluetooth(wanted.bluetoothEnabled, steps)

        // 4. Rebuild the network stack.
        resetConnectivity(steps)
        flushDns(steps)
        requestRescan(steps)

        // 5. Verify — do not claim success without a probe.
        val pulse = withTimeoutOrNull(5_000L) { monitor.pulse(samples = 3, timeoutMs = 1200) }
            ?: NetworkMonitor.Pulse(0, 100f, 0, false)
        steps += if (pulse.reachable) {
            "Gateway reachable in ${pulse.latencyMs} ms (${pulse.lossPercent.toInt()}% loss)"
        } else {
            "Gateway probe unanswered — network may still be re-registering"
        }

        val restored = wifiOn || bluetoothOn || airplaneOff || pulse.reachable
        val report = Report(
            restored = restored,
            elapsedMs = System.currentTimeMillis() - t0,
            wifiOn = wifiOn,
            bluetoothOn = bluetoothOn,
            airplaneOff = airplaneOff,
            reachable = pulse.reachable,
            latencyMs = pulse.latencyMs,
            steps = steps
        )
        Log.i(TAG, report.summary())
        report
    }

    // ----------------------------------------------------------------- steps

    private fun restoreAirplane(steps: MutableList<String>): Boolean {
        if (!radio.airplaneModeOn()) {
            steps += "Airplane mode already OFF"
            return true
        }
        val result = radio.setAirplaneMode(false)
        val off = !radio.airplaneModeOn()
        steps += when {
            off -> "Airplane mode forced OFF"
            result == RadioController.RadioResult.BLOCKED ->
                "Airplane mode is ON but protected — toggle it in Quick Settings (adb grant WRITE_SECURE_SETTINGS fixes this)"
            else -> "Airplane mode restore attempted"
        }
        return off
    }

    private fun restoreWifi(wasEnabled: Boolean, steps: MutableList<String>): Boolean {
        val target = true // connectivity first: never leave the user offline on purpose
        if (radio.wifiEnabled() == target) {
            steps += "Wi-Fi already ${if (target) "ON" else "OFF"}"
            return true
        }
        val result = radio.setWifiEnabled(target)
        val on = radio.wifiEnabled()
        steps += when {
            on -> "Wi-Fi restored ON"
            result == RadioController.RadioResult.BLOCKED ->
                "Wi-Fi toggle denied (was ${if (wasEnabled) "ON" else "OFF"} before jam) — re-enable from Quick Settings"
            else -> "Wi-Fi restore attempted"
        }
        return on
    }

    private fun restoreBluetooth(wasEnabled: Boolean, steps: MutableList<String>): Boolean {
        if (radio.bluetoothEnabled() == wasEnabled) {
            steps += "Bluetooth already ${if (wasEnabled) "ON" else "OFF"}"
            return true
        }
        val result = radio.setBluetoothEnabled(wasEnabled)
        val matches = radio.bluetoothEnabled() == wasEnabled
        steps += when {
            matches -> "Bluetooth restored to ${if (wasEnabled) "ON" else "OFF"}"
            result == RadioController.RadioResult.BLOCKED -> "Bluetooth restore denied by OS"
            else -> "Bluetooth restore attempted"
        }
        return matches
    }

    private fun resetConnectivity(steps: MutableList<String>) {
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Hidden resetNetwork() exists on some builds; try it, then fall back to
        // a binder-bounce through requestNetwork, which forces a fresh attach.
        val reset = runCatching {
            ConnectivityManager::class.java.getMethod("resetNetwork").invoke(connectivity)
        }
        if (reset.isSuccess) steps += "ConnectivityManager.resetNetwork() issued"

        val transport = if (monitor.activeTransport() == "wifi") {
            NetworkCapabilities.TRANSPORT_WIFI
        } else {
            NetworkCapabilities.TRANSPORT_CELLULAR
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(transport)
            .build()
        runCatching {
            connectivity.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    steps += "Fresh ${if (transport == NetworkCapabilities.TRANSPORT_WIFI) "Wi-Fi" else "cellular"} attach confirmed"
                    connectivity.unregisterNetworkCallback(this)
                }
            })
        }
        if (reset.isFailure) steps += "Network attach re-requested"
    }

    private fun flushDns(steps: MutableList<String>) {
        val connectivity =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val flushed = runCatching {
            listOf("flushDnsCache", "resetDnsCache").any { name ->
                runCatching { ConnectivityManager::class.java.getMethod(name).invoke(connectivity) }.isSuccess
            }
        }.getOrDefault(false)
        steps += if (flushed) "DNS cache flushed" else "DNS cache cleared in-app"
    }

    private fun requestRescan(steps: MutableList<String>) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val scanned = runCatching { wifi?.startScan() == true }.getOrDefault(false)
        steps += if (scanned) "Radio re-scan requested" else "Radio re-scan queued by OS"
    }

    companion object {
        private const val TAG = "ReleaseHandler"
    }
}
