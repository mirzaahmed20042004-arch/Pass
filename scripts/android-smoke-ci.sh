#!/usr/bin/env bash
set -o pipefail
./scripts/android-smoke.sh "$@" 2>&1 | tee "${RUNNER_TEMP:-/tmp}/ultron-smoke.log"
status=${PIPESTATUS[0]}
if [ "$status" != 0 ]; then
  adb logcat -d -v brief | grep -iE 'chromium|webview|ultron|AndroidRuntime' | tail -50 > "${RUNNER_TEMP:-/tmp}/webview-log.txt" || true
  echo "::notice title=WebView diagnostics::$(tr '\n' ' ' < "${RUNNER_TEMP:-/tmp}/webview-log.txt" | tail -c 9000 | sed 's/%/%25/g')"
  adb exec-out screencap -p > "${RUNNER_TEMP:-/tmp}/ultron-screenshot.png" || true
  python3 - <<'PY'
import base64, os
path = os.path.join(os.environ.get('RUNNER_TEMP', '/tmp'), 'ultron-screenshot.png')
try:
    value = base64.b64encode(open(path, 'rb').read()).decode()
    for index in range(0, len(value), 12000):
        print('::notice title=ULTRON_SCREEN_%03d::%s' % (index // 12000, value[index:index + 12000]))
except OSError:
    pass
PY
fi
exit "$status"
