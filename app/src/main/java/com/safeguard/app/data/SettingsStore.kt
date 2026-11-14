package com.safeguard.app.data

import android.content.Context
import com.safeguard.app.core.EngineMode

/** Tiny SharedPreferences façade: survives process death for the release audit. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("safeguard", Context.MODE_PRIVATE)

    var engineMode: EngineMode
        get() = if (prefs.getString(KEY_MODE, "OFFLINE") == "ONLINE") EngineMode.ONLINE else EngineMode.OFFLINE
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var batterySaver: Boolean
        get() = prefs.getBoolean(KEY_BATTERY, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY, value).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY, value).apply()

    /** Set when a jam session starts; read on boot to undo a stuck radio state. */
    var sessionActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVE, value).apply()

    var snapshotWifi: Boolean
        get() = prefs.getBoolean(KEY_WIFI, true)
        set(value) = prefs.edit().putBoolean(KEY_WIFI, value).apply()

    var snapshotBt: Boolean
        get() = prefs.getBoolean(KEY_BT, false)
        set(value) = prefs.edit().putBoolean(KEY_BT, value).apply()

    var lastReleaseSummary: String
        get() = prefs.getString(KEY_LAST_RELEASE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_RELEASE, value).apply()

    var releaseCount: Int
        get() = prefs.getInt(KEY_RELEASES, 0)
        set(value) = prefs.edit().putInt(KEY_RELEASES, value).apply()

    /** Last time the periodic worker confirmed a live connection. */
    var lastHeartbeat: Long
        get() = prefs.getLong(KEY_HEARTBEAT, 0L)
        set(value) = prefs.edit().putLong(KEY_HEARTBEAT, value).apply()

    private companion object {
        const val KEY_MODE = "engine_mode"
        const val KEY_BATTERY = "battery_saver"
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_ACTIVE = "session_active"
        const val KEY_WIFI = "snapshot_wifi"
        const val KEY_BT = "snapshot_bt"
        const val KEY_LAST_RELEASE = "last_release"
        const val KEY_RELEASES = "release_count"
        const val KEY_HEARTBEAT = "heartbeat"
    }
}
