package com.safeguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.safeguard.app.MainActivity
import com.safeguard.app.R
import com.safeguard.app.core.EngineMode
import com.safeguard.app.core.JamEngine
import com.safeguard.app.core.JamMode
import com.safeguard.app.core.JamState
import com.safeguard.app.core.ReleaseHandler
import com.safeguard.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the [JamEngine] so the loops survive the UI
 * being backgrounded or swiped away. Single source of truth for the whole app:
 * the ViewModel only observes [state] and sends commands.
 */
class JamService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var engine: JamEngine
    private lateinit var release: ReleaseHandler
    private lateinit var store: SettingsStore
    private lateinit var overlay: OverlayBadge
    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    val state: StateFlow<JamState>? get() = if (::engine.isInitialized) engine.state else null
    val monitor get() = engine.monitor
    val engineRef: JamEngine? get() = if (::engine.isInitialized) engine else null

    override fun onCreate() {
        super.onCreate()
        instance = this
        engine = JamEngine(this, serviceScope)
        release = ReleaseHandler(this)
        store = SettingsStore(this)
        overlay = OverlayBadge(this)
        engine.updateConfig(store.engineMode, store.batterySaver)
        engine.probeCapabilities()
        ServiceBridge.bind(serviceScope, engine)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val mode = runCatching {
                    JamMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: JamMode.FULL_SPECTRUM.name)
                }.getOrDefault(JamMode.FULL_SPECTRUM)
                val engineMode = runCatching {
                    EngineMode.valueOf(intent.getStringExtra(EXTRA_ENGINE) ?: EngineMode.OFFLINE.name)
                }.getOrDefault(EngineMode.OFFLINE)
                val saver = intent.getBooleanExtra(EXTRA_SAVER, false)
                startJam(mode, engineMode, saver)
            }
            ACTION_STOP -> stopJam("Stopped from notification")
            ACTION_RELEASE_ONLY -> stopJam("Release requested")
            else -> {
                // Restarted by the OS: only resume if a session was flagged active.
                if (store.sessionActive && !started) {
                    startJam(JamMode.FULL_SPECTRUM, store.engineMode, store.batterySaver)
                }
            }
        }
        return START_STICKY
    }

    private fun startJam(mode: JamMode, engineMode: EngineMode, saver: Boolean) {
        started = true
        store.sessionActive = true
        store.engineMode = engineMode
        store.batterySaver = saver
        val snapshot = engine.let { it.originalSnapshot() }
        store.snapshotWifi = snapshot.wifiEnabled
        store.snapshotBt = snapshot.bluetoothEnabled

        engine.updateConfig(engineMode, saver)
        engine.start(mode, engineMode, saver)
        acquireWakeLock()
        if (store.overlayEnabled) overlay.show("${mode.title} · jamming")
        startForegroundWithNotification(mode)

        if (!notificationCollectorAttached) {
            notificationCollectorAttached = true
            serviceScope.launch {
                engine.state.collect { publishNotification(it) }
            }
        }
    }

    private fun stopJam(reason: String) {
        if (!started) {
            stopSelfSafely()
            return
        }
        started = false
        store.sessionActive = false
        overlay.hide()
        acquireReleaseWakeLock()
        serviceScope.launch {
            val report = release.hardReset(engine, engine.originalSnapshot())
            store.lastReleaseSummary = report.summary()
            store.releaseCount = store.releaseCount + 1
            Log.i(TAG, "$reason → ${report.summary()}")
            releaseWakeLock()
            stopSelfSafely()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must never leave radios flipped.
        if (started) stopJam("Task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (started) {
            store.sessionActive = false
            overlay.hide()
            // Best-effort synchronous restore; the process may die right after.
            runCatching {
                kotlinx.coroutines.runBlocking {
                    release.hardReset(engine, store.snapshotWifi.let {
                        com.safeguard.app.core.RadioController.Snapshot(
                            wifiEnabled = it,
                            bluetoothEnabled = store.snapshotBt,
                            airplaneMode = false
                        )
                    })
                }
            }
        }
        overlay.hide()
        releaseWakeLock()
        ServiceBridge.unbind()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------- notification

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.channel_service_name), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    description = getString(R.string.service_description)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, getString(R.string.channel_alerts_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun buildNotification(state: JamState): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, JamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (state.running) {
            "${state.mode.title} · ${state.intervalMs} ms cadence · ${state.cycles} cycles · " +
                "loss ${state.lossPercent.toInt()}% · ${state.latencyMs} ms"
        } else {
            "Engine idle"
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle("Safe Guard · ${if (state.running) "ACTIVE" else "IDLE"}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stat_shield, "STOP & RESTORE", stop)
            .build()
    }

    private var lastNotify = 0L
    private var notificationCollectorAttached = false
    private fun publishNotification(state: JamState) {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 1500) return
        lastNotify = now
        if (started) {
            runCatching {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIF_ID, buildNotification(state))
            }
            overlay.update("${state.mode.title} · ${state.cycles} cycles")
        }
    }

    private fun startForegroundWithNotification(mode: JamMode) {
        val notification = buildNotification(engine.state.value.copy(mode = mode))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopSelfSafely() {
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

    // --------------------------------------------------------------- wake locks

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeGuard::JamEngine").apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }
    }

    private fun acquireReleaseWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeGuard::Release").apply {
            setReferenceCounted(false)
            acquire(20_000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "JamService"
        const val CHANNEL = "safeguard_engine"
        const val CHANNEL_ALERTS = "safeguard_alerts"
        const val NOTIF_ID = 4401

        const val ACTION_START = "com.safeguard.app.START"
        const val ACTION_STOP = "com.safeguard.app.STOP"
        const val ACTION_RELEASE_ONLY = "com.safeguard.app.RELEASE"
        const val EXTRA_MODE = "mode"
        const val EXTRA_ENGINE = "engine"
        const val EXTRA_SAVER = "saver"

        @Volatile
        var instance: JamService? = null
            private set

        fun start(context: Context, mode: JamMode, engineMode: EngineMode, batterySaver: Boolean) {
            val intent = Intent(context, JamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_ENGINE, engineMode.name)
                putExtra(EXTRA_SAVER, batterySaver)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, JamService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
