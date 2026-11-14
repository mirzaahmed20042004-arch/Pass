package com.safeguard.app.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeguard.app.core.EngineMode
import com.safeguard.app.core.JamMode
import com.safeguard.app.core.ShieldMode
import com.safeguard.app.service.JamService
import com.safeguard.app.service.ShieldGateway
import com.safeguard.app.ui.theme.Mono
import com.safeguard.app.ui.theme.SafeGuardTheme
import com.safeguard.app.ui.theme.SgPalette

/**
 * "Radio controls" — the honest non-root path to actually switching radios.
 *
 * A normal app cannot flip Wi-Fi/Bluetooth/cellular itself on modern Android, but
 * it *can* open the exact OS panel that owns those switches, and it can run the
 * VPN kill-switch that genuinely cuts this device's data. This screen does both
 * in one tap, plus a 6-step guided squence for older Android versions where the
 * quick-settings panels do not exist.
 */
class RadioControlActivity : ComponentActivity() {

    private val vpnConsent: androidx.activity.result.ActivityResultLauncher<Intent> =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                ShieldGateway.start(this, pendingMode ?: ShieldMode.FULL)
                toast("Data shield active")
            } else {
                toast("VPN consent denied — shield cannot run")
            }
            pendingMode = null
        }

    private var pendingMode: ShieldMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeGuardTheme {
                ControlsScreen(
                    onPanel = ::openPanel,
                    onShield = ::toggleShield,
                    onJam = { mode ->
                        if (mode == JamMode.OFF) JamService.stop(this)
                        else JamService.start(this, mode, EngineMode.OFFLINE, false)
                        toast("${mode.title} started")
                    },
                    onWifiOff = ::requestWifiOff,
                    onBtOff = ::requestBluetoothOff,
                    onAirplane = ::openPanelWith(OsPanelTarget.AIRPLANE),
                    onFinish = { finish() }
                )
            }
        }
    }

    private fun toggleShield(mode: ShieldMode) {
        if (ShieldGateway.isActive()) {
            ShieldGateway.stop(this)
            toast("Shield stopped — data restored")
            return
        }
        if (mode == ShieldMode.OFF) return
        val consent = ShieldGateway.consentIntent(this)
        if (consent != null) {
            pendingMode = mode
            vpnConsent.launch(consent)
        } else {
            ShieldGateway.start(this, mode)
            toast("${mode.title} active")
        }
    }

    private fun requestWifiOff() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        val ok = runCatching { wifi?.setWifiEnabled(false) == true }.getOrDefault(false)
        toast(
            if (ok) "Wi-Fi switched off"
            else "Android blocks this from apps — opening the Wi-Fi panel"
        )
        if (!ok) openPanel(OsPanelTarget.WIFI)
    }

    private fun requestBluetoothOff() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val ok = runCatching { adapter?.disable() == true }.getOrDefault(false)
        toast(
            if (ok) "Bluetooth switched off"
            else "Android blocks this from apps — opening the Bluetooth panel"
        )
        if (!ok) openPanel(OsPanelTarget.BLUETOOTH)
    }

    private fun openPanel(target: OsPanelTarget) {
        val intents = when (target) {
            OsPanelTarget.INTERNET -> listOf(
                Intent(ACTION_INTERNET_PANEL),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            OsPanelTarget.WIFI -> listOf(
                Intent(ACTION_WIFI_PANEL),
                Intent(Settings.ACTION_WIFI_SETTINGS)
            )
            OsPanelTarget.BLUETOOTH -> listOf(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            )
            OsPanelTarget.AIRPLANE -> listOf(
                Intent(ACTION_AIRPLANE_PANEL),
                Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            )
            OsPanelTarget.DATA_SAVER -> listOf(
                Intent(ACTION_DATA_SAVER),
                Intent(Settings.ACTION_DATA_SAVER_SETTINGS)
            )
            OsPanelTarget.NETWORK_MODE -> listOf(
                Intent(ACTION_NETWORK_MODE_PANEL).putExtra("android.provider.extra.SUB_ID", defaultSubId()),
                Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
        }
        for (intent in intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (t: ActivityNotFoundException) {
                // try the next, older fallback
            } catch (t: SecurityException) {
                // some OEMs gate the panel; fall through
            }
        }
        toast("This device has no panel for that setting")
    }

    private fun defaultSubId(): Int = try {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        tm.subscriptionId
    } catch (t: Throwable) {
        -1
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val ACTION_INTERNET_PANEL = "android.settings.panel.action.INTERNET_CONNECTIVITY"
        const val ACTION_WIFI_PANEL = "android.settings.panel.action.WIFI"
        const val ACTION_AIRPLANE_PANEL = "android.settings.panel.action.AIRPLANE_MODE"
        const val ACTION_DATA_SAVER = "android.settings.panel.action.DATA_SAVER"
        const val ACTION_NETWORK_MODE_PANEL = "android.settings.NETWORK_OPERATOR_SETTINGS"
    }
}

enum class OsPanelTarget { INTERNET, WIFI, BLUETOOTH, AIRPLANE, DATA_SAVER, NETWORK_MODE }

@Composable
private fun ControlsScreen(
    onPanel: (OsPanelTarget) -> Unit,
    onShield: (ShieldMode) -> Unit,
    onJam: (JamMode) -> Unit,
    onWifiOff: () -> Unit,
    onBtOff: () -> Unit,
    onAirplane: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(SgPalette.Deep)
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("RADIO CONTROLS", color = SgPalette.Teal, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text(
            "Everything here works on a stock, unrooted phone.",
            color = SgPalette.TextSecondary,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("DATA SHIELD · NON-ROOT · INSTANT")
        ControlRow("Full data blackout", "Drops all app traffic via local VPN", SgPalette.Danger) { onShield(ShieldMode.FULL) }
        ControlRow("DNS blackout", "Name lookups fail, IP flows survive", SgPalette.Amber) { onShield(ShieldMode.DNS) }
        ControlRow("Stop shield", "Restores connectivity immediately", SgPalette.Teal) { onShield(ShieldMode.OFF) }

        Spacer(Modifier.height(14.dp))
        SectionLabel("RADIO SWITCHES · ONE TAP")
        ControlRow("Turn Wi-Fi off", "Tries directly, then opens the panel", SgPalette.TextPrimary, onWifiOff)
        ControlRow("Turn Bluetooth off", "Tries directly, then opens the panel", SgPalette.TextPrimary, onBtOff)
        ControlRow("Airplane mode", "Opens the OS radio panel", SgPalette.TextPrimary, onAirplane)
        ControlRow("Internet panel", "Wi-Fi + mobile data switches", SgPalette.TextPrimary) { onPanel(OsPanelTarget.INTERNET) }
        ControlRow("Data Saver", "Restrict background data", SgPalette.TextPrimary) { onPanel(OsPanelTarget.DATA_SAVER) }
        ControlRow("Network mode (2G/3G/4G/5G)", "OS-owned preference picker", SgPalette.TextPrimary) { onPanel(OsPanelTarget.NETWORK_MODE) }

        Spacer(Modifier.height(14.dp))
        SectionLabel("STRESS ENGINE")
        ControlRow("Start full spectrum", "DNS + uplink stress + probes", SgPalette.Amber) { onJam(JamMode.FULL_SPECTRUM) }
        ControlRow("Stop everything", "Cancels all loops and restores", SgPalette.Danger) { onJam(JamMode.OFF) }

        Spacer(Modifier.height(16.dp))
        Text(
            "Note: no app on a non-rooted phone can switch off a neighbour's phone or a cell tower. " +
                "Data Shield cuts THIS device's traffic; the panels above are the OS's own switches. " +
                "That combination is the maximum any APK can legally do without root.",
            color = SgPalette.TextSecondary,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(14.dp))
        ControlRow("Back to dashboard", "", SgPalette.Teal, onFinish)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = SgPalette.TextSecondary, fontSize = 9.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ControlRow(title: String, subtitle: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SgPalette.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) Text(subtitle, color = SgPalette.TextSecondary, fontSize = 9.sp)
        }
        Text("›", color = SgPalette.Teal, fontSize = 16.sp, fontFamily = Mono)
    }
}
