package com.safeguard.app

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeguard.app.core.EngineMode
import com.safeguard.app.core.JamMode
import com.safeguard.app.core.JamState
import com.safeguard.app.core.NetworkMonitor
import com.safeguard.app.core.PermissionPlan
import com.safeguard.app.core.RadioController
import com.safeguard.app.core.ReleaseHandler
import com.safeguard.app.data.CloudSpectrumClient
import com.safeguard.app.data.SettingsStore
import com.safeguard.app.service.JamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MVVM state owner for the single screen.
 *
 * It never touches the radio directly: commands go to [JamService], state comes
 * back as a [StateFlow]. That keeps the engine alive across configuration
 * changes and makes the Instant Release path deterministic.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val release = ReleaseHandler(app)
    private val radio = RadioController(app)
    private val monitor = NetworkMonitor(app)
    private val spectrum = CloudSpectrumClient()

    private val local = MutableStateFlow(
        LocalUi(
            engineMode = store.engineMode,
            batterySaver = store.batterySaver,
            overlayEnabled = store.overlayEnabled,
            overlayGranted = canDrawOverlay(),
            lastRelease = store.lastReleaseSummary,
            releaseCount = store.releaseCount
        )
    )

    data class LocalUi(
        val engineMode: EngineMode,
        val batterySaver: Boolean,
        val overlayEnabled: Boolean,
        val overlayGranted: Boolean,
        val lastRelease: String,
        val releaseCount: Int,
        val releasing: Boolean = false,
        val spectrumSource: String = "On-device model",
        val towers: Int = 0,
        val permissionPrompt: String? = null
    )

    /** Live engine state, or a locally probed snapshot when the service is down. */
    private val engineState: StateFlow<JamState?> =
        com.safeguard.app.service.ServiceBridge.state

    val ui: StateFlow<UiState> = combine(local, engineState) { l, e ->
        UiState(
            jam = e ?: idleSnapshot(),
            local = l
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState(idleSnapshot(), local.value))

    data class UiState(val jam: JamState, val local: LocalUi)

    private var idleCache: JamState? = null
    private fun idleSnapshot(): JamState =
        idleCache ?: JamState(
            carrier = radio.carrierName(),
            networkType = radio.currentNetworkLabel(),
            signalDbm = monitor.realSignalDbm() ?: -75,
            wifiRssi = radio.wifiRssi(),
            indoor = monitor.estimateIndoor(),
            online = monitor.isOnline(),
            capabilities = emptyList(),
            lastEvent = store.lastReleaseSummary.ifBlank { "Engine idle. No radio changes made." }
        ).also { idleCache = it }

    init {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                idleCache = null
                if (engineState.value == null || engineState.value?.running != true) {
                    // keep the dashboard alive while the service is not attached
                    delay(4000)
                } else {
                    delay(6000)
                }
            }
        }
        if (local.value.engineMode == EngineMode.ONLINE) refreshSpectrum()
    }

    // ------------------------------------------------------------- commands

    fun engage(mode: JamMode) {
        if (mode == JamMode.OFF) {
            disengage()
            return
        }
        JamService.start(
            getApplication(),
            mode,
            local.value.engineMode,
            local.value.batterySaver
        )
    }

    fun disengage() {
        if (local.value.releasing) return
        local.value = local.value.copy(releasing = true)
        viewModelScope.launch {
            val service = JamService.instance
            if (service?.engineRef?.isRunning == true) {
                JamService.stop(getApplication())
                // Give the service a grace window, then guarantee the restore.
                repeat(12) {
                    delay(250)
                    if (JamService.instance == null) return@repeat
                }
            }
            val report = withContext(Dispatchers.IO) {
                release.hardReset(service?.engineRef, radio.snapshot().copy(
                    wifiEnabled = store.snapshotWifi,
                    bluetoothEnabled = store.snapshotBt
                ))
            }
            store.lastReleaseSummary = report.summary()
            store.releaseCount = store.releaseCount + 1
            local.value = local.value.copy(
                releasing = false,
                lastRelease = report.summary(),
                releaseCount = store.releaseCount
            )
        }
    }

    fun setEngineMode(mode: EngineMode) {
        store.engineMode = mode
        local.value = local.value.copy(engineMode = mode)
        if (mode == EngineMode.ONLINE) refreshSpectrum()
    }

    fun setBatterySaver(enabled: Boolean) {
        store.batterySaver = enabled
        local.value = local.value.copy(batterySaver = enabled)
        if (JamService.instance?.engineRef?.isRunning == true) {
            JamService.start(
                getApplication(),
                engineState.value?.mode ?: JamMode.FULL_SPECTRUM,
                local.value.engineMode,
                enabled
            )
        }
    }

    fun setOverlay(enabled: Boolean) {
        if (enabled && !canDrawOverlay()) {
            local.value = local.value.copy(permissionPrompt = OVERLAY_PROMPT)
            return
        }
        store.overlayEnabled = enabled
        local.value = local.value.copy(overlayEnabled = enabled, overlayGranted = canDrawOverlay())
    }

    fun refreshSpectrum() {
        viewModelScope.launch {
            val report = spectrum.fetch(mcc = "410", mnc = "01", lat = 24.86, lon = 67.0)
            local.value = local.value.copy(
                spectrumSource = report.source,
                towers = report.towersNearby
            )
            JamService.instance?.engineRef?.refreshContext(
                carrier = radio.carrierName(),
                networkType = radio.currentNetworkLabel(),
                indoor = monitor.estimateIndoor(),
                crowded = report.crowdedBands
            )
        }
    }

    fun requestPermissions(): List<String> = PermissionPlan.required(android.os.Build.VERSION.SDK_INT)

    fun onPermissionsResolved() {
        local.value = local.value.copy(
            overlayGranted = canDrawOverlay(),
            permissionPrompt = null
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { JamService.instance?.engineRef?.probeCapabilities() }
        }
    }

    fun dismissPrompt() {
        local.value = local.value.copy(permissionPrompt = null)
    }

    fun overlaySettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${getApplication<Application>().packageName}"))

    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(getApplication())

    companion object {
        const val OVERLAY_PROMPT = "overlay"
    }
}
