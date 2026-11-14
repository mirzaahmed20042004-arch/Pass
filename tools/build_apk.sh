#!/usr/bin/env bash
# Full offline APK build: aapt2 -> javac -> D8 -> zip pack -> v1 sign -> align -> v2 sign.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD

# Resolve the (out-of-repo) toolchain: honour $TOOLS_DIR, else look in common spots.
TOOLS="${TOOLS_DIR:-}"
if [ -z "$TOOLS" ]; then
  for cand in "$ROOT/tools" /home/user/tools "$ROOT/../tools"; do
    if [ -x "$cand/jdk17/linux-x86/bin/javac" ]; then TOOLS=$cand; break; fi
  done
fi
[ -n "$TOOLS" ] || { echo "toolchain not found - set TOOLS_DIR"; exit 1; }

JAVA=$TOOLS/jdk17/linux-x86/bin
AAPT2=$TOOLS/aapt2
ANDROID_JAR=$TOOLS/android.jar
R8=$TOOLS/r8-master.jar
PY=$TOOLS/venv/bin/python
BUILD=$ROOT/build
OUT=$ROOT/apk/VoiceFilter-v2.0.apk

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/obj" "$BUILD/dex" "$BUILD/apk" "$ROOT/apk" "$ROOT/keys"

if [ ! -f "$ROOT/keys/key.pk8" ]; then
  echo "== generating signing identity"
  "$PY" tools/gen_key.py
fi

echo "== aapt2 compile"
"$AAPT2" compile --dir app/res -o "$BUILD/res.zip"

echo "== aapt2 link"
"$AAPT2" link -o "$BUILD/apk/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest app/AndroidManifest.xml \
  --java "$BUILD/gen" \
  --min-sdk-version 24 --target-sdk-version 29 \
  --version-code 2 --version-name 2.0 \
  "$BUILD/res.zip"

echo "== javac"
find app/src "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
"$JAVA/javac" -source 8 -target 8 -nowarn -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$BUILD/obj" @"$BUILD/sources.txt"

echo "== d8 -> classes.dex"
find "$BUILD/obj" -name '*.class' > "$BUILD/classes.txt"
"$JAVA/java" -cp "$R8" com.android.tools.r8.D8 --release --min-api 24 \
  --lib "$ANDROID_JAR" --output "$BUILD/dex" $(tr '\n' ' ' < "$BUILD/classes.txt")

echo "== pack + classes.dex"
"$PY" tools/pkg_align.py pack "$BUILD/apk/base.apk" "$BUILD/dex/classes.dex" "$BUILD/apk/packed.apk"

echo "== v1 sign (jarsigner)"
cp "$BUILD/apk/packed.apk" "$BUILD/apk/signed.apk"
"$JAVA/jarsigner" -keystore "$ROOT/keys/voicemorph.p12" -storetype PKCS12 \
  -storepass voicemorph -sigalg SHA256withRSA -digestalg SHA-256 \
  "$BUILD/apk/signed.apk" voicemorph

echo "== zipalign"
"$PY" tools/pkg_align.py align "$BUILD/apk/signed.apk" "$BUILD/apk/aligned.apk"

echo "== v2 sign"
"$PY" tools/sign_v2.py "$BUILD/apk/aligned.apk" "$OUT" "$ROOT/keys/key.pk8" "$ROOT/keys/cert.der"

echo "== sanity: badging"
"$AAPT2" dump badging "$OUT" | head -8
ls -la "$OUT"
