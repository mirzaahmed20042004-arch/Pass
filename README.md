# Easy AI

An open-source, Android-first toolkit with 20 useful tools and no account or API key. The web app also runs in a browser.

**Honest capabilities:** 18 tools are local, deterministic utilities (including extractive summarization and template-based ideas), not generative AI models. Wikipedia Search and Dictionary need a network connection and send the entered query to Wikipedia or dictionaryapi.dev respectively. These external services can change, fail, impose limits, or have their own privacy policies; free unlimited service forever cannot be guaranteed. Speech uses the device/browser speech engine and may not be available on every device. Favorites and recent-tool IDs stay in local storage.

## Run on the web

Requires Node.js 20+.

```sh
npm ci
npm test
npm run dev
```

## Download Android APK

[Download the Easy AI debug APK](https://github.com/mirzaahmed20042004-arch/Pass/raw/refs/heads/arena/a8e1102b-pass/releases/Easy-AI-debug.apk). This is a debug build, signature-verified but not tested on a physical Android device. See the signing notes below.

## Build Android APK

Install JDK 17, Android SDK Platform 35 and Gradle 8.9. Then from the repository root:

```sh
npm ci
npm run build
mkdir -p android/app/src/main/assets/web
cp -R dist/. android/app/src/main/assets/web/
gradle -p android assembleDebug
```

The debug APK is at `android/app/build/outputs/apk/debug/app-debug.apk`. It is signed with the local Android debug key, suitable for testing, not store publication. For a release, configure your own signing key and review the app. The included GitHub Actions workflow can build the debug APK as a downloadable workflow artifact when run in a GitHub repository with Actions enabled.

The Android wrapper uses a WebView with local bundled assets and no JavaScript bridge. It allows cross-origin requests from bundled files for the two online lookup tools and prevents navigation away from the local app. Review this security trade-off before distribution. Internet permission is required for the online tools. WebView and a speech engine must be installed/enabled on the device.

## License

MIT. See `LICENSE`.

The downloadable debug APK in the workspace, when provided, was assembled with Android API 28 resources (minimum API 23) and signed with a temporary debug key. Its signing key is not retained; reinstalling a differently signed build requires uninstalling this build first. The APK was signature-checked but has not been tested on an Android device in this environment. The Gradle project above targets API 35 for future builds.
