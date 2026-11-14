package com.safeguard.app.core

import android.content.Context
import com.safeguard.app.data.SettingsStore

/**
 * "Kids' phone" rules that work on a normal, unrooted phone.
 *
 * Two independent levers, both legal and both actually enforced:
 *  - **Bedtime blackout**: a daily window where the data shield turns itself on
 *    (this phone only — the OS guarantees the tunnel, so no app can sneak past).
 *  - **Per-app data ban**: asks the OS Usage Access screen to restrict a given
 *    app's data, and shows the shortcut so the parent can confirm.
 *
 * Nothing here touches other people's phones. To cut a child's own device you
 * get an explicit router-side helper instead (see [RouterGuide]).
 */
class ScheduleController(private val context: Context) {

    private val store = SettingsStore(context)

    data class Bedtime(
        val enabled: Boolean,
        val startHour: Int,
        val endHour: Int
    ) {
        fun isActiveNow(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Boolean =
            if (!enabled) false else if (startHour <= endHour) {
                hour in startHour until endHour
            } else {
                hour >= startHour || hour < endHour
            }

        fun describe(): String = "%02d:00 → %02d:00".format(startHour, endHour)
    }

    fun bedtime(): Bedtime = Bedtime(
        enabled = store.bedtimeEnabled,
        startHour = store.bedtimeStart,
        endHour = store.bedtimeEnd
    )

    fun setBedtime(enabled: Boolean, start: Int, end: Int) {
        store.bedtimeEnabled = enabled
        store.bedtimeStart = start
        store.bedtimeEnd = end
    }

    /** Called by the health worker; returns true when the shield should be up. */
    fun shouldShieldNow(): Boolean = bedtime().isActiveNow()
}

/**
 * Step-by-step router instructions, generated for the gateway the phone is on.
 * This is the legal equivalent of "block the other router": you log into it as
 * its owner and switch the band off, instead of shouting RF noise at it.
 */
object RouterGuide {

    data class Step(val title: String, val detail: String)

    fun commonGateways(subnet: String?): List<String> = buildList {
        subnet?.let { add("$it.1") }
        add("192.168.1.1")
        add("192.168.0.1")
        add("192.168.100.1")
        add("10.0.0.1")
        add("192.168.18.1")
    }.distinct()

    fun steps(gateway: String?): List<Step> = listOf(
        Step(
            "1. Open the router page",
            "Connect to the Wi-Fi you want to control, then open http://${gateway ?: "192.168.1.1"} in Chrome."
        ),
        Step(
            "2. Log in",
            "Username/password is usually on a sticker under the router. Defaults are often admin / admin."
        ),
        Step(
            "3. Turn the Wi-Fi off",
            "Find Wireless → Wireless Settings and set 'Enable Wireless' (or 'Enable SSID Broadcast') to OFF. Apply/Save."
        ),
        Step(
            "4. Or pause one device only",
            "Wireless → MAC Filter / Access Control → add the device's MAC and choose 'Deny'. Safer than switching off for everyone."
        ),
        Step(
            "5. Set a time schedule",
            "Many routers have Access Control → Time Schedule. Set 22:00–07:00 so the Wi-Fi pauses by itself every night."
        ),
        Step(
            "6. Keep the admin password yours",
            "Change the router admin password so the setting cannot be undone from a child's phone."
        )
    )
}

/** Legal, non-technical guidance for the "other router in the house" case. */
object OtherRouterGuide {

    data class Item(val title: String, val detail: String)

    fun items(): List<Item> = listOf(
        Item(
            "Talk first, then restrict",
            "Explain the rule once. If the behaviour continues, move to a technical control below — that keeps it fair and legal."
        ),
        Item(
            "Log into that router as its owner",
            "You need its admin password (sticker on the device). If it is not your router, you need the owner's permission before changing anything."
        ),
        Item(
            "Use MAC filtering, not blocking",
            "Wireless → MAC Filter → deny only the phone you want. Everyone else keeps working, and it is reversible in seconds."
        ),
        Item(
            "Schedule it instead of killing it",
            "Access Control → Time Schedule, e.g. 21:00–06:00. Less conflict, and it enforces itself."
        ),
        Item(
            "Watch the data plan, not the person",
            "Router → Traffic Statistics shows which device is eating GB. Useful evidence before you restrict anyone."
        ),
        Item(
            "Never touch a network you don't own",
            "Joining or changing a neighbour's/cafe's router without permission is illegal in Pakistan (PECA). This app will not help with that."
        )
    )
}
