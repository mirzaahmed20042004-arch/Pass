package com.safeguard.app

import android.app.Application
import com.safeguard.app.data.SettingsStore
import com.safeguard.app.work.WorkerScheduler

class SafeGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val store = SettingsStore(this)
        // A reboot wipes the radios' jammed state; only the flag needs healing.
        if (store.sessionActive) {
            // BootReceiver handles the actual restore; this is the fast path
            // for a cold start initiated by the user.
            store.sessionActive = false
        }
        WorkerScheduler.schedule(this)
    }
}
