package com.safeguard.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.app.core.PermissionPlan
import com.safeguard.app.service.JamService
import com.safeguard.app.ui.DashboardScreen
import com.safeguard.app.ui.theme.SafeGuardTheme
import com.safeguard.app.ui.theme.SgPalette

/**
 * Single-activity, single-screen Compose host. Owns no engine state — the
 * ViewModel + JamService do — so rotation, backgrounding and process death all
 * behave predictably.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == RESULT_OK
        viewModel.onShieldConsent(granted)
        toast(
            if (granted) "VPN approved — shield starting"
            else "VPN denied. Tap BLACKOUT again and press OK."
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionsResolved() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SafeGuardTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val tools by viewModel.tools.collectAsStateWithLifecycle()
                var showTools by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val missing = PermissionPlan.required(Build.VERSION.SDK_INT).filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                            PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                }

                Surface(modifier = Modifier.fillMaxSize(), color = SgPalette.Deep) {
                    if (showTools) {
                        com.safeguard.app.ui.ToolsScreen(
                            ui = tools,
                            onBack = { showTools = false },
                            onScan = { viewModel.scanNetwork() },
                            onAnalyseWifi = { viewModel.analyseWifi() },
                            onOpenRouter = { url -> runCatching { startActivity(viewModel.routerIntent(url)) } },
                            onBedtimeToggle = { enabled -> viewModel.setBedtime(enabled) },
                            onBedtimeHours = { start, end -> viewModel.setBedtime(tools.bedtime.enabled, start, end) },
                            onOpenUsageAccess = { runCatching { startActivity(viewModel.usageAccessIntent()) } },
                            onOpenWifiPanel = { runCatching { startActivity(viewModel.wifiSettingsIntent()) } },
                            onDismissMessage = { viewModel.dismissToolsMessage() }
                        )
                    } else {
                    DashboardScreen(
                        ui = ui,
                        onEngage = { mode -> viewModel.engage(mode) },
                        onRelease = { viewModel.disengage() },
                        onEngineMode = viewModel::setEngineMode,
                        onBatterySaver = viewModel::setBatterySaver,
                        onOverlay = viewModel::setOverlay,
                        onOverlaySettings = {
                            runCatching { startActivity(viewModel.overlaySettingsIntent()) }
                        },
                        onBatterySettings = {
                            runCatching { startActivity(viewModel.batteryOptimizationIntent()) }
                        },
                        onRefreshSpectrum = viewModel::refreshSpectrum,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                (PermissionPlan.required(Build.VERSION.SDK_INT) +
                                    PermissionPlan.telephony(Build.VERSION.SDK_INT)).toTypedArray()
                            )
                        },
                        onDismissPrompt = viewModel::dismissPrompt,
                        onShield = { mode -> requestShield(mode) },
                        onOpenRadioControls = {
                            runCatching { startActivity(viewModel.openRadioControls()) }
                        },
                        onActivate = {
                            viewModel.engage(com.safeguard.app.core.JamMode.FULL_SPECTRUM)
                            requestShield(com.safeguard.app.core.ShieldMode.FULL)
                        },
                        onEmergencyRestore = { viewModel.disengage() },
                        onOpenTools = {
                            viewModel.refreshNetworkInfo()
                            showTools = true
                        }
                    )
                    }
                }
            }
        }
    }

    /** Single place that owns the VPN consent handshake. */
    private fun requestShield(mode: com.safeguard.app.core.ShieldMode) {
        if (mode == com.safeguard.app.core.ShieldMode.OFF) {
            viewModel.stopShield()
            return
        }
        viewModel.pendingShield = mode
        val consent = viewModel.shieldConsentIntent()
        if (consent != null) {
            vpnConsentLauncher.launch(consent)
        } else {
            viewModel.startShield(mode)
            toast("${mode.title} active")
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncShieldState()
        viewModel.onPermissionsResolved()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            val engine = JamService.instance?.engineRef
            if (engine?.isRunning == true) {
                JamService.stop(applicationContext)
            }
        }
    }
}
