package com.safeguard.app.core

/**
 * Non-root data-plane modes, implemented with a local VPN (see ShieldVpnService).
 *
 *  - [DNS]  : only name resolution is blackholed. New connections fail, already
 *             established IP flows keep working. Light touch, easy to demo.
 *  - [FULL] : every app flow on the device is routed into a tunnel with no
 *             upstream and dropped. Effective "data off" without root.
 */
enum class ShieldMode {
    OFF,
    DNS,
    FULL;

    val title: String
        get() = when (this) {
            OFF -> "Shield off"
            DNS -> "DNS blackout"
            FULL -> "Full data blackout"
        }

    val detail: String
        get() = when (this) {
            OFF -> "Traffic flows normally."
            DNS -> "Name lookups fail — new connections time out, old ones survive."
            FULL -> "All app traffic is dropped. This phone goes dark until STOP."
        }
}

/** Deep links into the OS panels that can actually switch radios on a stock phone. */
enum class OsPanel(val label: String, val hint: String) {
    INTERNET("Internet", "Wi-Fi + mobile data toggles"),
    WIFI("Wi-Fi", "Wi-Fi on/off panel"),
    BLUETOOTH("Bluetooth", "Bluetooth on/off panel"),
    AIRPLANE("Airplane mode", "Radio off in one tap"),
    DATA_SAVER("Data Saver", "Restrict background data"),
    NETWORK_MODE("Network mode", "2G/3G/4G/5G preference")
}
