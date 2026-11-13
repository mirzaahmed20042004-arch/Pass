#!/usr/bin/env bash
set -o pipefail
./scripts/android-smoke.sh "$@" 2>&1 | tee "${RUNNER_TEMP:-/tmp}/ultron-smoke.log"
