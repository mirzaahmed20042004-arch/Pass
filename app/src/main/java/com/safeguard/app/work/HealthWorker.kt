package com.safeguard.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safeguard.app.core.NetworkMonitor
import com.safeguard.app.core.RadioController
import com.safeguard.app.core.ReleaseHandler
import com.safeguard.app.data.SettingsStore

/**
 * Periodic safety sweep (WorkManager, min 15 min).
 *
 * Two jobs:
 *  1. If a session is flagged active but no engine is running (process was
 *     killed), reconcile the radios so the phone is not stuck offline.
 *  2. Record a heartbeat of real connectivity for the dashboard history.
 */
class HealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SettingsStore(applicationContext)
        val monitor = NetworkMonitor(applicationContext)
        val serviceAlive = com.safeguard.app.service.JamService.instance?.engineRef?.isRunning == true

        if (store.sessionActive && !serviceAlive) {
            val radio = RadioController(applicationContext)
            ReleaseHandler(applicationContext, radio, monitor).hardReset(
                engine = null,
                wanted = radio.snapshot()
            )
            store.sessionActive = false
        }

        val online = monitor.isOnline()
        store.lastHeartbeat = if (online) System.currentTimeMillis() else store.lastHeartbeat
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "safeguard-health"
    }
}
