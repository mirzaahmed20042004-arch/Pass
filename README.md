# Safe Guard — SignalDisruptor Pro

> ## v1.2.0 — the strongest non-root build
>
> - **Signed release APK (1.0 MB)** → https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.2.0/SafeGuard-v1.2.0-release.apk
> - **Debug APK (16 MB)** → https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.2.0/SafeGuard-v1.2.0-debug.apk
> - Release page → https://github.com/mirzaahmed20042004-arch/Pass/releases/tag/v1.2.0
>
> ### What actually works on a stock, unrooted phone (Android 8 → 15)
>
> | Feature | Non-root status | How |
> |---|---|---|
> | **Data Blackout (kill-switch)** | ✅ WORKS | Local `VpnService` tunnel with no upstream — all app traffic dies instantly, restored in <1 s. One system consent dialog, then one tap. |
> | **DNS Blackout** | ✅ WORKS | Tunnel that swallows only resolver traffic — new connections time out. |
> | **Wi-Fi / Bluetooth / Airplane / Network-mode switches** | ⚠️ OS-owned | One tap opens the exact system panel that owns the switch (`INTERNET_CONNECTIVITY`, `WIFI`, `AIRPLANE_MODE`, network-operator picker). Direct API call is tried first and reported honestly. |
> | **Data Saver / background restriction** | ✅ via panel | Deep link to the OS Data Saver panel. |
> | **DNS saturation + uplink stress** | ✅ WORKS | Pure userspace. |
> | **Carrier / geo targeting, live latency-loss gauges, chaos visualiser** | ✅ WORKS | Real probes. |
> | **Instant release** | ✅ WORKS | Shield teardown → loops cancelled → radios restored → `resetNetwork()` → verified probe. |
> | **Jamming a neighbour's phone / the tower** | ❌ IMPOSSIBLE without radio hardware | Not a permission problem — it needs a transmitter. Also illegal (PTA/PECA). |
>
> Everything in the *Radio Controls* screen (new in v1.2.0) runs unrooted: shield on/off,
> Wi-Fi-off, Bluetooth-off, airplane panel, internet panel, Data Saver, network mode.


> **v1.1.0 update** — verified radio toggles, privilege detection, service-lifecycle fixes.
>
> - Signed release APK (1.0 MB) → https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.1.0/SafeGuard-v1.1.0-release.apk
> - Debug APK (16 MB) → https://github.com/mirzaahmed20042004-arch/Pass/releases/download/v1.1.0/SafeGuard-v1.1.0-debug.apk
> - Release page → https://github.com/mirzaahmed20042004-arch/Pass/releases/tag/v1.1.0
>
> **Read the "Why it can't block your neighbours" section below before installing.** No Android APK,
> rooted or not, can jam nearby phones' Wi-Fi/Bluetooth/cellular — that needs radio hardware, not software.

## Why it can't block your neighbours' signals (the honest answer)

Android gives third-party apps **no** API to switch off radios, and RF jamming is a hardware problem:

| What you asked for | Why an APK cannot do it |
|---|---|
| Switch off **Wi-Fi** | `WifiManager.setWifiEnabled()` is ignored for apps on Android 10+ (the OS returns `false`). |
| Switch off **Bluetooth** | `BluetoothAdapter.enable()/disable()` is a no-op for apps targeting Android 13+. |
| **Airplane mode** | `AIRPLANE_MODE_ON` requires `WRITE_SECURE_SETTINGS`, grantable only over adb or by the system. |
| **Mobile data / network mode** | `setDataEnabled`, `setPreferredNetworkType` are `@SystemApi` — system signature only. |
| **Block other people's phones ("aas paas ke sab")** | Jamming is radio-frequency energy. A phone's Wi-Fi/BT chipset covers ~10 m indoors and its cellular radio cannot transmit out-of-band at all. Transmitting noise to drown other bands is illegal in every country (Pakistan: PTA / PECA) and needs hardware — an app cannot do it. |

So the app ships in one of two honest states:

* **STANDARD (default, no root)** — `MONITORING · RADIO LOCKED`. It measures real latency/loss/signal,
  runs the CPU-only stress loops (DNS saturation, uplink hog), shows the chaos visualiser, and reports
  in the OS Capability Matrix exactly which switches the platform refused. It does **not** claim to jam.
* **PRIVILEGED (root or adb grant)** — the same build unlocks verified radio writes: airplane pulse,
  Wi-Fi/BT toggles, mobile-data cycling, RAT hopping, restricted background data.

### Unlock real control on your own phone (optional)

```bash
# Option A — one-time adb grant (no root, works on stock Android):
adb shell pm grant com.safeguard.app android.permission.WRITE_SECURE_SETTINGS
adb shell appops set com.safeguard.app SYSTEM_ALERT_WINDOW allow

# Option B — rooted device: the engine detects `su` automatically and uses it
# for `svc wifi/bt/data`, secure-settings writes and preferred-network switching.
```

After granting, reopen the app: the badge changes from `STANDARD` to `ADB GRANT` / `ROOT` and the
capability matrix flips to `ACTIVE`.

## What v1.1.0 fixed

* Radio toggles now **verify by reading state back** (public API → hidden service → `svc` shell), so
  "OK" is only reported when the radio really changed.
* Capability probe no longer touches radios while merely checking them (old build could leave Wi-Fi off).
* Service lifecycle: no more self-cancelling scope in `onDestroy`, no `runBlocking` on the service thread,
  `START_NOT_STICKY` + no silent re-jam after a kill, idempotent start/stop.
* Single owner for the release path (service restores the snapshot it captured; ViewModel only waits),
  restoring `data` power-cycle so the modem re-attaches in ~2 s.
* Privilege detector (`Privilege.kt`) + a `RADIO CONTROL NOT GRANTED` banner so the UI can never
  pretend a jam is happening when the OS is blocking it.


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
