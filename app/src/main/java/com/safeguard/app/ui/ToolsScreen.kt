package com.safeguard.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeguard.app.core.NetworkScanner
import com.safeguard.app.core.OtherRouterGuide
import com.safeguard.app.core.RouterGuide
import com.safeguard.app.core.ScheduleController
import com.safeguard.app.core.WifiAnalyzer
import com.safeguard.app.ui.theme.Mono
import com.safeguard.app.ui.theme.SgPalette

/** Everything the Tools tab can show. */
data class ToolsUi(
    val network: NetworkScanner.Network? = null,
    val devices: List<NetworkScanner.Device> = emptyList(),
    val scanning: Boolean = false,
    val scanError: String? = null,
    val wifi: WifiAnalyzer.Report? = null,
    val analysing: Boolean = false,
    val bedtime: ScheduleController.Bedtime = ScheduleController.Bedtime(false, 22, 7),
    val message: String? = null
)

@Composable
fun ToolsScreen(
    ui: ToolsUi,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onAnalyseWifi: () -> Unit,
    onOpenRouter: (String) -> Unit,
    onBedtimeToggle: (Boolean) -> Unit,
    onBedtimeHours: (Int, Int) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenWifiPanel: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SgPalette.Deep, SgPalette.DeepElevated, SgPalette.Deep)))
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(SgPalette.Card)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 11.dp, vertical = 7.dp)
            ) { Text("← BACK", color = SgPalette.Teal, fontSize = 9.sp, letterSpacing = 1.sp) }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("TOOLS", color = SgPalette.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("Legal network tools for your own home", color = SgPalette.TextSecondary, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // ------------------------------------------------ 1. WHO IS ON MY WI-FI
        BoxCard(
            icon = "👥",
            title = "Who is on my Wi-Fi",
            subtitle = "Sees every phone, TV and laptop sharing your home network right now.",
            accent = SgPalette.Teal
        ) {
            InfoRow("Network", ui.network?.ssid ?: "—")
            InfoRow("Router (gateway)", ui.network?.gateway ?: "—")
            InfoRow("Your subnet", ui.network?.subnet?.plus("/${ui.network.prefix}") ?: "—")
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                if (ui.scanning) "SCANNING…" else "SCAN MY WI-FI",
                enabled = !ui.scanning,
                onClick = onScan
            )
            if (ui.scanError != null) {
                Spacer(Modifier.height(6.dp))
                Text(ui.scanError, color = SgPalette.Amber, fontSize = 10.sp)
            }
            if (ui.devices.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${ui.devices.size} DEVICE(S) FOUND",
                    color = SgPalette.TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
                ui.devices.forEach { device ->
                    DeviceRow(device)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap a device to open the router page where you can block its MAC address. " +
                        "The app never blocks anything by itself — that is the router owner's decision.",
                    color = SgPalette.TextSecondary,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }
        }

        // ------------------------------------------------ 2. ROUTER QUICK ACCESS
        BoxCard(
            icon = "🛠",
            title = "Router quick access",
            subtitle = "One tap opens your router's admin page — where Wi-Fi can really be switched off.",
            accent = SgPalette.Amber
        ) {
            RouterGuide.commonGateways(ui.network?.subnet).forEach { gateway ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF060B12))
                        .clickable { onOpenRouter("http://$gateway") }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("http://$gateway", color = SgPalette.TextPrimary, fontSize = 10.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                    Text(
                        if (gateway == ui.network?.gateway) "YOUR ROUTER" else "OPEN",
                        color = if (gateway == ui.network?.gateway) SgPalette.Teal else SgPalette.TextSecondary,
                        fontSize = 8.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("HOW TO SWITCH YOUR Wi-Fi OFF (3 STEPS)", color = SgPalette.TextSecondary, fontSize = 9.sp, letterSpacing = 1.1.sp)
            RouterGuide.steps(ui.network?.gateway).forEach { step ->
                StepRow(step.title, step.detail)
            }
        }

        // ------------------------------------------------ 3. HOME WI-FI PAUSE
        BoxCard(
            icon = "⏸",
            title = "Pause home Wi-Fi",
            subtitle = "Stop the whole house internet for a set time — the legal way (router switch).",
            accent = SgPalette.Danger
        ) {
            Detail("Why not from the app?", "Only the router can switch a Wi-Fi network off for everyone. An app can cut its own phone's data, not the router's antenna.")
            Detail("Fastest way", "Open your router page (box above) → Wireless → 'Enable Wireless' = OFF → Save. Everyone loses Wi-Fi, mobile data keeps working.")
            Detail("Better for a family", "Set Access Control → Time Schedule, e.g. 22:00–06:00. It repeats itself every night without arguments.")
            Detail("Undo", "Same page, set it back to ON. Takes 10 seconds.")
            Spacer(Modifier.height(8.dp))
            PrimaryButton("OPEN ROUTER PAGE", enabled = true) {
                onOpenRouter("http://${ui.network?.gateway ?: "192.168.1.1"}")
            }
            Spacer(Modifier.height(6.dp))
            SecondaryButton("OPEN Wi-Fi SETTINGS ON THIS PHONE", onOpenWifiPanel)
        }

        // ------------------------------------------------ 4. OTHER ROUTER (child)
        BoxCard(
            icon = "🧑‍🤝‍🧑",
            title = "Child using another router",
            subtitle = "The right way to stop a device on a second router in your house.",
            accent = Color(0xFF49A0FF)
        ) {
            OtherRouterGuide.items().forEach { item ->
                StepRow(item.title, item.detail)
            }
        }

        // ------------------------------------------------ 5. BEDTIME
        BoxCard(
            icon = "🌙",
            title = "Bedtime blackout",
            subtitle = "This phone's internet switches off by itself every night. Works without root.",
            accent = SgPalette.Teal
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        if (ui.bedtime.enabled) "ACTIVE · ${ui.bedtime.describe()}" else "OFF",
                        color = if (ui.bedtime.enabled) SgPalette.Teal else SgPalette.TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = Mono
                    )
                    Text(
                        "During this window the data shield turns on and stays on.",
                        color = SgPalette.TextSecondary,
                        fontSize = 9.sp
                    )
                }
                ToggleSwitch(ui.bedtime.enabled, onBedtimeToggle)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HourChip("START", ui.bedtime.startHour, Modifier.weight(1f)) {
                    onBedtimeHours((ui.bedtime.startHour + 1) % 24, ui.bedtime.endHour)
                }
                HourChip("END", ui.bedtime.endHour, Modifier.weight(1f)) {
                    onBedtimeHours(ui.bedtime.startHour, (ui.bedtime.endHour + 1) % 24)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap a time to move it forward one hour.", color = SgPalette.TextSecondary, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("RESTRICT A SPECIFIC APP'S DATA", onOpenUsageAccess)
        }

        // ------------------------------------------------ 6. WI-FI ANALYSER
        BoxCard(
            icon = "📶",
            title = "Wi-Fi channel checker",
            subtitle = "Shows how crowded your channel is and which one to pick in the router for faster Wi-Fi.",
            accent = SgPalette.Amber
        ) {
            PrimaryButton(if (ui.analysing) "SCANNING…" else "CHECK MY CHANNELS", enabled = !ui.analysing, onClick = onAnalyseWifi)
            ui.wifi?.let { report ->
                Spacer(Modifier.height(8.dp))
                Text(report.message, color = if (report.ok) SgPalette.Teal else SgPalette.Amber, fontSize = 10.sp)
                if (report.ok) {
                    InfoRow("Best 2.4 GHz channel", "${report.recommended2g} (1 / 6 / 11 are the clean ones)")
                    InfoRow("Networks on your channel", "${report.sameChannelAsMine}")
                    Spacer(Modifier.height(6.dp))
                    report.channelLoad.toSortedMap().forEach { (channel, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("CH $channel", color = SgPalette.TextSecondary, fontSize = 9.sp, fontFamily = Mono, modifier = Modifier.width(52.dp))
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SgPalette.Grid)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((count / 8f).coerceIn(0.05f, 1f))
                                        .height(6.dp)
                                        .background(if (channel == report.recommended2g) SgPalette.Teal else SgPalette.Amber)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("$count", color = SgPalette.TextPrimary, fontSize = 9.sp, fontFamily = Mono)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BoxCard(
            icon = "⚖️",
            title = "What this app will never do",
            subtitle = "So nobody gets a nasty surprise.",
            accent = SgPalette.TextSecondary
        ) {
            Detail("No jamming", "It does not transmit interference. Jamming radio signals is illegal in Pakistan (PTA / PECA) and needs hardware, not an app.")
            Detail("No other people's phones", "Every tool here works only on the network this phone is already connected to, or on this phone's own data.")
            Detail("No hidden changes", "Nothing is changed in your router without you logging in and pressing Save.")
            if (ui.message != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                        .background(SgPalette.Teal.copy(alpha = 0.12f)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(ui.message, color = SgPalette.Teal, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Text("OK", color = SgPalette.Teal, fontSize = 9.sp, modifier = Modifier.clickable(onClick = onDismissMessage))
                }
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

// ---------------------------------------------------------------- components

@Composable
private fun BoxCard(
    icon: String,
    title: String,
    subtitle: String,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SgPalette.Card)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text(icon, fontSize = 15.sp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = SgPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = SgPalette.TextSecondary, fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        content()
    }
    Spacer(Modifier.height(11.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = SgPalette.TextSecondary, fontSize = 10.sp, modifier = Modifier.width(126.dp))
        Text(value, color = SgPalette.TextPrimary, fontSize = 10.sp, fontFamily = Mono)
    }
}

@Composable
private fun Detail(title: String, body: String) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(title, color = SgPalette.TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = SgPalette.TextSecondary, fontSize = 9.sp, lineHeight = 13.sp)
    }
}

@Composable
private fun StepRow(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 5.dp).size(4.dp).clip(CircleShape).background(SgPalette.Teal))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = SgPalette.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = SgPalette.TextSecondary, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun DeviceRow(device: NetworkScanner.Device) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF080F1A))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .background(if (device.isThisPhone) SgPalette.Teal else SgPalette.Amber)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                device.ip + if (device.isThisPhone) "  (you)" else "",
                color = SgPalette.TextPrimary,
                fontSize = 10.sp,
                fontFamily = Mono
            )
            Text(
                listOfNotNull(
                    device.mac,
                    device.hostname?.takeIf { it.isNotBlank() && it != device.ip },
                    if (device.ms > 0) "${device.ms} ms" else null
                ).joinToString(" · ").ifBlank { "MAC not visible (device hides it)" },
                color = SgPalette.TextSecondary,
                fontSize = 8.5.sp
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) SgPalette.Teal.copy(alpha = 0.16f) else SgPalette.Grid)
            .border(1.dp, if (enabled) SgPalette.Teal.copy(alpha = 0.6f) else SgPalette.Grid, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (enabled) SgPalette.Teal else SgPalette.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SgPalette.Card)
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = SgPalette.TextPrimary, fontSize = 9.sp, letterSpacing = 0.8.sp) }
}

@Composable
private fun HourChip(label: String, hour: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF060B12))
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = SgPalette.TextSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
        Text("%02d:00".format(hour), color = SgPalette.Teal, fontSize = 15.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) SgPalette.Teal else Color(0xFF1B2434))
            .clickable { onChange(!checked) }
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .size(20.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(if (checked) SgPalette.Deep else SgPalette.TextSecondary)
        )
    }
}

/** Intent helpers used by the Activity. */
object ToolsIntents {
    fun browser(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    fun usageAccess(packageName: String): Intent = Intent("android.settings.USAGE_ACCESS_SETTINGS")
    fun wifiSettings(): Intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
}
