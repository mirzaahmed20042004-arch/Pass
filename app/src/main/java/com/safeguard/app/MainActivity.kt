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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.safeguard.app.core.JamMode
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionsResolved() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SafeGuardTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    val missing = PermissionPlan.required(Build.VERSION.SDK_INT).filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                            PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                }

                Surface(modifier = Modifier.fillMaxSize(), color = SgPalette.Deep) {
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
                        onDismissPrompt = viewModel::dismissPrompt
                    )
                }
            }
        }
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
