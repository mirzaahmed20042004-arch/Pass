package com.safeguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeguard.app.MainActivity
import com.safeguard.app.R
import com.safeguard.app.core.ShieldMode
import java.io.FileInputStream
import java.io.IOException

/**
 * Non-root data-plane shield.
 *
 * This is the strongest thing a normal (unrooted) app is actually allowed to do
 * to connectivity: bring up a local VPN tunnel with no upstream and route the
 * device's traffic into it. Because nothing forwards those packets, every app's
 * flow stalls and times out exactly as if the radio link were gone — and it is
 * reverted in well under a second. It needs one user consent (the standard
 * "Allow VPN connection?" dialog) and no root, on Android 8 through 15.
 *
 * What it does NOT do: it cannot touch other phones, other people's traffic or
 * the cellular/Wi-Fi hardware. Only this device's own traffic, by design.
 */
class ShieldVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var pump: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = runCatching {
                    ShieldMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: ShieldMode.FULL.name)
                }.getOrDefault(ShieldMode.FULL)
                startShield(mode)
            }
            ACTION_STOP -> stopShield()
            else -> if (active == ShieldMode.OFF) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startShield(mode: ShieldMode) {
        if (mode == ShieldMode.OFF) {
            stopShield()
            return
        }
        teardownTunnel()

        val builder = Builder()
            .setSession("SafeGuard Shield")
            .setMtu(1500)

        // Keep our own app outside the tunnel so the dashboard keeps measuring
        // the real network while every other app is cut off.
        runCatching { builder.addDisallowedApplication(packageName) }

        when (mode) {
            ShieldMode.FULL -> {
                builder.addAddress("10.111.0.1", 32)
                builder.addRoute("0.0.0.0", 0)
                builder.addDnsServer("10.111.0.2")
            }
            ShieldMode.DNS -> {
                builder.addAddress("10.111.0.1", 32)
                builder.addRoute("10.111.0.2", 32)
                builder.addDnsServer("10.111.0.2")
            }
            ShieldMode.OFF -> return
        }

        val descriptor = runCatching { builder.establish() }.getOrNull()
        if (descriptor == null) {
            Log.w(TAG, "VPN tunnel refused by the OS — consent not granted")
            stopSelf()
            return
        }

        tun = descriptor
        active = mode
        startForegroundSafely(mode)
        startPump(descriptor)
        Log.i(TAG, "shield up: $mode")
    }

    private fun startPump(descriptor: ParcelFileDescriptor) {
        pump = Thread({
            val input = runCatching { FileInputStream(descriptor.fileDescriptor) }.getOrNull()
                ?: return@Thread
            val buffer = ByteArray(32_767)
            try {
                while (active != ShieldMode.OFF) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    // Deliberate blackhole: packets are drained and dropped, so
                    // every flow times out like a dead radio link.
                    if (mode() == ShieldMode.DNS) {
                        // DNS-only mode: let the resolver query die too, but the
                        // rest of the traffic never entered this tunnel anyway.
                        continue
                    }
                }
            } catch (t: IOException) {
                Log.d(TAG, "pump closed: ${t.message}")
            } catch (t: Throwable) {
                Log.d(TAG, "pump stopped: ${t.javaClass.simpleName}")
            }
        }, "sg-shield-pump").apply {
            isDaemon = true
            start()
        }
    }

    private fun mode(): ShieldMode = active

    private fun startForegroundSafely(mode: ShieldMode) {
        val notification = buildNotification(mode)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }
    }

    private fun buildNotification(mode: ShieldMode): Notification {
        val open = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 11,
            Intent(this, ShieldVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when (mode) {
            ShieldMode.FULL -> "DATA BLACKOUT — all app traffic is being dropped"
            ShieldMode.DNS -> "DNS blackout — name resolution is failing"
            ShieldMode.OFF -> "Shield idle"
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Safe Guard · Data Shield")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nTap STOP to restore instantly."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_shield, "STOP & RESTORE", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Data Shield",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    private fun stopShield() {
        active = ShieldMode.OFF
        teardownTunnel()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }
        stopSelf()
    }

    private fun teardownTunnel() {
        active = ShieldMode.OFF
        runCatching { pump?.interrupt() }
        pump = null
        runCatching { tun?.close() }
        tun = null
    }

    override fun onRevoke() {
        // The OS or the user pulled the VPN: drop everything, never hang.
        Log.i(TAG, "VPN revoked by system")
        stopShield()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownTunnel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShieldVpnService"
        const val CHANNEL = "safeguard_shield"
        const val NOTIF_ID = 4402
        const val ACTION_START = "com.safeguard.app.SHIELD_START"
        const val ACTION_STOP = "com.safeguard.app.SHIELD_STOP"
        const val EXTRA_MODE = "shield_mode"

        @Volatile
        var active: ShieldMode = ShieldMode.OFF
            private set
    }
}

/**
 * Start/stop/detect helper around [ShieldVpnService] and `VpnService.prepare()`.
 * Consent is a one-time system dialog; after that the shield is a single tap.
 */
object ShieldGateway {

    /** Returns the consent intent when the user has not approved VPN yet. */
    fun consentIntent(context: Context): Intent? = runCatching {
        VpnService.prepare(context)
    }.getOrNull()

    fun isActive(): Boolean = ShieldVpnService.active != ShieldMode.OFF

    fun start(context: Context, mode: ShieldMode) {
        val intent = Intent(context, ShieldVpnService::class.java)
            .setAction(ShieldVpnService.ACTION_START)
            .putExtra(ShieldVpnService.EXTRA_MODE, mode.name)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        ShieldVpnService.active = mode
    }

    /**
     * Guaranteed teardown used by the release path: stopService() closes the
     * tun immediately even if the process is about to die.
     */
    fun stop(context: Context) {
        ShieldVpnService.active = ShieldMode.OFF
        runCatching {
            context.startService(
                Intent(context, ShieldVpnService::class.java)
                    .setAction(ShieldVpnService.ACTION_STOP)
            )
        }
        runCatching {
            context.stopService(Intent(context, ShieldVpnService::class.java))
        }
    }
}
