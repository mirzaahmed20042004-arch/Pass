# System Architecture — NoFilter Ai (`ARCHITECTURE.md`)

## 1. High-Level Overview
NoFilter Ai is architected as a **zero-dependency, local-first native Android application** written in Kotlin. Rather than relying on heavy third-party frameworks or remote inference servers, every subsystem—UI rendering, Markdown formatting, SQLite persistence, HTTP networking, HTML extraction, ensemble ranking, and loopback WebAssembly OCR—is implemented directly atop the Android SDK (`API 24–34`) and bundled assets.

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         Presentation Layer (Activities)                   │
│  SplashActivity ─► MainActivity ─► ChatActivity                           │
│                         ├──► OcrActivity (Gallery / Camera ─► OCR)        │
│                         ├──► MemoryActivity (Long-Term Memory Vault)      │
│                         ├──► PersonasActivity (Behavior Training Studio)  │
│                         └──► SettingsActivity (Theme, Offline, Backup)    │
└───────────────┬───────────────────────────────────────────┬───────────────┘
                │                                           │
                ▼                                           ▼
┌───────────────────────────────────────┐   ┌───────────────────────────────┐
│     Mixture-of-Experts Ensemble       │   │     On-Device OCR Subsystem   │
│         (engine/Ensemble.kt)          │   │       (ocr/OcrEngine.kt)      │
│                                       │   │                               │
│  1. Safety.kt      (harm guardrails)  │   │  Hidden WebView               │
│  2. PersonaMemory  (auto-learn/recall)│   │       │  JS Bridge (window.NF)│
│  3. MathSolver.kt  (AST math/units)   │   │       ▼                       │
│  4. Knowledge.kt   (TF-IDF kb.json)   │   │  AssetServer (127.0.0.1:ephem)│
│  5. CodeAssist.kt  (codegen/debugger) │   │   ├─ /tesseract.min.js        │
│  6. Reasoner.kt    (plans/TLDR/poem)  │   │   ├─ /worker.min.js           │
│  7. WebResearch.kt (DDG + Wiki + Web) │   │   ├─ /core/*-lstm.wasm(.js)   │
│                                       │   │   ├─ /lang/eng.traineddata    │
│  ─► Confidence Scorer & Combiner      │   │   └─ /tmp/scan_*.jpg          │
│  ─► Persona Tone Post-Processor       │   └───────────────────────────────┘
│  ─► Typewriter UI Streamer            │
└───────────────┬───────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     Persistence & Utility Layer                           │
│  • db/Db.kt        — SQLiteOpenHelper (`nofilter.db`: chats, messages,    │
│                      memories, personas + JSON export/import)             │
│  • util/Prefs.kt   — SharedPreferences (`nofilter_prefs`)                 │
│  • util/Http.kt    — Redirect-aware, gzip-capable HttpURLConnection       │
│  • util/Html2Text  — Entity decoding, boilerplate stripping, sentence rank│
│  • util/Markdown   — Spannable renderer (headings, code fences, links)    │
│  • util/Speech.kt  — Android RecognizerIntent (STT) & TextToSpeech (TTS)  │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Package & Module Breakdown

### `com.nofilter.ai` (Activities)
- **`BaseActivity.kt`** — Initializes `Prefs` and applies the active theme (`Theme.NoFilter` dark or `Theme.NoFilter.Light`) prior to `super.onCreate`.
- **`SplashActivity.kt`** — Branded launch screen displaying the conceptual neural-aperture emblem and transitioning smoothly into `MainActivity`.
- **`MainActivity.kt`** — Home hub listing all conversations ordered by `updated DESC`, instant full-text search across chat titles and message bodies, long-press rename/delete, and top-bar navigation to OCR Studio, Memory Vault, Personas Studio, and Settings.
- **`ChatActivity.kt`** — Real-time streaming chat interface with per-chat Web Research toggle, Persona switcher, Sources inspector, Starter Prompts & Markdown chat export, voice dictation (STT), image attachment → auto-OCR pipeline, and long-press message actions (*Copy, Share, Read aloud, Memorize, Regenerate, View sources*).
- **`OcrActivity.kt`** — Standalone & intent-callable OCR Studio supporting gallery image picking, live camera capture, automatic bitmap downsampling (`maxDim = 2200px`), live progress bar, confidence reporting, copy, share, and direct text return to `ChatActivity`.
- **`MemoryActivity.kt`** — CRUD interface for user memories with creation timestamps and recall hit counters.
- **`PersonasActivity.kt`** — Behavior training studio pre-seeded with 10 personas plus custom persona creation, duplication, editing, tone selection, and default assignment.
- **`SettingsActivity.kt`** — Dynamic settings screen for Dark/Light theme, chat font scaling (`85%–130%`), default Web Research, strict Offline Mode, full JSON export/import via Android Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`), and data reset.

### `com.nofilter.ai.engine` (Mixture-of-Experts Core)
- **`Ensemble.kt`** — Background thread-pool orchestrator (`Executors.newCachedThreadPool()`). Evaluates slash commands, safety checks, auto-memory learning, and dispatches the query across all active experts. Scores each `Candidate(expert, icon, text, score, sources)`, selects the highest-confidence lead answer, merges non-redundant insights from secondary experts (`score >= 0.58`), attaches recalled memories and deduplicated source citations, applies `PersonaMemory.applyTone`, and streams chunks onto the main thread (`Handler(Looper.getMainLooper())`).
- **`WebResearch.kt`** — Keyless multi-stage web researcher:
  1. Queries DuckDuckGo Instant Answer JSON API (`api.duckduckgo.com`).
  2. Scrapes DuckDuckGo HTML/Lite search results (`html.duckduckgo.com` / `lite.duckduckgo.com`) with automatic Bing HTML fallback (`www.bing.com`).
  3. Queries Wikipedia's open search & REST summary APIs (`en.wikipedia.org/api/rest_v1/page/summary/...`).
  4. Fetches up to 3–5 top web pages concurrently/sequentially (skipping heavy video/social walls), strips HTML via `Html2Text`, extracts the highest query-overlap sentences, and builds numbered `[1]`, `[2]` citations.
- **`Knowledge.kt`** — Loads `assets/web/kb.json` once into memory, tokenizes keywords and answers, computes inverse document frequency (`IDF = ln((N + 1)/(df + 1)) + 1`), and scores queries with phrase bonuses and length normalization.
- **`MathSolver.kt`** — Recursive-descent mathematical expression parser (`+`, `-`, `*`, `/`, `%`, `^`, parentheses, `sqrt`, `sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `log`, `ln`, `log2`, `abs`, `exp`, `floor`, `ceil`, `round`, constants `pi`, `e`, `tau`), percentage solver, unit converter (length, mass, volume, data, temperature `°C/°F/K`), and date-offset calculator.
- **`CodeAssist.kt`** — Recognizes programming languages, diagnoses 12+ common runtime/compiler error families from stack traces, and synthesizes clean reference implementations for common algorithmic and networking tasks.
- **`Reasoner.kt`** — Extractive multi-sentence summarizer (`summarize: ...`), word/character/reading-time analyzer, `X vs Y` comparator, pros/cons matrix builder, 4-phase learning roadmap generator, and creative haiku/poem/micro-story writer.
- **`PersonaMemory.kt`** — Regex-based explicit & implicit memory extractor, token-overlap memory retriever with hit-counter bumping, and persona tone post-processor.
- **`Safety.kt`** — Lightweight regex guardrail declining severe harm (explosives, CSAM, hard-drug synthesis, malware authoring, murder/self-harm, illegal weapons) while keeping general discussion uncensored.

### `com.nofilter.ai.ocr` (Loopback WebAssembly OCR)
- **`AssetServer.kt`** — Daemon `ServerSocket` bound strictly to `127.0.0.1:0` (ephemeral local port, unreachable from outside the device). Serves bundled `assets/web/*` files with exact MIME types (`application/wasm`, `application/javascript`, etc.) and sanitized temporary scan images from `/tmp/<filename>`.
- **`OcrEngine.kt` & `assets/web/ocr.html`** — Configures an invisible `1dp × 1dp` `WebView` (`allowFileAccess = false`, `allowContentAccess = false`), downscales oversized camera photos to `≤ 2200px` JPEG for fast inference, invokes `window.nfOcr(url, lang)` over `evaluateJavascript`, and receives progress/confidence/text callbacks via `@JavascriptInterface` (`NF`). Inside the WebWorker, Tesseract.js v7 loads `/core/tesseract-core-lstm.wasm` and `/lang/eng.traineddata` (`tessdata_best`, 15.4 MB) completely offline.

---

## 3. Reproducible Build Pipeline (`tools/build_apk.py`)
In addition to standard Gradle configuration (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`), the repository includes a self-contained deterministic build script (`tools/build_apk.py`):
1. **`aapt2 compile` & `aapt2 link`** — Compiles all vector drawables, layouts, themes, mipmaps, and uncompressed `.arsc`/`.wasm`/`.traineddata` assets against `android-34.jar` and generates `R.java`.
2. **`R.java` → `R.kt` + `kotlinc-jvm`** — Translates resource constants to Kotlin and compiles all 27 `.kt` files to JVM bytecode (`classes.jar`).
3. **`D8` (`com.android.tools.r8.D8`)** — Desugars and compiles `classes.jar` + `kotlin-stdlib.jar` into `classes.dex` (`--min-api 24 --release`).
4. **4-Byte Zipalign** — Packs `classes.dex` and linked resources while padding ZIP local-header `extra` fields so every `ZIP_STORED` entry (`resources.arsc`, PNGs, `.wasm`, `.traineddata`) starts on an exact 4-byte boundary as required by Android 11–15+.
5. **`keytool` + `apksigner`** — Signs the aligned archive with APK Signature Scheme v2 & v3 and verifies cryptographic integrity via `apksigner verify --verbose` and `aapt2 dump badging`.
