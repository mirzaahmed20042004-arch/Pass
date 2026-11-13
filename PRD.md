# Product Requirements Document (PRD) — NoFilter Ai

## 1. Product Vision
**NoFilter Ai** (`com.nofilter.ai`) is a full-stack, native Android AI assistant designed to remove every artificial paywall, token quota, sign-up gate, and API-key requirement that restricts mainstream AI chatbots. Instead of routing user conversations through a paid cloud LLM API, NoFilter Ai runs a **6-expert Mixture-of-Experts (MoE) Ensemble** directly on the user's Android device—combining live multi-source internet research, an offline TF-IDF knowledge base, an exact symbolic math & unit solver, a code generator & stack-trace debugger, an analytical/creative reasoner, persistent long-term SQLite memory, trainable behavioral personas, voice I/O, and bundled offline **Tesseract LSTM WebAssembly OCR**.

---

## 2. Goals & Non-Goals

### Primary Goals
1. **Zero API Keys, Zero Subscriptions, Unlimited Usage:** Every feature works out-of-the-box upon installing the `.apk` with no account registration, no billing, and no API keys.
2. **Multi-Expert Ensemble Intelligence:** Combine specialized local engines and live open-web retrieval, rank candidate answers by confidence, and synthesize grounded, cited responses.
3. **Trainable Behavior (Personas):** Let users train how the AI behaves—choosing from 10 built-in personas (*General, Boss, Teacher, Coder, Researcher, Creative, Friend, Poet, ELI5, Concise*) or authoring custom personas with bespoke instructions and tone.
4. **Infinite Persistent Memory:** Automatically learn user facts (`"Remember that…"`, `"My name is…"`, `"I prefer…"`) or save any message with one tap into an on-device SQLite memory vault that enriches future responses.
5. **Live Internet Research with Citations:** Fetch and synthesize live information from DuckDuckGo Instant Answers, DuckDuckGo HTML/Lite search, Bing fallback, Wikipedia REST APIs, and top result web pages with clickable source citations.
6. **Best-in-Class Offline OCR (No API Key):** Bundle `tesseract-core-lstm.wasm` and `tessdata_best` English neural LSTM weights (`eng.traineddata`) inside the APK so users can extract text from gallery images or camera photos completely offline.
7. **Full User Ownership & Portability:** Provide 1-click JSON backup/restore of all chats, memories, and custom personas, plus per-chat Markdown export, dark/light themes, adjustable typography, voice input (STT), and read-aloud (TTS).

### Non-Goals
- Hosting a multi-gigabyte raw GGUF weight download on first launch (the app must work immediately after installing the ~20 MB APK).
- Cloud account syncing that exposes private user chats or memories to third-party servers.
- Facilitating severe real-world harm (weapons/explosives, CSAM, malware creation, self-harm—guarded by a minimal, transparent on-device safety check).

---

## 3. Target Audience & Use Cases
- **Students & Self-Learners:** Step-by-step explanations (*Teacher / ELI5* personas), math/unit solving, offline science/CS reference, and scanning textbook/whiteboard photos via offline OCR.
- **Developers & Engineers:** Instant code templates, stack-trace debugging (*Coder* persona), regex/SQL/HTTP references, and cited technical web lookups.
- **Professionals & Researchers:** Executive-briefing formatting (*Boss / Researcher* personas), multi-source live web synthesis, summarization of pasted text, and Markdown chat export.
- **Privacy-Conscious & Offline Users:** 100% local SQLite storage, explicit Offline Mode toggle, and zero telemetry.

---

## 4. Feature Matrix

| Capability | NoFilter Ai | Typical Freemium AI Apps |
| :--- | :--- | :--- |
| API Key / Login Required | **None** | Account + paid tier / API key |
| Message / Token Limits | **Unlimited** | Daily caps & rate limits |
| Multi-Expert Ensemble | **6 local + web experts combined** | Single black-box model |
| Live Web Research + Citations | **Built-in, toggleable per chat** | Often paywalled |
| Trainable Personas | **10 built-in + unlimited custom** | Limited custom instructions |
| Persistent Long-Term Memory | **Auto-learned + manual vault + hit tracking** | Capped or opaque |
| Offline Neural OCR | **Bundled Tesseract LSTM WASM (`tessdata_best`)** | Cloud vision API / quota-limited |
| Voice Input & Read-Aloud | **Android STT + TTS built-in** | Varies |
| Full JSON Export / Import | **100% local & portable** | Locked in cloud |

---

## 5. Technical & Platform Requirements
- **Package ID:** `com.nofilter.ai`
- **Minimum SDK:** Android 7.0 Nougat (`API 24`)
- **Target / Compile SDK:** Android 14 (`API 34`)
- **Language:** 100% Kotlin + Android platform views + embedded WebAssembly OCR worker
- **Signing:** APK Signature Scheme v2 & v3, 4-byte aligned uncompressed `resources.arsc` and binary assets
