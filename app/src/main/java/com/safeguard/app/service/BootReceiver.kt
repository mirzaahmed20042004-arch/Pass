package com.safeguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.safeguard.app.core.RadioController
import com.safeguard.app.core.ReleaseHandler
import com.safeguard.app.data.SettingsStore
import kotlinx.coroutines.runBlocking

/**
 * Safety net: if the process was killed mid-jam (or the device rebooted while
 * a session was flagged active) the radio state can be left detached. This
 * receiver restores it before the user notices.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val store = SettingsStore(context)
        if (!store.sessionActive) return

        Log.i(TAG, "Recovering from interrupted session")
        // Blocking is acceptable here: the receiver is short-lived and we must
        // finish the restore before the process goes away.
        runBlocking {
            ReleaseHandler(context).hardReset(
                engine = null,
                wanted = RadioController.Snapshot(
                    wifiEnabled = store.snapshotWifi,
                    bluetoothEnabled = store.snapshotBt,
                    airplaneMode = false
                )
            )
        }
        store.sessionActive = false
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
