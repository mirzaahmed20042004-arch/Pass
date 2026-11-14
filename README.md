# Voice Filter — male & female voice changer with game HUD for Android

Full-stack Android app built **from source with no Android Studio and no Gradle**:
`aapt2` → `javac` → `D8` → hand-rolled zip packaging → `jarsigner` (v1) → custom
zipalign → custom **APK Signature Scheme v2** signer (verified with `apksigtool`).
Signed release: [`apk/VoiceFilter-v2.0.apk`](apk/VoiceFilter-v2.0.apk).

* Package `com.voicefilter.app` · minSdk 24 (Android 7.0) · targetSdk 29 · ~166 KB
* Zero third-party runtime libraries — pure Android framework + pure-Java DSP
* **No root, no injection, no hidden behaviour** — microphone, overlay and network only

## v2.0 features

| Area | What it does |
|---|---|
| **Studio** | Unlimited recording, neon waveform + level meter, **Male / Female / Original** voices, strength / pitch / mic-boost sliders, **noise gate** and **echo** toggles, instant preview, save, share WAV, cloud presets. |
| **Live** | Real-time morphing (~90 ms blocks): mic → WSOLA pitch shift + shelf-EQ → low-latency monitor. Works on speaker *and* headphones automatically. |
| **Game HUD** | Floating neon bubble on top of PUBG / Free Fire / COD Mobile and every app (`SYSTEM_ALERT_WINDOW` + foreground service). **Blue = male, pink = female.** Drag anywhere, tap = live monitor on/off, long-press = quick panel (voice switch, monitor, broadcast last clip). |
| **Game broadcast** | Clip soundboard with 3-second countdown and max-volume speaker output the game mic picks up. **Force-speaker routing** bypasses headset routing so broadcasts work even with headphones plugged in. |
| **Backend** | FastAPI landing page + secured preset API (token auth, per-IP rate limits, strict validation, security headers, HTTPS-ready). |

### The DSP (`app/src/com/voicefilter/app/VoiceEngine.java`)

noise gate → pre-gain → linear resample (pitch bend) → **WSOLA** time-scale (restore
duration, keep new pitch) → RBJ shelving EQ (male/female timbre) → feedback echo →
peak normalise. Desktop-JVM unit test (`tools/run_dsp_test.sh`): 160 Hz test voice →
122 Hz male / 207 Hz female (targets 124.8 / 208), duration preserved, 20–60× real time.

## The truth about "voice changers that work in game"

Android grants the microphone to **one app at a time** — the game in front — and has
**no virtual-microphone API**. Without root, *no* app can inject audio into PUBG's voice
chat; apps claiming otherwise are fake. Voice Filter never injects anything. Instead:

1. the **HUD overlay** keeps controls on top of the game (this is what "runs in
   background on PUBG" honestly means on non-rooted Android);
2. **broadcast** plays your filtered clip at max volume into the game mic — with
   force-speaker routing this works with or without headphones;
3. **live monitoring** runs whenever Voice Filter is the foreground app.

Root-level audio hooks are deliberately avoided: they trip anti-cheat and get bans.

## Backend (secured)

```bash
pip install -r backend/requirements.txt
VOICEFILTER_TOKEN=s3cret uvicorn main:app --host 0.0.0.0 --port 8000   # from backend/
```

* `GET /` landing · `GET /download` APK · `GET /api/health` · `GET /api/version`
* `GET /api/presets` public · `POST /api/presets` requires `X-VoiceFilter-Token`
  when `VOICEFILTER_TOKEN` is set (otherwise open but rate-limited to 5 writes/min/IP)
* global 120 req/min/IP sliding window, pydantic + regex sanitising, bounded store,
  `nosniff` / `DENY` / `no-referrer` / CSP headers on every response
* in-app: **Studio → Cloud presets** — paste the server URL and (optionally) the token

## Building the APK yourself

Toolchain (GitHub/PyPI mirrors only, no `dl.google.com`):

| Piece | Source |
|---|---|
| JDK 17 | `msft-mirror-aosp/platform.prebuilts.jdk.jdk17` (git sparse clone) |
| `android.jar` API 34 | `msft-mirror-aosp/platform.prebuilts.sdk` |
| D8/R8 | `LineageOS/android_prebuilts_r8` (`r8-master.jar`) |
| `aapt2` | npm `aaptjs3` |
| v2 verify | PyPI `apksigtool` |

```bash
bash tools/build_apk.sh        # compile → dex → pack → v1 sign → align → v2 sign
bash tools/run_dsp_test.sh     # desktop DSP unit test
```

Signing identity in `keys/` (RSA-2048 self-signed demo). Verification performed on the
shipped APK: `apksigtool verify` → v2 verified · `jarsigner -verify` → jar verified ·
`aapt2 dump badging` + androguard parse manifest, resources and dex cleanly.

## Layout

```
app/            manifest, resources, Java sources (com.voicefilter.app)
backend/        FastAPI landing + secured preset API
tools/          build scripts (pack/align/v2-sign/key-gen/icons) + DSP test
keys/           demo signing identity
apk/            signed release artifact
assets_src/     generated source artwork (HD logo, hero)
```

## Disclaimer

Broadcasting a filtered voice is fine; impersonating a real person or evading a game
ban is not. Voice Filter never touches game files or processes.
