# iPhone Launcher 18 — iOS Style Launcher for Android

Apne Android phone (Android 10 se lekar 17 aur future versions tak) ko iPhone bana do:
iOS 18 jaisi home screen, Dynamic Island, Control Center, top shutter (Notification Center),
iOS volume panel, AssistiveTouch, iOS lock screen aur 50 iPhone wallpapers — sab kuch ek APK me.

## Download (Direct APK)

**[iPhone-Launcher-18.apk](https://github.com/mirzaahmed20042004-arch/Pass/releases/download/iphone-launcher-v1.0/iPhone-Launcher-18.apk)**

 Phone me link kholo → APK download karo → open karke install karo
 ("Install unknown apps" allow karna parega) → app kholo aur setup follow karo.

## Features

- **iOS Home Screen** — paged grid, dock, widgets (clock/date/battery), page dots, search pill
- **Dynamic Island** — home status bar + charging alerts
- **Control Center** — Wi-Fi, Bluetooth, Focus, rotation, flashlight, brightness, volume
- **Notification Center (top shutter)** — iOS style grouped notifications, swipe/tap
- **iOS Volume Panel** — side pill jis se system panel chhup jata hai (Accessibility se)
- **AssistiveTouch** — floating button: Home, CC, notifications, lock, settings
- **iOS Lock Screen** — bara clock, notifications, flashlight/camera shortcuts, 4-digit passcode
- **50 iPhone Wallpapers** — built-in, home + lock dono par lagao
- **App Icons** — Light / Dark / Tinted (iOS 18) + mashhoor apps ke liye custom iOS icons
- **Spotlight Search** — neeche swipe par search
- **App Library** — auto-categories
- **Folders, Hide apps, jiggle edit mode** — lambi press par
- **iOS mini-apps** — Calculator, Notes, Clock + stopwatch, Weather
- **Support** — Android 10 (API 29) se Android 17+ tak

## Gestures

| Gesture | Kaam |
|---|---|
| Home par neeche swipe | Spotlight search |
| Top-right se neeche swipe | Control Center |
| Top-left/center se neeche swipe | Notification Center |
| App par lambi press | Jiggle mode (hide / folder / info / uninstall) |
| Khaali jagah par lambi press | Wallpapers |

## Build (developers)

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Wallpapers/icons dobara banane ke liye:

```bash
pip install pillow
python3 scripts/gen_assets.py
```

GitHub Actions har push par APK banata hai aur
[Releases](https://github.com/mirzaahmed20042004-arch/Pass/releases) me publish karta hai.

## Notes

- Kuch features (notifications, overlay, volume panel, lock screen) ke liye system
  permissions/setup chahiye — app khud guide karti hai (Setup Wizard + Settings).
- Debug-signed APK hai, seedha install ho jata hai, Play Store ki zaroorat nahi.
