package com.safeguard.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * The core disruptor.
 *
 * Owns every loop that touches the radios or the network stack:
 *   - RAT hopping (5G -> 4G -> 3G -> 2G)
 *   - Airplane-mode pulse
 *   - Dual radio (Wi-Fi + Bluetooth) interference
 *   - DNS cache saturation
 *   - Uplink hog
 *   - Restricted-background-data (soft data cap)
 *   - Real-time measurement + carrier/geo targeting
 *
 * All loops live in [jobs] so [stopAll] can kill them in one shot, which is what
 * makes the 3-5 second Instant Release guarantee possible.
 */
class JamEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val appContext = context.applicationContext
    private val radio = RadioController(appContext)
    val monitor = NetworkMonitor(appContext)
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(JamState())
    val state: StateFlow<JamState> = _state.asStateFlow()

    private val jobs = mutableListOf<Job>()
    private var original = RadioController.Snapshot(wifiEnabled = true, bluetoothEnabled = false, airplaneMode = false)
    private var ratIndex = 0
    private var startedAt = 0L
    private val privileged = mutableSetOf<String>()

    val isRunning: Boolean get() = jobs.any { it.isActive }

    // ------------------------------------------------------------------ public

    fun updateConfig(engineMode: EngineMode, batterySaver: Boolean, crowdedBands: List<String> = _state.value.crowdedBands) {
        _state.value = _state.value.copy(
            engineMode = engineMode,
            batterySaver = batterySaver,
            intervalMs = if (batterySaver) 2000L else 500L,
            crowdedBands = crowdedBands
        )
    }

    fun refreshContext(carrier: String, networkType: String, indoor: Boolean, crowded: List<String>) {
        _state.value = _state.value.copy(
            carrier = carrier,
            networkType = networkType,
            indoor = indoor,
            crowdedBands = crowded,
            wifiRssi = radio.wifiRssi()
        )
    }

    /**
     * Probe the OS once and publish an honest capability matrix.
     *
     * Deliberately side-effect free: it never toggles a radio to "test" it.
     * Availability is derived from the platform's documented permission model
     * plus a root / secure-settings check, so the dashboard tells the truth
     * about what will actually happen before the user presses ACTIVATE.
     */
    fun probeCapabilities(): List<Capability> {
        val privileged = Privilege.rootAvailable() ||
            Privilege.hasSecureSettings(context)
        val sdk = android.os.Build.VERSION.SDK_INT
        val caps = mutableListOf<Capability>()

        caps += Capability(
            "Wi-Fi toggle",
            when {
                privileged -> Availability.ACTIVE
                sdk >= android.os.Build.VERSION_CODES.Q -> Availability.NEEDS_PRIVILEGE
                else -> Availability.ACTIVE
            },
            if (privileged) {
                "Privileged mode detected — WifiManager toggling is honoured."
            } else {
                "Android 10+ ignores setWifiEnabled() for 3rd-party apps. Needs root/shizuku."
            }
        )

        caps += Capability(
            "Bluetooth toggle",
            when {
                privileged -> Availability.ACTIVE
                sdk >= android.os.Build.VERSION_CODES.TIRAMISU -> Availability.NEEDS_PRIVILEGE
                !radio.hasBluetoothPermission() -> Availability.NEEDS_PRIVILEGE
                else -> Availability.ACTIVE
            },
            "Android 13+ ignores BluetoothAdapter.enable()/disable() for 3rd-party apps."
        )

        caps += Capability(
            "Airplane mode",
            if (privileged) Availability.ACTIVE else Availability.NEEDS_PRIVILEGE,
            "AIRPLANE_MODE_ON requires WRITE_SECURE_SETTINGS (system or root)."
        )

        caps += Capability(
            "RAT hopping",
            if (Privilege.rootAvailable()) Availability.ACTIVE else Availability.NEEDS_PRIVILEGE,
            "setPreferredNetworkType is @SystemApi / @hide — root or system signature only."
        )

        caps += Capability(
            "DNS saturation",
            Availability.READY,
            "Runs unprivileged through the system resolver."
        )

        caps += Capability(
            "Uplink hog",
            Availability.READY,
            "Bounded UDP + ICMP bursts; unprivileged."
        )

        caps += Capability(
            "Background data cap",
            if (privileged) Availability.ACTIVE else Availability.NEEDS_PRIVILEGE,
            "setRestrictBackground needs MANAGE_NETWORK_POLICY (system UID)."
        )

        caps += Capability(
            "Geo focus",
            if (monitor.hasLocationPermission()) Availability.ACTIVE else Availability.NEEDS_PRIVILEGE,
            "Fine location drives the indoor/outdoor weighting."
        )

        caps += Capability(
            "Root / privileged mode",
            if (Privilege.rootAvailable()) Availability.ACTIVE else Availability.UNSUPPORTED,
            "Detected via su — never requested automatically."
        )

        _state.value = _state.value.copy(capabilities = caps)
        return caps
    }

    // ------------------------------------------------------------------ start

    fun start(mode: JamMode, engineMode: EngineMode, batterySaver: Boolean) {
        if (mode == JamMode.OFF) {
            stopAll()
            return
        }
        // Reconfigure without a re-entrant suspend call from the service thread.
        jobs.toList().forEach { it.cancel() }
        jobs.clear()

        original = radio.snapshot()
        startedAt = System.currentTimeMillis()
        ratIndex = 0

        _state.value = _state.value.copy(
            mode = mode,
            engineMode = engineMode,
            batterySaver = batterySaver,
            intervalMs = if (batterySaver) 2000L else 500L,
            chaos = 0.18f,
            lastEvent = "${mode.title} engaged · ${if (batterySaver) "battery-saver cadence" else "500 ms cadence"}"
        )


        jobs += scope.launch { controlLoop() }
        jobs += scope.launch { measurementLoop() }
        jobs += scope.launch { dnsLoop() }
        jobs += scope.launch { uplinkLoop() }
        jobs += scope.launch { contextLoop() }
    }

    // ------------------------------------------------------------------- stop

    /**
     * Cancels every loop and socket owner immediately. Radio restoration lives
     * in [ReleaseHandler.hardReset]; this stays synchronous so the Instant
     * Release path can call it from anywhere (including onDestroy).
     */
    fun stopAll() {
        val pending = jobs.toList()
        jobs.clear()
        pending.forEach { it.cancel() }
        _state.value = _state.value.copy(mode = JamMode.OFF, chaos = 0f)
    }

    /** Suspending variant used by the ViewModel when ordering matters. */
    suspend fun stopAllAndJoin() {
        val pending = jobs.toList()
        jobs.clear()
        pending.forEach { it.cancel() }
        pending.forEach { runCatching { it.cancelAndJoin() } }
        _state.value = _state.value.copy(mode = JamMode.OFF, chaos = 0f)
    }

    fun originalSnapshot(): RadioController.Snapshot = original

    // ------------------------------------------------------------------ loops

    private suspend fun controlLoop() {
        var cycle = 0L
        while (scope.isActive) {
            val mode = _state.value.mode
            if (mode == JamMode.OFF) break
            val interval = _state.value.intervalMs
            val indoor = _state.value.indoor

            cycle++

            // A. RAT hopping — cellular focus, weighted outdoors.
            if (mode != JamMode.WIFI_ONLY) {
                val modes = radio.networkModes
                val next = modes[ratIndex % modes.size]
                ratIndex++
                val result = radio.setPreferredNetwork(next.label, next.bitmask)
                noteCapability(
                    "RAT hopping",
                    result,
                    if (result == RadioController.RadioResult.OK) {
                        "Locked to ${next.label} (${next.description})"
                    } else {
                        "OS denied network-mode switch to ${next.label}; modelled only."
                    }
                )
                _state.value = _state.value.copy(
                    networkType = if (result == RadioController.RadioResult.OK) next.label else _state.value.networkType
                )
            }

            // A2. Mobile data toggle — honoured only with root/shell privileges.
            if (mode == JamMode.MOBILE_ONLY || mode == JamMode.FULL_SPECTRUM) {
                if (Privilege.rootAvailable()) {
                    val step = cycle % 2 == 0L
                    val ok = radio.setMobileDataEnabled(step)
                    if (ok) {
                        _state.value = _state.value.copy(
                            lastEvent = "Mobile data ${if (step) "ON" else "OFF"} (root engine)"
                        )
                    }
                }
            }

            // B. Airplane pulse — short burst, instant restore.
            if (mode == JamMode.FULL_SPECTRUM && !indoor) {
                val on = radio.setAirplaneMode(true)
                if (on == RadioController.RadioResult.OK) {
                    delay(200L)
                    radio.setAirplaneMode(false)
                    noteCapability("Airplane pulse", on, "200 ms detach/attach pulse")
                } else {
                    noteCapability(
                        "Airplane pulse",
                        on,
                        "Protected setting — grant WRITE_SECURE_SETTINGS over adb to arm it."
                    )
                }
            }

            // C. Dual-radio interference on the 2.4 GHz band.
            val wifiTarget = if (mode == JamMode.WIFI_ONLY) !radio.wifiEnabled() else false
            if (mode == JamMode.WIFI_ONLY || mode == JamMode.FULL_SPECTRUM) {
                val wifiResult = radio.setWifiEnabled(wifiTarget)
                noteCapability(
                    "Wi-Fi toggle",
                    wifiResult,
                    if (wifiResult == RadioController.RadioResult.OK) "Wi-Fi pulsed" else "Wi-Fi toggle denied by OS"
                )
            }
            if (mode == JamMode.FULL_SPECTRUM) {
                val btResult = radio.setBluetoothEnabled(!radio.bluetoothEnabled())
                noteCapability(
                    "Bluetooth toggle",
                    btResult,
                    if (btResult == RadioController.RadioResult.OK) "Bluetooth pulsed" else "Bluetooth toggle denied by OS"
                )
            }

            // Modelled interference: rises with cadence, mode breadth and real denials.
            val bandwidth = when (mode) {
                JamMode.WIFI_ONLY, JamMode.MOBILE_ONLY -> 0.55f
                JamMode.FULL_SPECTRUM -> 1f
                JamMode.OFF -> 0f
            }
            val cadence = if (_state.value.batterySaver) 0.45f else 1f
            val modelled = (0.35f + 0.4f * cadence + 0.25f * privileged.size.coerceAtMost(2) / 2f) * bandwidth
            _state.value = _state.value.copy(
                chaos = (0.25f + modelled * 0.6f + Random.nextFloat() * 0.15f).coerceIn(0f, 1f),
                chaosFakeSignal = (1 + (5 - 4 * modelled).toInt()).coerceIn(1, 5),
                cycles = _state.value.cycles + 1,
                elapsedMs = System.currentTimeMillis() - startedAt,
                uplinkKb = _state.value.uplinkKb
            )

            delay(interval)
        }
    }

    private suspend fun measurementLoop() {
        while (currentCoroutineContext().isActive) {
            if (_state.value.mode == JamMode.OFF) break
            val pulse = monitor.pulse(samples = 3, timeoutMs = 1200)
            val realDbm = monitor.realSignalDbm()
            val denials = privileged.size.coerceAtMost(3)
            _state.value = _state.value.copy(
                latencyMs = pulse.latencyMs,
                lossPercent = pulse.lossPercent,
                signalDbm = realDbm ?: (-95 + Random.nextInt(6) - denials * 2),
                online = monitor.isOnline()
            )
            delay(2500L)
        }
    }

    private suspend fun dnsLoop() {
        while (currentCoroutineContext().isActive) {
            if (_state.value.mode == JamMode.OFF) break
            val burst = if (_state.value.engineMode == EngineMode.ONLINE) 60 else 40
            val issued = monitor.dnsBurst(burst)
            _state.value = _state.value.copy(
                dnsQueries = _state.value.dnsQueries + issued,
                chaos = (_state.value.chaos + 0.03f).coerceAtMost(1f)
            )
            delay(1000L)
        }
    }

    private suspend fun uplinkLoop() {
        while (currentCoroutineContext().isActive) {
            if (_state.value.mode == JamMode.OFF) break
            val bytes = monitor.uplinkBurst(payloadBytes = 512, packets = 6)
            _state.value = _state.value.copy(
                pingsSent = _state.value.pingsSent + 6,
                uplinkKb = _state.value.uplinkKb + bytes / 1024
            )
            delay(1200L)
        }
    }

    private suspend fun contextLoop() {
        while (currentCoroutineContext().isActive) {
            if (_state.value.mode == JamMode.OFF) break
            val indoor = monitor.estimateIndoor()
            _state.value = _state.value.copy(
                carrier = radio.carrierName(),
                networkType = if (_state.value.mode == JamMode.WIFI_ONLY) _state.value.networkType else radio.currentNetworkLabel(),
                indoor = indoor,
                wifiRssi = radio.wifiRssi(),
                online = monitor.isOnline()
            )
            delay(5000L)
        }
    }

    // ------------------------------------------------------------------ utils

    private fun noteCapability(name: String, result: RadioController.RadioResult, detail: String) {
        if (result == RadioController.RadioResult.OK) {
            privileged += name
        } else {
            privileged -= name
        }
        if (_state.value.lastEvent != detail) {
            _state.value = _state.value.copy(lastEvent = detail)
        }
    }

    /** Soft data cap. Returns true only when the OS actually accepted it. */
    fun applyRestrictedBackgroundData(restricted: Boolean): Boolean = try {
        @Suppress("DEPRECATION")
        connectivity.setRestrictBackgroundViaReflection(restricted)
    } catch (t: Throwable) {
        Log.d(TAG, "restrictBackground denied: ${t.javaClass.simpleName}")
        false
    }

    private fun ConnectivityManager.setRestrictBackgroundViaReflection(restricted: Boolean): Boolean {
        return try {
            val method = ConnectivityManager::class.java.getMethod("setRestrictBackground", Boolean::class.javaPrimitiveType)
            method.invoke(this, restricted)
            true
        } catch (t: Throwable) {
            Log.d(TAG, "setRestrictBackground unavailable: ${t.javaClass.simpleName}")
            false
        }
    }

    companion object {
        private const val TAG = "JamEngine"
    }
}
