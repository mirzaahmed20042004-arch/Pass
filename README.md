# Ultron Ai

A local-first Android assistant prototype with a floating HUD, voice input and replies, local notes, supervised Android actions, and configurable Ollama-compatible AI. The original conceptual logo is in [`assets/ultron-logo.png`](assets/ultron-logo.png).

## Download and install

**[Download Ultron Ai v0.1.0 APK](https://github.com/mirzaahmed20042004-arch/Pass/raw/refs/heads/arena/77e09c51-pass/releases/Ultron-Ai-v0.1.0.apk)**

1. Download the APK on your Android phone and open it. If Android asks, allow that browser or file manager to install unknown apps, then install Ultron Ai.
2. Open the app. Notes and the built-in commands work without connecting an AI model. Try **“remember buy milk”**, **“open Camera”**, or **“search the web for astronomy”**.
3. For open-ended chat and coding help, connect your own Ollama-compatible server in **Settings** (instructions below). No model is bundled in the APK.
4. To use the floating HUD, choose **Set up HUD** and grant Android's overlay permission. Tap the bubble to return to Ultron; long-press it while another app is visible to capture one accessible-text snapshot. Screen reading also requires enabling **Ultron Ai screen assistance** in Android Accessibility settings.

The APK is signed with a **development/debug key**, not a production release key. Treat it as a prototype. A rebuild signed with a different key cannot update this installation; uninstalling first will delete its local data.

### Compatibility and limitations

- Targets Android **5.0 / API 21 and newer** with an up-to-date Android System WebView; speech, overlay and accessibility behavior varies by phone and OS. It cannot support every historical Android version or bypass Android's restrictions. The APK has been build- and signature-verified and smoke-tested for launch and dashboard rendering on an Android 10 emulator; it has **not** been tested on a physical Android device in this environment.
- Press-to-talk uses the phone's speech recognition service; spoken replies use Android text-to-speech. The speech provider may require internet and may process audio externally. Continuous conversation is optional and runs only while the app is open; there is no wake-word or background listening.
- The HUD reads accessibility labels and content descriptions, **not live video, pixels, or every screen**. Password fields are skipped, but other exposed text may still be sensitive. A snapshot stays in local chat and is not automatically included in model requests.
- The supervised tap action attempts a single exact, unique accessible label after a five-second countdown. It blocks some sensitive labels and system screens, but cannot understand the consequences of every button. Verify the target and avoid sensitive operations. Some apps expose no usable controls.
- Messages open in a messaging app as **drafts**; you select/verify the recipient and send manually. Sharing uses Android's share sheet. No social accounts or customers are connected automatically, no posts or messages are sent silently, and there is no unrestricted cross-app control.
- The four agents are prompt profiles, not four bundled models. They can use different models that you have already installed on your server. Model quality, licensing, hardware use, and provider costs depend on your choices. The app has no subscription, but unlimited inference or free third-party services cannot be guaranteed.
- Local notes, chat, configuration, and an optional gateway token are stored in app-private WebView storage **without application-level encryption**. Uninstalling or clearing data removes them; app backup is disabled. Use the Settings page to clear local data. Disable accessibility/overlay permissions in Android settings separately.

## Connect an open model

The app implements Ollama's non-streaming `/api/chat` and `/api/tags` APIs. You need an accessible Ollama server with your chosen compatible model already pulled. For example, on your own computer:

```sh
ollama pull qwen2.5:3b
# Ensure Ollama is running on the computer, normally at 127.0.0.1:11434.
```

An optional Python standard-library gateway is included. By default it listens only on the computer's loopback interface. To let a phone on a **trusted LAN** connect, set a strong token, bind the gateway, and allow access in your firewall:

```sh
export ULTRON_GATEWAY_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
python3 backend/server.py --host 0.0.0.0 --port 8765
```

In the Android app's **Settings**, enter `http://YOUR_COMPUTER_LAN_IP:8765`, the installed model name (e.g. `qwen2.5:3b`), and the token. Save and test the connection. The phone and computer must be able to reach one another; `localhost` on the phone is the phone itself, not your computer. Alternatively, connect directly to a properly configured Ollama server.

**Security:** The gateway's bearer token controls access but does not encrypt traffic, implement rate limiting, or make it safe to expose publicly. Plain HTTP sends prompts, replies, and the token in cleartext. Use a trusted network, firewall, and HTTPS reverse proxy or a secure tunnel when appropriate. Ollama itself generally has no built-in authentication; do not expose it openly. Configured model prompts and recent model-chat messages are sent to the chosen server. Android's speech provider and the browser have separate privacy policies.

## Development

Open this directory in Android Studio with JDK 17 and Android SDK platform/build tools 34 installed, then run the `app` configuration. Or use the Gradle wrapper:

```sh
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

A dependency-free command-line build is also available on Linux when JDK 17, `platforms/android-34/android.jar`, and build-tools 34.0.0 are installed:

```sh
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk
./scripts/build-apk.sh
```

It writes `releases/Ultron-Ai-v0.1.0.apk` and signs it with `$HOME/.android/debug.keystore` (created if missing). Do not use a debug keystore for a production release. Android Studio/Gradle and the script may use different debug keys.

Run the local tests and optional browser preview:

```sh
npm ci
npm test             # command parsing and real HTTP gateway tests
npm run test:ui      # headless UI tests with a mocked Android bridge; Node 22+
npm run preview      # browser UI preview; native capabilities require Android
```

The optional gateway has no Python dependencies. The optional pure-Java native-logic tests use JDK 17 and a local [JSON-java](https://github.com/stleary/JSON-java) source checkout: `JAVA_HOME=/path/to/jdk-17 JSON_JAVA_SRC=/path/to/JSON-java/src/main/java ./scripts/test-native-logic.sh`. See [ARCHITECTURE.md](ARCHITECTURE.md), [DESIGN.md](DESIGN.md), [MEMORY.md](MEMORY.md), [PRD.md](PRD.md), [RULES.md](RULES.md), and [TASKS.md](TASKS.md) for details.

## Permissions

| Permission / access | Why | When |
| --- | --- | --- |
| Internet | Connect to your configured model server | Only when you test or send a model prompt |
| Microphone | Android speech recognition | Requested when you start voice |
| Display over other apps | Floating HUD | Requested when you turn it on |
| Accessibility service | Read visible labels / one supervised tap | Separately enabled by you in system settings |
| Foreground service and notifications | Keep the user-enabled HUD visible and provide a stop control | While HUD is on; notification access is optional |

Ultron does not request contact, SMS-send, broad storage, or location permissions. The app uses Android intents instead of sending SMS or accessing contacts itself.

## Third-party notices

Manrope and Space Grotesk fonts are distributed under the SIL Open Font License; their licenses are in [`licenses/`](licenses/). The Gradle wrapper is distributed by Gradle under the Apache License 2.0. Models, Ollama, Android services, and apps you choose have their own licenses and terms.
