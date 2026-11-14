package com.safeguard.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Detects whether this install has the privileges that Android requires before
 * any radio can actually be switched off from an app.
 *
 * There are exactly three ways to get there on a stock phone, and the app never
 * asks for any of them silently:
 *
 *  1. **Root** — `su` available; the engine then runs its commands through it.
 *  2. **WRITE_SECURE_SETTINGS** — granted manually once over adb:
 *     `adb shell pm grant com.safeguard.app android.permission.WRITE_SECURE_SETTINGS`
 *  3. **Shell/Shizuku** — `svc wifi/bt/data` executed as the shell UID.
 *
 * Without one of these, the OS silently drops every radio-state write. That is
 * a platform guarantee (Android 10 for Wi-Fi, 13 for Bluetooth, always for
 * airplane mode) and no APK can bypass it — not even a "full" or "pro" one.
 */
object Privilege {

    private const val TAG = "Privilege"

    @Volatile
    private var rootCache: Boolean? = null

    fun hasSecureSettings(context: Context): Boolean = try {
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun canWriteGlobal(context: Context): Boolean {
        if (hasSecureSettings(context)) return true
        return try {
            val before = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
            Settings.Global.putInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, before)
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == before &&
                rootAvailable()
        } catch (t: SecurityException) {
            false
        } catch (t: Throwable) {
            false
        }
    }

    /** Runs `id` through su once and caches the answer. */
    fun rootAvailable(): Boolean {
        rootCache?.let { return it }
        val result = try {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor()
            finished == 0 && output.contains("uid=0")
        } catch (t: Throwable) {
            Log.d(TAG, "su not available: ${t.javaClass.simpleName}")
            false
        }
        rootCache = result
        return result
    }

    /**
     * Executes a shell snippet, escalating through su when root is present.
     * Returns true only when the command exits 0.
     */
    fun shell(command: String): Boolean = try {
        val (bin, args) = if (rootAvailable()) {
            "su" to arrayOf("-c", command)
        } else {
            "sh" to arrayOf("-c", command)
        }
        val process = ProcessBuilder(bin, *args).redirectErrorStream(true).start()
        val ok = process.waitFor() == 0
        if (ok) Log.i(TAG, "shell ok: $command") else Log.w(TAG, "shell failed: $command")
        ok
    } catch (t: Throwable) {
        Log.w(TAG, "shell error: ${t.message}")
        false
    }

    /** Human-readable summary for the dashboard. */
    fun describe(context: Context): String = when {
        rootAvailable() -> "ROOT — full radio control"
        hasSecureSettings(context) -> "ADB GRANT — secure settings writable"
        else -> "STANDARD — radio writes blocked by Android"
    }
}
