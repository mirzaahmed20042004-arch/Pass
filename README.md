# App Fly

App Fly is a premium, open-source, Android-ready knowledge companion built with React, Vite, FastAPI, and Capacitor.

## What works
- **Discover:** Fetch Wikipedia article summaries/images and Open Library book covers/metadata side by side. Pages do not open unless the user deliberately follows a source link.
- **Translate:** Connects to a LibreTranslate-compatible open-source translator. The Android build uses the public instance by default; public availability and supported language pairs may change. You can configure your own server.
- **Voice studio:** Plays text using the device's installed speech voices. English and Urdu availability depends on Android TTS voices; Roman Urdu can use an English voice. This is playback, not an exported audio file.
- Responsive desktop/mobile interface and Android app shell. The app requires internet for search and online translation.

Scribd and Z-Library are not scraped or integrated: they do not provide open APIs for unrestricted book retrieval. Open Library is the legal open-catalog alternative.

## Run the web app locally
1. Install Node.js 20+ and Python 3.10+.
2. `npm install`
3. `python -m venv .venv && . .venv/bin/activate && pip install -r server/requirements.txt`
4. Start the backend: `uvicorn server.main:app --host 0.0.0.0 --port 8000`
5. In another terminal run `npm run dev` and open the Vite address.

By default the web development server proxies API calls to FastAPI. Configure `LIBRETRANSLATE_URL` and optionally `LIBRETRANSLATE_API_KEY` to use another LibreTranslate service.

## Android build and download
A GitHub Actions workflow builds a debug APK on pushes to the App Fly branch and publishes it as a public GitHub Release. After a successful workflow run, download the newest APK here:

**[Download App Fly APK](https://github.com/mirzaahmed20042004-arch/Pass/releases/latest/download/app-fly.apk)**

The GitHub build installs the native Android project with JDK 21 and the Android SDK, and does not need a separately deployed FastAPI server. The native app queries Wikipedia and Open Library directly, and calls the public LibreTranslate endpoint; all require network access. To build locally, install Android Studio, Android SDK (API 23+), and JDK 21+, then run:

```sh
npm ci
npm run build
npx cap sync android
cd android && ./gradlew assembleDebug
```

The APK is created at `android/app/build/outputs/apk/debug/app-debug.apk`. For a different LibreTranslate instance in an Android build, set `VITE_LIBRETRANSLATE_URL` before `npm run build`.

## License and third-party services
App Fly source is MIT-licensed. Wikipedia and Open Library are queried via their public APIs, and each service retains its own terms and attribution requirements. LibreTranslate is open-source software, but public hosted instances may have availability limits. Voice playback depends on the device's speech engine and installed voices; no audio is uploaded or saved as an audio file.
