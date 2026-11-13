#!/usr/bin/env bash
# Smoke-test the exact APK on an already-booted emulator/device.
set -euo pipefail
APK="${1:-releases/Ultron-Ai-v0.1.0.apk}"
adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.ultronai.assistant
adb shell am start -n com.ultronai.assistant/.MainActivity
sleep 15
if ! adb shell pidof com.ultronai.assistant | tr -d '\r' | grep -q '[0-9]'; then
  adb logcat -d -s AndroidRuntime:E; echo 'App process is not running' >&2; exit 1
fi
LOG="$(adb logcat -d -s AndroidRuntime:E)"
if grep -q 'Process: com.ultronai.assistant' <<< "$LOG"; then echo "$LOG"; exit 1; fi
adb shell uiautomator dump /sdcard/ultron-window.xml >/dev/null
UI="$(adb shell cat /sdcard/ultron-window.xml | tr -d '\r')"
if ! grep -q 'Consider it\|YOUR PERSONAL COMMAND CENTER\|Good morning\|Good afternoon\|Good evening' <<< "$UI"; then
  echo "$UI"; echo 'WebView content did not appear in the accessibility tree' >&2; exit 1
fi
echo 'APK installed, started, and rendered the dashboard without an AndroidRuntime crash.'
