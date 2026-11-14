package com.safeguard.app.core

import android.Manifest
import android.os.Build

/** The runtime permission set the dashboard asks for, version-aware. */
object PermissionPlan {

    fun required(sdk: Int): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (sdk >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    /** Nice-to-have, asked only if the user opts into carrier/gauge enrichment. */
    fun telephony(sdk: Int): List<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
    }
}
