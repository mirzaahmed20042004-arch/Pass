package com.safeguard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeguard.app.MainViewModel
import com.safeguard.app.core.Availability
import com.safeguard.app.core.EngineMode
import com.safeguard.app.core.JamMode
import com.safeguard.app.core.JamState
import com.safeguard.app.ui.components.GaugeCard
import com.safeguard.app.ui.components.MiniBar
import com.safeguard.app.ui.components.SignalBars
import com.safeguard.app.ui.components.Waveform
import com.safeguard.app.ui.components.chaosLabel
import com.safeguard.app.ui.components.signalPercent
import com.safeguard.app.ui.theme.Mono
import com.safeguard.app.ui.theme.SgPalette

@Composable
fun DashboardScreen(
    ui: MainViewModel.UiState,
    onEngage: (JamMode) -> Unit,
    onRelease: () -> Unit,
    onEngineMode: (EngineMode) -> Unit,
    onBatterySaver: (Boolean) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onOverlaySettings: () -> Unit,
    onBatterySettings: () -> Unit,
    onRefreshSpectrum: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDismissPrompt: () -> Unit
) {
    val jam = ui.jam
    val local = ui.local
    val accent by animateColorAsState(
        if (jam.running) SgPalette.Danger else SgPalette.Teal,
        tween(500),
        label = "accent"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        SgPalette.Deep,
                        if (jam.running) Color(0xFF160A12) else SgPalette.DeepElevated,
                        SgPalette.Deep
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Header(jam)
        Spacer(Modifier.height(18.dp))

        StatusStrip(jam, accent)
        Spacer(Modifier.height(14.dp))

        WaveformCard(jam, accent)
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GaugeCard(
                progress = signalPercent(jam.signalDbm),
                value = "${jam.signalDbm}",
                unit = "dBm",
                label = "Signal",
                color = if (jam.running) SgPalette.Amber else SgPalette.Teal,
                modifier = Modifier.weight(1f),
                footer = "model"
            )
            GaugeCard(
                progress = (jam.latencyMs / 400f).coerceIn(0f, 1f),
                value = if (jam.latencyMs == 0) "—" else "${jam.latencyMs}",
                unit = "ms",
                label = "Latency",
                color = SgPalette.Teal,
                modifier = Modifier.weight(1f),
                footer = "real probe"
            )
            GaugeCard(
                progress = jam.lossPercent / 100f,
                value = "${jam.lossPercent.toInt()}",
                unit = "%",
                label = "Packet loss",
                color = SgPalette.Danger,
                modifier = Modifier.weight(1f),
                footer = "real probe"
            )
        }

        Spacer(Modifier.height(16.dp))
        ActivateBlock(jam, local.releasing, onEngage, onRelease)
        Spacer(Modifier.height(14.dp))
        QuickActions(jam, onEngage)
        Spacer(Modifier.height(16.dp))
        ModesRow(local.engineMode, onEngineMode)
        Spacer(Modifier.height(14.dp))
        togglesCard(local, jam, onBatterySaver, onOverlay, onOverlaySettings)
        Spacer(Modifier.height(14.dp))
        TargetingCard(jam, local, onRefreshSpectrum)
        Spacer(Modifier.height(14.dp))
        CapabilityCard(jam)
        Spacer(Modifier.height(14.dp))
        ReleaseCard(local, jam)
        Spacer(Modifier.height(14.dp))
        PermissionCard(onRequestPermissions, onBatterySettings, onDismissPrompt, local.permissionPrompt)
        Spacer(Modifier.height(20.dp))
        Text(
            "SafeGuard is an RF diagnostics / resilience testing tool. It uses only public,\n" +
                "unprivileged Android APIs; privileged features stay disabled unless you grant\n" +
                "them yourself over adb. Never use it to interfere with networks you don't own.",
            color = SgPalette.TextSecondary.copy(alpha = 0.7f),
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun Header(jam: JamState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(SgPalette.Teal, SgPalette.TealDim))),
            contentAlignment = Alignment.Center
        ) {
            Text("SG", color = SgPalette.Deep, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "SAFE GUARD",
                color = SgPalette.TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )
            Text(
                "SignalDisruptor Pro · v1.0",
                color = SgPalette.TextSecondary,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (jam.online) SgPalette.Teal else SgPalette.Amber)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (jam.online) "ONLINE" else "OFFLINE",
                    color = SgPalette.TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
            Text(jam.carrier, color = SgPalette.TextPrimary.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatusStrip(jam: JamState, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SgPalette.Card)
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (jam.running) "JAMMING ACTIVE" else "ENGINE IDLE",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp
            )
            Text(
                "${jam.mode.title} · ${jam.intervalMs} ms · ${jam.networkType}",
                color = SgPalette.TextSecondary,
                fontSize = 10.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(jam.chaosFakeSignal, jam.running)
                Spacer(Modifier.width(8.dp))
                Text(
                    chaosLabel(jam.chaos),
                    color = if (jam.running) SgPalette.Danger else SgPalette.Teal,
                    fontSize = 9.sp,
                    fontFamily = Mono,
                    letterSpacing = 1.sp
                )
            }
            Text(
                "${if (jam.indoor) "INDOOR" else "OUTDOOR"} focus",
                color = SgPalette.TextSecondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun WaveformCard(jam: JamState, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SgPalette.Card)
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(18.dp))
            .padding(vertical = 8.dp)
    ) {
        Waveform(chaos = jam.chaos, modifier = Modifier.padding(horizontal = 4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Metric("CYCLES", jam.cycles.toString(), SgPalette.TextPrimary)
            Metric("DNS", jam.dnsQueries.toString(), SgPalette.Teal)
            Metric("PINGS", jam.pingsSent.toString(), SgPalette.TextSecondary)
            Metric("UPLINK", "${jam.uplinkKb} KB", SgPalette.Amber)
            Spacer(Modifier.weight(1f))
            Text(
                if (jam.running) "LIVE" else "IDLE",
                color = accent,
                fontSize = 9.sp,
                fontFamily = Mono,
                letterSpacing = 1.4.sp
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(Modifier.padding(end = 16.dp)) {
        Text(label, color = SgPalette.TextSecondary, fontSize = 8.sp, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActivateBlock(
    jam: JamState,
    releasing: Boolean,
    onEngage: (JamMode) -> Unit,
    onRelease: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (jam.running) 1.10f else 1.02f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(196.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                (if (jam.running) SgPalette.Danger else SgPalette.Teal).copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(158.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            if (jam.running) {
                                listOf(Color(0xFF40121F), Color(0xFF1A0910))
                            } else {
                                listOf(Color(0xFF0F2A26), Color(0xFF0A1714))
                            }
                        )
                    )
                    .border(
                        2.dp,
                        if (jam.running) SgPalette.Danger else SgPalette.Teal,
                        CircleShape
                    )
                    .clickable(enabled = !releasing) {
                        if (jam.running) onRelease() else onEngage(JamMode.FULL_SPECTRUM)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            releasing -> "RESTORING"
                            jam.running -> "STOP"
                            else -> "ACTIVATE"
                        },
                        color = if (jam.running) SgPalette.Danger else SgPalette.Teal,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        when {
                            releasing -> "hard reset running"
                            jam.running -> "instant release"
                            else -> "full spectrum jam"
                        },
                        color = SgPalette.TextSecondary,
                        fontSize = 9.sp,
                        letterSpacing = 0.9.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            jam.lastEvent,
            color = SgPalette.TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
    }
}

@Composable
private fun QuickActions(jam: JamState, onEngage: (JamMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        QuickChip("Wi-Fi only", jam.mode == JamMode.WIFI_ONLY, Modifier.weight(1f)) {
            onEngage(if (jam.mode == JamMode.WIFI_ONLY) JamMode.OFF else JamMode.WIFI_ONLY)
        }
        QuickChip("Mobile only", jam.mode == JamMode.MOBILE_ONLY, Modifier.weight(1f)) {
            onEngage(if (jam.mode == JamMode.MOBILE_ONLY) JamMode.OFF else JamMode.MOBILE_ONLY)
        }
        QuickChip("Full spectrum", jam.mode == JamMode.FULL_SPECTRUM, Modifier.weight(1f)) {
            onEngage(if (jam.mode == JamMode.FULL_SPECTRUM) JamMode.OFF else JamMode.FULL_SPECTRUM)
        }
    }
}

@Composable
private fun QuickChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SgPalette.Danger.copy(alpha = 0.16f) else SgPalette.Card)
            .border(
                1.dp,
                if (selected) SgPalette.Danger else SgPalette.CardStroke,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) SgPalette.Danger else SgPalette.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun ModesRow(current: EngineMode, onSelect: (EngineMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ModeCard(
            title = "PURE OFFLINE",
            subtitle = "On-device CPU only",
            selected = current == EngineMode.OFFLINE,
            modifier = Modifier.weight(1f)
        ) { onSelect(EngineMode.OFFLINE) }
        ModeCard(
            title = "ONLINE ENHANCED",
            subtitle = "Cloud spectrum hints",
            selected = current == EngineMode.ONLINE,
            modifier = Modifier.weight(1f)
        ) { onSelect(EngineMode.ONLINE) }
    }
}

@Composable
private fun ModeCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) SgPalette.Teal.copy(alpha = 0.10f) else SgPalette.Card)
            .border(
                1.dp,
                if (selected) SgPalette.Teal.copy(alpha = 0.7f) else SgPalette.CardStroke,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(title, color = if (selected) SgPalette.Teal else SgPalette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = SgPalette.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun togglesCard(
    local: MainViewModel.LocalUi,
    jam: JamState,
    onBatterySaver: (Boolean) -> Unit,
    onOverlay: (Boolean) -> Unit,
    onOverlaySettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SgPalette.Card)
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        ToggleRow(
            title = "Battery saver",
            subtitle = if (local.batterySaver) "2 s cadence · lighter drain" else "500 ms cadence · max disruption",
            checked = local.batterySaver,
            onChange = onBatterySaver
        )
        Spacer(Modifier.height(10.dp))
        ToggleRow(
            title = "Floating badge",
            subtitle = if (local.overlayGranted) "Shows over other apps while jamming" else "Needs overlay permission — tap to grant",
            checked = local.overlayEnabled && local.overlayGranted,
            onChange = { enabled ->
                if (enabled && !local.overlayGranted) onOverlaySettings() else onOverlay(enabled)
            }
        )
        if (jam.running) {
            Spacer(Modifier.height(10.dp))
            MiniBar(jam.chaos, SgPalette.Danger)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SgPalette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = SgPalette.TextSecondary, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SgPalette.Deep,
                checkedTrackColor = SgPalette.Teal,
                uncheckedThumbColor = SgPalette.TextSecondary,
                uncheckedTrackColor = Color(0xFF1B2434),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun TargetingCard(jam: JamState, local: MainViewModel.LocalUi, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SgPalette.Card)
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SMART TARGETING",
                color = SgPalette.TextSecondary,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "REFRESH",
                color = SgPalette.Teal,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.clickable(onClick = onRefresh)
            )
        }
        Spacer(Modifier.height(10.dp))
        TargetLine("Carrier", jam.carrier, SgPalette.TextPrimary)
        TargetLine("Radio", "${jam.networkType} · Wi-Fi ${jam.wifiRssi} dBm", SgPalette.TextPrimary)
        TargetLine(
            "Environment",
            if (jam.indoor) "Indoors — Wi-Fi/BT focus" else "Outdoors — cellular hopping focus",
            if (jam.indoor) SgPalette.Amber else SgPalette.Teal
        )
        if (jam.crowdedBands.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Crowded bands: ${jam.crowdedBands.joinToString(" · ")}",
                color = SgPalette.Teal,
                fontSize = 10.sp,
                fontFamily = Mono
            )
        }
        Text(
            "Spectrum source: ${local.spectrumSource}${if (local.towers > 0) " · ${local.towers} towers" else ""}",
            color = SgPalette.TextSecondary,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun TargetLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = SgPalette.TextSecondary, fontSize = 11.sp, modifier = Modifier.widthIn(min = 96.dp))
        Text(value, color = color, fontSize = 11.sp, fontFamily = Mono)
    }
}

@Composable
private fun CapabilityCard(jam: JamState) {
    if (jam.capabilities.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SgPalette.Card)
            .border(1.dp, SgPalette.CardStroke, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            "OS CAPABILITY MATRIX",
            color = SgPalette.TextSecondary,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(8.dp))
        jam.capabilities.forEach { cap ->
            val color = when (cap.availability) {
                Availability.ACTIVE -> SgPalette.Teal
                Availability.READY -> SgPalette.TextPrimary
                Availability.NEEDS_PRIVILEGE -> SgPalette.Amber
                Availability.UNSUPPORTED -> SgPalette.Danger
            }
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(8.dp))
                Text(cap.name, color = SgPalette.TextPrimary, fontSize = 11.sp, modifier = Modifier.widthIn(min = 118.dp))
                Text(
                    cap.availability.name.replace('_', ' '),
                    color = color,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(local: MainViewModel.LocalUi, jam: JamState) {
    AnimatedVisibility(visible = local.lastRelease.isNotBlank()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SgPalette.Teal.copy(alpha = 0.07f))
                .border(1.dp, SgPalette.Teal.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                "INSTANT RELEASE · ${local.releaseCount} RESETS",
                color = SgPalette.Teal,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(local.lastRelease, color = SgPalette.TextPrimary, fontSize = 10.sp, fontFamily = Mono, lineHeight = 15.sp)
            Text(
                "Press STOP, close the app, or swipe it away — the release path always runs.",
                color = SgPalette.TextSecondary,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun PermissionCard(
    onRequest: () -> Unit,
    onBattery: () -> Unit,
    onDismiss: () -> Unit,
    prompt: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SgPalette.Card)
            .border(1.dp, Color(0x3349A0FF), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text("ACCESS", color = SgPalette.TextSecondary, fontSize = 9.sp, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(8.dp))
        ActionRow("Grant runtime permissions", "Location · Bluetooth · Notifications", onRequest)
        ActionRow("Allow overlay badge", "SYSTEM_ALERT_WINDOW", { })
        ActionRow("Disable battery optimisation", "Keeps the foreground engine alive", onBattery)
        if (prompt != null) {
            Text(
                "Overlay permission is required for the floating badge. Enable it, then flip the switch again.",
                color = SgPalette.Amber,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                "DISMISS",
                color = SgPalette.Teal,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onDismiss)
            )
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SgPalette.TextPrimary, fontSize = 11.sp)
            Text(subtitle, color = SgPalette.TextSecondary, fontSize = 9.sp)
        }
        Text("›", color = SgPalette.Teal, fontSize = 16.sp)
    }
}
