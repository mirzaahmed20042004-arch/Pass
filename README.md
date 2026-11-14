# VoiceMorph — real male & female voice changer for Android (+ game soundboard)

A full-stack Android voice changer built **from source with no Android Studio and no
Gradle**: `aapt2` → `javac` → `D8` → hand-rolled zip packaging → `jarsigner` (v1) →
custom zipalign → custom **APK Signature Scheme v2** signer (verified with
`apksigtool`). The signed APK lives in [`apk/VoiceMorph-v1.0.apk`](apk/VoiceMorph-v1.0.apk).

* Package: `com.voicemorph.app` · minSdk 24 (Android 7.0) · targetSdk 29 · ~130 KB
* Zero third-party runtime libraries — pure Android framework + pure-Java DSP.

## Features

| Tab | What it does |
|---|---|
| **Studio** | Record a take (44.1 kHz PCM), morph it to **Male** or **Female** (or keep Original), adjustable effect strength, instant preview, save to library, share as WAV. Optional **cloud presets** fetched from the backend (`GET /api/presets`). |
| **Live** | Real-time voice changer: mic → WSOLA pitch shift + shelf-EQ → low-latency `AudioTrack` monitor (~90 ms blocks). |
| **Game** | Clip soundboard for PUBG Mobile / Free Fire / COD Mobile: 3-second countdown, then the morphed clip is broadcast through the speaker at max volume so the game microphone picks it up. |

### The DSP (`app/src/com/voicemorph/app/VoiceEngine.java`)

1. **Linear resample** by the pitch factor (bends pitch *and* formants).
2. **WSOLA** (waveform-similar overlap-add, 2048-sample Hann windows, ±128-sample
   similarity search) restores the original duration while keeping the new pitch.
3. **RBJ shelving EQ** tilts the timbre: male = +4.5 dB low shelf @180 Hz / −4 dB high
   shelf @3.5 kHz, female = the mirror image.
4. Peak normalisation to −1 dBFS.

Unit-tested on the desktop JVM (`tools/run_dsp_test.sh`): a 160 Hz harmonic voice-like
signal lands at 122 Hz for male (target 124.8) and 207 Hz for female (target 208),
duration preserved within 0.1 %, ~20–60× faster than real time.

## The truth about "voice changers that work in game"

Android grants the microphone to **one app at a time** — the game in the foreground —
and Android has **no virtual-microphone API**. Without root, *no* app can inject a
changed voice into PUBG's voice chat in real time; store apps claiming that are fake or
use the speaker trick. VoiceMorph does the speaker trick properly and tells you so
in-app:

1. Record + morph your line in **Studio** and save it.
2. Join voice chat in your game.
3. Hit **Broadcast** — countdown, max volume, the game mic hears your new voice.

**Live** mode morphs in real time while VoiceMorph itself is the foreground app (Android
silences background capture as soon as a game grabs the mic). Root-level audio hooks are
deliberately *not* used: they break game anti-cheat rules and can get accounts banned.

## Backend (full stack)

`backend/` is a FastAPI service: landing page with the APK download button, and the
preset API the app consumes.

```bash
pip install -r backend/requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000   # from backend/
```

* `GET /` – landing page · `GET /download` – the APK
* `GET /api/presets`, `POST /api/presets` – community voice recipes
  (`{name, author, pitch, strength, low_db, low_hz, high_db, high_hz}`)

In the app: **Studio → Cloud presets**, paste this server's URL.

## Building the APK yourself

The toolchain is fetched from GitHub/PyPI mirrors only (no `dl.google.com` needed):

| Piece | Source |
|---|---|
| JDK 17 (`javac`, `jar`, `jarsigner`, `keytool`) | `msft-mirror-aosp/platform.prebuilts.jdk.jdk17` (git sparse clone) |
| `android.jar` (API 34) | `msft-mirror-aosp/platform.prebuilts.sdk` (`34/public/android.jar`) |
| D8/R8 dexer | `LineageOS/android_prebuilts_r8` (`r8-master.jar`) |
| `aapt2` | npm package `aaptjs3` (prebuilt linux binary) |
| v2 verification | PyPI `apksigtool` |

Layout (put them under `$TOOLS_DIR`, default `/home/user/tools` or `../tools`):

```
tools/jdk17/linux-x86/   tools/android.jar   tools/r8-master.jar
tools/aapt2              tools/venv/         (python venv: cryptography, apksigtool…)
```

Then:

```bash
bash tools/build_apk.sh        # compile → dex → pack → v1 sign → align → v2 sign
bash tools/run_dsp_test.sh     # desktop DSP unit test
```

Signing identity: `keys/` (RSA-2048, self-signed "VoiceMorph Demo Signer", PKCS#8 +
PKCS#12). Replace with your own keystore for production releases.

### Verification performed on the shipped APK

* `apksigtool verify` → **v2 verified**
* `jarsigner -verify` → **jar verified** (v1)
* `aapt2 dump badging` → package/permissions/labels as expected
* `androguard` → manifest, resources and all 60 dex classes parse cleanly

## Repository layout

```
app/            Android app: manifest, resources, Java sources
backend/        FastAPI landing page + preset API
tools/          build scripts (pack/align/v2-sign/key-gen/icons) + DSP unit test
keys/           demo signing identity
apk/            signed release artifact
assets_src/     generated source artwork (icon, hero)
```

## Disclaimer

Use responsibly: broadcasting a changed voice is fine, but impersonating a real person
or evading a game ban is not. VoiceMorph never touches game files or processes.
