# Engineering, Privacy & Safety Rules (`RULES.md`)

All code, assets, and releases of **NoFilter Ai** strictly adhere to the following rules:

## 1. Zero-API-Key & Zero-Paywall Rule
- **NEVER** require an API key, OAuth token, credit card, or user login for any feature (chat, web research, math, code, personas, memory, voice, or OCR).
- **NEVER** embed hard-coded third-party secret keys inside the APK that could expire, leak, or hit rate limits.
- Live internet research must rely exclusively on open, public web endpoints (`api.duckduckgo.com`, `html.duckduckgo.com`, `en.wikipedia.org/w/api.php`, `en.wikipedia.org/api/rest_v1`, and standard public web pages) with graceful fallbacks when offline.

## 2. Local-First Privacy & Security Rules
- **100% On-Device Persistence:** All conversations, messages, long-term memories, custom personas, and preferences must remain in the app's private sandbox (`SQLite` + `SharedPreferences`) unless explicitly exported by the user.
- **Zero Telemetry:** No analytics SDKs, ad networks, crash-reporting beacons, or hidden background trackers are permitted.
- **Strict Loopback Isolation for OCR:** `AssetServer` (`ocr/AssetServer.kt`) must bind exclusively to `InetAddress.getByName("127.0.0.1")` on an ephemeral port (`0`), sanitize `/tmp/` filenames against path traversal (`[A-Za-z0-9._-]+`, rejecting `..`), and keep `WebView` `allowFileAccess` and `allowContentAccess` set to `false`.
- **Strict Offline Mode:** When `Prefs.offlineMode == true`, `Ensemble.kt` must never initiate outbound network requests; all offline experts (`Knowledge`, `MathSolver`, `CodeAssist`, `Reasoner`, `PersonaMemory`, and `OcrEngine`) must continue functioning at 100%.

## 3. Uncensored Openness with Minimal Harm Guardrails
- NoFilter Ai answers philosophical, technical, creative, controversial, and everyday questions openly without preachy lecturing or arbitrary topic blocks.
- Only narrow, high-severity real-world harm categories defined in `engine/Safety.kt` (improvised explosives/weapons, CSAM, hard-drug synthesis, malware/ransomware creation, violent harm, suicide instruction, illegal arms trafficking) are declined—and when declined, the response remains brief, respectful, and offers legitimate defensive/educational context where appropriate.

## 4. Android Quality & Build Integrity Rules
- **No Main-Thread Networking or Heavy I/O:** All HTTP requests, HTML parsing, and ensemble ranking run on background worker threads; UI mutations post exclusively through `Looper.getMainLooper()`.
- **Bitmap Memory Safety:** Camera and gallery photos passed to `OcrEngine` must be bounds-checked (`inJustDecodeBounds = true`) and downsampled (`inSampleSize`) before decoding to prevent `OutOfMemoryError` on mid-range devices.
- **Broad Device Compatibility:** Hardware features (`android.hardware.microphone`, `android.hardware.camera`) must declare `android:required="false"` in `AndroidManifest.xml` so tablets, emulators, and devices without cameras/mics can install the APK without restriction.
- **Verified Release Artifacts:** Every `.apk` shipped to GitHub Releases must pass:
  1. `aapt2 link` with zero resource errors
  2. `kotlinc` & `D8` with zero compilation errors
  3. 4-byte alignment check on all `ZIP_STORED` entries (`resources.arsc`, `.png`, `.wasm`, `.traineddata`)
  4. `apksigner verify --verbose` confirming APK Signature Scheme v2 and v3 validity.
