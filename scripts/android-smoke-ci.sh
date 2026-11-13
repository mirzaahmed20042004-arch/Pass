#!/usr/bin/env bash
set -o pipefail
./scripts/android-smoke.sh "$@" 2>&1 | tee "${RUNNER_TEMP:-/tmp}/ultron-smoke.log"
status=${PIPESTATUS[0]}
if [ "$status" != 0 ]; then
  adb logcat -d -v brief | grep -iE 'chromium|webview|ultron|AndroidRuntime' | tail -50 > "${RUNNER_TEMP:-/tmp}/webview-log.txt" || true
  echo "::notice title=WebView diagnostics::$(tr '\n' ' ' < "${RUNNER_TEMP:-/tmp}/webview-log.txt" | tail -c 9000 | sed 's/%/%25/g')"
fi
exit "$status"
