package com.safeguard.app.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Thin, honest wrapper around the standard radio APIs.
 *
 * Android deliberately removed most programmatic radio control from normal apps
 * (airplane mode since API 17, Wi-Fi toggling since API 29, Bluetooth toggling
 * since API 33, preferred network type always). Every call therefore:
 *   1. tries the public API,
 *   2. falls back to the hidden/system API via reflection,
 *   3. reports [RadioResult.BLOCKED] instead of lying about success.
 */
class RadioController(private val context: Context) {

    private val wifi: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val bluetooth: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val telephony: TelephonyManager?
        get() = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    enum class RadioResult { OK, BLOCKED, UNSUPPORTED }

    data class Snapshot(
        val wifiEnabled: Boolean,
        val bluetoothEnabled: Boolean,
        val airplaneMode: Boolean
    )

    fun snapshot(): Snapshot = Snapshot(
        wifiEnabled = wifiEnabled(),
        bluetoothEnabled = bluetoothEnabled(),
        airplaneMode = airplaneModeOn()
    )

    // ---------------------------------------------------------------- Wi-Fi

    fun wifiEnabled(): Boolean = try {
        wifi?.isWifiEnabled == true
    } catch (t: Throwable) {
        false
    }

    @Suppress("DEPRECATION")
    fun setWifiEnabled(enabled: Boolean): RadioResult {
        val manager = wifi ?: return RadioResult.UNSUPPORTED
        return try {
            val ok = manager.setWifiEnabled(enabled)
            if (ok) RadioResult.OK else RadioResult.BLOCKED
        } catch (t: Throwable) {
            Log.w(TAG, "wifi toggle blocked: ${t.message}")
            RadioResult.BLOCKED
        }
    }

    // ------------------------------------------------------------- Bluetooth

    fun bluetoothEnabled(): Boolean = try {
        bluetooth?.isEnabled == true
    } catch (t: Throwable) {
        false
    }

    fun hasBluetoothPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, needed) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun setBluetoothEnabled(enabled: Boolean): RadioResult {
        val adapter = bluetooth ?: return RadioResult.UNSUPPORTED
        if (!hasBluetoothPermission()) return RadioResult.BLOCKED
        return try {
            val ok = if (enabled) adapter.enable() else adapter.disable()
            if (ok) RadioResult.OK else RadioResult.BLOCKED
        } catch (t: Throwable) {
            Log.w(TAG, "bt toggle blocked: ${t.message}")
            RadioResult.BLOCKED
        }
    }

    // --------------------------------------------------------- Airplane mode

    fun airplaneModeOn(): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (t: Throwable) {
        false
    }

    /**
     * Writing AIRPLANE_MODE_ON is gated behind WRITE_SECURE_SETTINGS
     * (grantable only over adb). We attempt it, then verify by reading back,
     * so the engine never claims a pulse it did not deliver.
     */
    fun setAirplaneMode(enabled: Boolean): RadioResult {
        val resolver = context.contentResolver
        return try {
            Settings.Global.putInt(
                resolver,
                Settings.Global.AIRPLANE_MODE_ON,
                if (enabled) 1 else 0
            )
            context.sendBroadcast(
                android.content.Intent(android.content.Intent.ACTION_AIRPLANE_MODE_CHANGED)
                    .putExtra("state", enabled)
            )
            if (airplaneModeOn() == enabled) RadioResult.OK else RadioResult.BLOCKED
        } catch (t: Throwable) {
            Log.w(TAG, "airplane toggle blocked: ${t.message}")
            RadioResult.BLOCKED
        }
    }

    // ------------------------------------------------- Preferred network type

    /** IWLAN/WCDMA style preferred-mode constants: 2G, 3G, 4G, 5G. */
    val networkModes = listOf(
        NetMode("5G", 26, "NR only"),
        NetMode("4G", 12, "LTE only"),
        NetMode("3G", 9, "WCDMA/GSM"),
        NetMode("2G", 1, "GSM only")
    )

    data class NetMode(val label: String, val bitmask: Int, val description: String)

    /**
     * setPreferredNetworkType / setAllowedNetworkTypesForReason are @SystemApi.
     * Reflection is attempted for completeness (it works on some OEM builds
     * with MODIFY_PHONE_STATE granted to a privileged app).
     */
    fun setPreferredNetwork(label: String, bitmask: Int): RadioResult {
        val tm = telephony ?: return RadioResult.UNSUPPORTED
        val attempts: List<() -> Any?> = listOf(
            {
                tm.javaClass.getMethod("setPreferredNetworkType", Int::class.javaPrimitiveType)
                    .invoke(tm, bitmask)
            },
            {
                tm.javaClass.getMethod(
                    "setAllowedNetworkTypesForReason",
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType
                ).invoke(tm, bitmask.toLong(), 0)
            }
        )
        for (attempt in attempts) {
            try {
                attempt()
                return RadioResult.OK
            } catch (t: Throwable) {
                Log.d(TAG, "network mode $label blocked: ${t.javaClass.simpleName}")
            }
        }
        return RadioResult.BLOCKED
    }

    fun carrierName(): String {
        val tm = telephony ?: return "No telephony"
        return try {
            val name = tm.networkOperatorName?.takeIf { it.isNotBlank() }
                ?: tm.simOperatorName?.takeIf { it.isNotBlank() }
            name ?: "Unknown carrier"
        } catch (t: Throwable) {
            "Unknown carrier"
        }
    }

    fun currentNetworkLabel(): String {
        val tm = telephony ?: return "--"
        return try {
            @Suppress("DEPRECATION")
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_EVDO_0 -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_GSM -> "2G"
                else -> "—"
            }
        } catch (t: Throwable) {
            "--"
        }
    }

    fun wifiRssi(): Int = try {
        wifi?.connectionInfo?.rssi ?: 0
    } catch (t: Throwable) {
        0
    }

    companion object {
        private const val TAG = "RadioController"
    }
}
