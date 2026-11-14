package com.safeguard.app.core

/** What the user is currently stressing. */
enum class JamMode {
    OFF,
    WIFI_ONLY,
    MOBILE_ONLY,
    FULL_SPECTRUM;

    val title: String
        get() = when (this) {
            OFF -> "Standby"
            WIFI_ONLY -> "Wi-Fi Only"
            MOBILE_ONLY -> "Mobile Data Only"
            FULL_SPECTRUM -> "Full Spectrum"
        }
}

/** Offline = everything on-device. Online = cloud spectrum hints + telemetry sync. */
enum class EngineMode { OFFLINE, ONLINE }

/**
 * How much of a capability this build actually got from the OS.
 * Nothing here is faked: blocked calls are reported honestly instead of
 * pretending the radio changed.
 */
enum class Availability {
    ACTIVE,
    READY,
    NEEDS_PRIVILEGE,
    UNSUPPORTED
}

data class Capability(
    val name: String,
    val availability: Availability,
    val detail: String
)

data class JamState(
    val mode: JamMode = JamMode.OFF,
    val engineMode: EngineMode = EngineMode.OFFLINE,
    val batterySaver: Boolean = false,
    val intervalMs: Long = 500L,

    // live gauges
    val signalDbm: Int = -75,
    val latencyMs: Int = 0,
    val lossPercent: Float = 0f,
    val chaos: Float = 0f,
    val chaosFakeSignal: Int = 4,

    // counters
    val cycles: Long = 0L,
    val pingsSent: Long = 0L,
    val dnsQueries: Long = 0L,
    val uplinkKb: Long = 0L,
    val elapsedMs: Long = 0L,

    // context
    val carrier: String = "Detecting…",
    val networkType: String = "--",
    val wifiRssi: Int = 0,
    val indoor: Boolean = false,
    val crowdedBands: List<String> = emptyList(),

    val capabilities: List<Capability> = emptyList(),
    val lastEvent: String = "Engine idle. No radio changes made.",
    val lastReleaseMs: Long = 0L,
    val online: Boolean = false,
    val backendSynced: Int = 0
) {
    val running: Boolean get() = mode != JamMode.OFF
    /** 0..1 scalar used by the UI for glow intensity. */
    val intensity: Float get() = if (running) 1f else 0f
}
