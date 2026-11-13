# Persistent Memory & Persona Training Specification (`MEMORY.md`)

## 1. Dual Memory Architecture
This file documents **both**:
1. **In-App User Memory & Persona Engine:** How NoFilter Ai memorizes user facts forever and learns custom behavior on Android.
2. **Project Engineering Memory:** Key architectural decisions, verified asset hashes, and build invariants for maintainers of this repository.

---

## 2. In-App Long-Term Memory Engine (`db/Db.kt` & `engine/PersonaMemory.kt`)

### SQLite Schema
Stored in `/data/data/com.nofilter.ai/databases/nofilter.db` (version `1`):
```sql
CREATE TABLE memories(
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    text TEXT,
    tags TEXT DEFAULT '',
    ts   INTEGER,
    hits INTEGER DEFAULT 0
);

CREATE TABLE personas(
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT,
    emoji        TEXT DEFAULT '🤖',
    instructions TEXT DEFAULT '',
    tone         TEXT DEFAULT 'Balanced',
    is_custom    INTEGER DEFAULT 0,
    ts           INTEGER
);
```

### Three Ways NoFilter Ai Memorizes Anything
1. **Explicit Voice / Text Commands:**
   - `"Remember that I am a Kotlin developer in Karachi"`
   - `"Memorize: my server IP is 10.0.4.22"`
   - `"Don't forget my exam is on Friday"`
   - `"/remember I prefer concise bullet points"`
   → Saved with tag `"explicit"` and confirmed immediately.
2. **Implicit Fact Learning During Normal Chat:**
   - Statements matching `"my name is …"`, `"i live in …"`, `"i work as …"`, `"my favorite …"`, or `"i prefer …"` are automatically captured with tag `"auto"` while still answering the user's question, appending a subtle `*(💾 Auto-saved to memory)*` note.
3. **One-Tap Message Pinning & Memory Vault:**
   - Long-pressing any user or AI message in `ChatActivity` offers **⭐ Memorize this**.
   - `MemoryActivity` allows adding, editing, and deleting memories manually at any time.

### Retrieval & Hit Tracking
On every query:
- If the user asks *"What do you remember?"*, *"What is my name?"*, *"Who am I?"*, or `/memories`, up to 8 recent memories are returned directly.
- Otherwise, `PersonaMemory.matchMemories` tokenizes the query (filtering 65+ English stopwords) and computes token-overlap + tag-match scores against all stored memories, returning the top 3 above threshold `0.12`, incrementing `hits = hits + 1` via `db.bumpMemory(id)`, and weaving recalled facts into the synthesized ensemble response.

### Trainable Personas ("How to Behave")
- **10 Pre-Seeded Personas:** *General, Boss, Teacher, Coder, Researcher, Creative, Friend, Poet, ELI5, Concise*.
- **Custom Training:** Users define a custom **Name**, **Emoji**, free-text **Behavior Instructions**, and **Tone** (*Balanced, Formal, Casual, Friendly, Technical, Playful, Poetic, Concise*).
- **Runtime Application:** `PersonaMemory.applyTone` enforces brevity caps (`Concise`), strips emojis when `"no emoji"` is instructed, frames executive headers (`Boss`), simplifies framing (`ELI5`), or injects expressive warmth (`Playful` / `Friend`).

---

## 3. Project Engineering Memory (Maintainer Reference)
- **Application ID & Version:** `com.nofilter.ai`, `versionCode = 1`, `versionName = "1.0.0"`, `minSdk = 24`, `targetSdk = 34`.
- **Bundled Offline OCR Assets (`app/src/main/assets/web/`):**
  - `tesseract.min.js` & `worker.min.js` — Tesseract.js v7 browser/worker runtime
  - `core/tesseract-core-lstm.wasm` & `.wasm.js` — WebAssembly LSTM recognition core
  - `lang/eng.traineddata` — `tesseract-ocr/tessdata_best` English neural model (`15,400,601` bytes, blob SHA `176dc3220de7db34d3b3aecbfa42043a6038348b`), served uncompressed (`gzip: false`, `cacheMethod: 'none'`) over `127.0.0.1` loopback HTTP (`AssetServer.kt`).
- **APK Packaging Invariants:**
  - Android 11+ (`targetSdk >= 30`) mandates that `resources.arsc` be stored **uncompressed (`ZIP_STORED`)** and **4-byte aligned**. `tools/build_apk.py` enforces `-0 arsc -0 wasm -0 traineddata` during `aapt2 link` and verifies 4-byte alignment of every `ZIP_STORED` entry before signing with APK Signature Scheme v2 & v3.
