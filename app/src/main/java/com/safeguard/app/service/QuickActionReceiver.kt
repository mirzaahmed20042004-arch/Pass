package com.safeguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Handles the panic action from the notification / widget / adb test hooks. */
class QuickActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                val serviceIntent = Intent(context, JamService::class.java)
                    .setAction(JamService.ACTION_STOP)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP_ALL = "com.safeguard.app.ACTION_STOP_ALL"
    }
}
