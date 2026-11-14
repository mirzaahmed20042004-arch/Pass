#!/usr/bin/env bash
# Compile the pure-Java DSP core + preset table on the desktop JVM and run the pitch test.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
TOOLS="${TOOLS_DIR:-}"
if [ -z "$TOOLS" ]; then
  for cand in "$ROOT/tools" /home/user/tools "$ROOT/../tools"; do
    if [ -x "$cand/jdk17/linux-x86/bin/javac" ]; then TOOLS=$cand; break; fi
  done
fi
[ -n "$TOOLS" ] || { echo "toolchain not found - set TOOLS_DIR"; exit 1; }
mkdir -p build/dsptest
$TOOLS/jdk17/linux-x86/bin/javac -d build/dsptest \
  app/src/com/voicefilter/app/VoiceEngine.java \
  app/src/com/voicefilter/app/VoicePreset.java \
  tools/dsptest/TestDsp.java
$TOOLS/jdk17/linux-x86/bin/java -cp build/dsptest TestDsp
