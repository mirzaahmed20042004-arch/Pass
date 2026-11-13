#!/usr/bin/env bash
# Smoke-test the exact APK on an already-booted emulator/device.
set -euo pipefail
APK="${1:-releases/Ultron-Ai-v0.1.0.apk}"
UI_FILE="${TMPDIR:-/tmp}/ultron-ui-$$.xml"
trap 'rm -f "$UI_FILE"' EXIT

dump_ui() {
  adb shell uiautomator dump /sdcard/ultron-window.xml >/dev/null
  adb exec-out cat /sdcard/ultron-window.xml > "$UI_FILE"
}
tap_node() {
  local label="$1"
  local coords
  coords="$(python3 - "$UI_FILE" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
needle = sys.argv[2]
for node in root.iter('node'):
    attrs = node.attrib
    if attrs.get('text') == needle or attrs.get('content-desc') == needle or (needle == '__EDIT__' and attrs.get('class') == 'android.widget.EditText'):
        bounds = list(map(int, re.findall(r'\d+', attrs.get('bounds', ''))))
        if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
            print((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
            break
else:
    sys.exit('Could not find visible UI control: ' + needle)
PY
)" || { cat "$UI_FILE"; return 1; }
  adb shell input tap $coords
}

adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am force-stop com.ultronai.assistant
adb shell am start -n com.ultronai.assistant/.MainActivity
sleep 15
if ! adb shell pidof com.ultronai.assistant | tr -d '\r' | grep -q '[0-9]'; then
  adb logcat -d -s AndroidRuntime:E; echo 'App process is not running' >&2; exit 1
fi
# The first dump can enable WebView accessibility asynchronously; allow it to settle.
dump_ui
sleep 3
dump_ui
if ! grep -q 'Consider it\|YOUR PERSONAL COMMAND CENTER\|Good morning\|Good afternoon\|Good evening' "$UI_FILE"; then
  cat "$UI_FILE"; echo 'WebView content did not appear in the accessibility tree' >&2; exit 1
fi
# Exercise a real WebView interaction and local note persistence, not a mocked bridge.
tap_node 'Chat'
sleep 2
dump_ui
tap_node '__EDIT__'
adb shell input text 'remember%sbuy%smilk'
adb shell input keyevent 4
sleep 1
dump_ui
tap_node 'Send message'
sleep 4
dump_ui
if ! grep -q 'Saved to your local memories' "$UI_FILE"; then cat "$UI_FILE"; echo 'Local command did not complete' >&2; exit 1; fi
adb shell am force-stop com.ultronai.assistant
adb shell am start -n com.ultronai.assistant/.MainActivity
sleep 8
dump_ui
sleep 2
dump_ui
tap_node 'Memory'
sleep 2
dump_ui
if ! grep -q 'buy milk' "$UI_FILE"; then cat "$UI_FILE"; echo 'Local memory did not persist across restart' >&2; exit 1; fi
LOG="$(adb logcat -d -s AndroidRuntime:E)"
if grep -q 'Process: com.ultronai.assistant' <<< "$LOG"; then echo "$LOG"; exit 1; fi
echo 'APK installed, rendered, handled a local chat command, and persisted a note across restart without an AndroidRuntime crash.'
