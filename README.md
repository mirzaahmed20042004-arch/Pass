# Safe Guard — SignalDisruptor Pro

Single-screen Android app for **RF diagnostics and network-resilience testing**. Kotlin · MVVM · Jetpack
Compose · Coroutines · WorkManager · Min SDK 26 (Android 8.0) · Target/Compile SDK 34.

> **Direct download (built by CI, installable):**
> - Signed release APK → https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.0.0/SafeGuard-v1.0.0-release.apk
> - Debug APK (same features, debuggable) → https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.0.0/SafeGuard-v1.0.0-debug.apk
> - Release page → https://github.com/mirzaahmed20042004-arch/Pass/releases/tag/v1.0.0

![Safe Guard logo](branding/safe-guard-logo.png)

---

## What it actually does (honest capability model)

Android removed almost all programmatic radio control from normal apps, and **no app can jam RF without
root or a privileged system signature**. Rather than ship buttons that lie, Safe Guard splits every module
into “works unprivileged”, “needs a one-line adb grant”, and “needs a system build”, probes the device at
startup, and shows the result in an **OS Capability Matrix** on the dashboard.

| Module | Real behaviour | Status without privileges |
|---|---|---|
| RAT hopping (5G→4G→3G→2G / 500 ms) | `setPreferredNetworkType` / `setAllowedNetworkTypesForReason` via reflection, one mode per tick | Denied by OS → modelled + logged, never faked as success |
| Airplane pulse (200 ms ON/OFF) | Writes `AIRPLANE_MODE_ON`, broadcasts the change, reads it back to verify | Denied unless `WRITE_SECURE_SETTINGS` is granted over adb |
| Dual-radio interference | Alternates `WifiManager.setWifiEnabled` + `BluetoothAdapter.enable/disable` (2.4 GHz band sharing) | Wi-Fi toggle denied on Android 10+, BT toggle denied on 13+ |
| DNS cache saturation | Hundreds of unique-hostname resolver lookups per burst through the system resolver | **Works unprivileged** |
| Uplink hog (ping/UDP flood) | Real ICMP probes via the system `ping` binary + bounded UDP bursts to the resolver port | **Works unprivileged** |
| Simulated data cap | `ConnectivityManager.setRestrictBackground` via reflection (NetworkPolicy style) | Needs `MANAGE_NETWORK_POLICY` |
| Carrier detection | `TelephonyManager.getNetworkOperatorName()` / `simOperatorName` + data network type | Works (READ_PHONE_STATE helps) |
| Geo-fenced focus | Indoor/outdoor heuristic from Wi-Fi RSSI + last known network fix → biases Wi-Fi/BT vs cellular | Works with location permission |
| Online Enhanced | OpenCellID spectrum lookup (needs an API key) or a deterministic on-device band model | Works; source is labelled in the UI |
| Instant Release | Cancels loops + sockets, restores the captured pre-jam snapshot, `resetNetwork()`, DNS flush, re-scan, verified by a live probe | **Works unprivileged** (radio restores limited only by the same OS rules) |

Armed privileged features (optional, your call):

```bash
adb shell pm grant com.safeguard.app android.permission.WRITE_SECURE_SETTINGS   # airplane pulse
adb shell appops set com.safeguard.app SYSTEM_ALERT_WINDOW allow               # floating badge
```

---

## Code structure (requested order)

| File | Role |
|---|---|
| `app/src/main/AndroidManifest.xml` | Permissions (version-gated BT pairs), foreground service with `dataSync\|connectedDevice` types, overlay, boot receiver |
| `MainActivity.kt` | Single activity, Compose host, runtime-permission launcher, edge-to-edge |
| `core/JamEngine.kt` | The disruptor: RAT hop, airplane pulse, dual-radio, DNS loop, uplink loop, measurement loop, capability probe |
| `core/NetworkMonitor.kt` | Real probe layer: ICMP/TCP pulse, DNS burst, UDP uplink burst, gateway from `/proc/net/route`, RSSI, indoor heuristic |
| `core/ReleaseHandler.kt` | **Instant Release**: ordered teardown → radio restore → `resetNetwork()` → DNS flush → re-scan → verified probe report |
| `core/RadioController.kt` | Thin honest wrapper over Connectivity/Wifi/Telephony/Bluetooth + reflection fallbacks |
| `service/JamService.kt` | Foreground service owning the engine; live notification with STOP & RESTORE; wake locks; task-removal and destroy both hard-reset |
| `service/OverlayBadge.kt` | `TYPE_APPLICATION_OVERLAY` floating “Jamming Active” pill with a pulsing glow |
| `service/ServiceBridge.kt` | `StateFlow` bridge from service → ViewModel |
| `service/BootReceiver.kt`, `work/HealthWorker.kt` | Crash/reboot reconciliation so a killed process can never leave radios flipped |
| `MainViewModel.kt` | MVVM state owner; commands only, no direct radio access |
| `ui/DashboardScreen.kt` | The single screen: header, status strip, visualiser, gauges, one-tap actions, modes, targeting, capability matrix |
| `ui/components/Waveform.kt`, `Gauges.kt` | Chaotic-when-jamming waveform + radial gauges |

## Instant Release contract

`STOP` button, notification action, swipe-away (`onTaskRemoved`), activity finish (`onDestroy`), service
destroy, and reboot recovery all funnel into the same path:

1. cancel every coroutine job and close every socket,
2. lift the restricted-background-data flag,
3. restore the exact snapshot captured at jam start (Wi-Fi / Bluetooth / airplane),
4. `ConnectivityManager.resetNetwork()` (or force a fresh attach), DNS flush, `startScan()`,
5. probe the gateway and report the measured result — "restored in 412 ms · Wi-Fi ON · gateway 28 ms".

## Build

```bash
gradle :app:assembleDebug     # or ./gradlew on a machine with the wrapper
gradle :app:assembleRelease   # signed when SG_KEYSTORE_* env vars are present
```

CI: `.github/workflows/build-apk.yml` builds, signs, uploads artifacts and publishes the release on every
push; a second job boots an Android emulator, installs the APK, launches it and captures a screenshot so
regressions are caught automatically.
