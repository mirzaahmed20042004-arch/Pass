# Architecture

## Overview

Ultron Ai is a small native Android shell around bundled, offline HTML/CSS/JavaScript. Android's private WebView storage holds user data; native components implement capabilities the web layer cannot. No third-party analytics, cloud account, or model weights are included.

```
Bundled UI / localStorage
  ├─ local command parser + agent prompt profiles
  ├─ Android JavaScript bridge (bundled page only)
  │   ├─ Android intents: app launcher, browser, SMS draft, share sheet
  │   ├─ Android speech recognizer UI + TextToSpeech
  │   ├─ opt-in foreground overlay HUD
  │   ├─ opt-in accessibility service: snapshot / one supervised tap
  │   └─ ModelClient ── HTTP(S) ── Ollama / optional gateway ── Ollama
  └─ Android private local data, backup disabled
```

`MainActivity` only loads packaged assets and rejects remote navigation inside the bridge-enabled WebView. The UI has a restrictive content security policy, escapes user/model text, and does not perform browser-side network requests. The bridge runs model HTTP calls off the UI thread, limits payload/response sizes, rejects embedded URL credentials and redirects, and does not forward model output as commands.

## AI

`core.js` defines four instruction profiles: assistant, coding, research, and creative. They are not separate model installations. A default model and optional profile-specific model names are configured by the user. The app sends the profile's system instruction and at most eight model-context messages (truncated to 4,000 characters each) to `/api/chat` with `stream: false`. Notes, screen snapshots and local action messages are not added to model context. `/api/tags` checks connectivity and installed model names. The configured server processes model prompts; inference is not bundled or guaranteed offline.

`backend/server.py` is an optional dependency-free, Ollama-compatible proxy. It binds to loopback by default and requires a token for non-loopback binds; it validates inputs, strips tool definitions, limits request/response sizes, refuses upstream redirects, and stores no prompts. It is not an internet-hardened server or TLS terminator.

## Android features

- `MainActivity`: local WebView, intent launching, speech, TTS, runtime permission requests, model networking, and callbacks.
- `HudService`: user-started foreground service with draggable overlay, persistent stop notification where notifications are allowed, and long-press snapshot shortcut.
- `AssistService`: discards events; on demand it traverses the visible accessibility tree with limits, skips password nodes, and returns a local snapshot. A separate explicit action attempts a single unique exact-label tap, rejects system packages, editable/password nodes and some sensitive labels, and does not run on model output. It cannot guarantee that a control is safe merely from its label.

Android owns and enforces all permissions. Screen content is never continuously recorded, but granting accessibility provides the service broad OS-level capability; users can revoke it any time.

## Build and test

`app/build.gradle` is the standard Android project. `scripts/build-apk.sh` provides an alternative SDK-tools-only debug-key-signed APK build. Tests cover pure command parsing, the real HTTP gateway with a fake Ollama upstream, browser UI interactions with a mocked Android bridge, and pure-Java model-client and safety-filter tests against a local HTTP server. The exact APK has also been smoke-tested on an Android 10 emulator for launch, local note persistence, and HUD start/stop, with overlay permission granted by the test. Permission flows, voice, accessibility, model connections, and device-specific behavior still need hands-on testing; this environment did not provide a physical Android device.
