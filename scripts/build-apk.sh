#!/usr/bin/env bash
# Build a dependency-free debug APK using Android SDK command-line tools.
# This is equivalent to a debug build, not a production-signed release.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-${ANDROID_HOME}/build-tools/34.0.0}"
ANDROID_JAR="${ANDROID_JAR:-${ANDROID_HOME}/platforms/android-34/android.jar}"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
for required in "$JAVA_HOME/bin/javac" "$ANDROID_JAR" "$ANDROID_BUILD_TOOLS/aapt" "$ANDROID_BUILD_TOOLS/d8" "$ANDROID_BUILD_TOOLS/zipalign" "$ANDROID_BUILD_TOOLS/apksigner"; do
  [[ -e "$required" ]] || { echo "Missing Android build prerequisite: $required" >&2; exit 1; }
done
WORK="${TMPDIR:-/tmp}/ultron-apk-build-$$"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes" "$WORK/dex" "$ROOT/releases" "$HOME/.android"
# AGP gets the namespace from build.gradle; standalone aapt needs it in the manifest.
MANIFEST="$WORK/AndroidManifest.xml"
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.ultronai.assistant">#' "$ROOT/app/src/main/AndroidManifest.xml" > "$MANIFEST"
RES="$ROOT/app/src/main/res"
ASSETS="$ROOT/app/src/main/assets"
"$ANDROID_BUILD_TOOLS/aapt" package -f -m -J "$WORK/gen" -M "$MANIFEST" -S "$RES" -A "$ASSETS" -I "$ANDROID_JAR" -F "$WORK/unsigned-unaligned.apk" --version-code 1 --version-name 0.1.0
find "$ROOT/app/src/main/java" "$WORK/gen" -name '*.java' | sort > "$WORK/sources.txt"
"$JAVA_HOME/bin/javac" -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$ANDROID_BUILD_TOOLS/core-lambda-stubs.jar" -encoding UTF-8 -d "$WORK/classes" @"$WORK/sources.txt"
find "$WORK/classes" -name '*.class' | sort > "$WORK/classes.txt"
"$ANDROID_BUILD_TOOLS/d8" --min-api 21 --lib "$ANDROID_JAR" --output "$WORK/dex" @"$WORK/classes.txt"
(cd "$WORK/dex" && "$ANDROID_BUILD_TOOLS/aapt" add "$WORK/unsigned-unaligned.apk" classes.dex >/dev/null)
"$ANDROID_BUILD_TOOLS/zipalign" -f 4 "$WORK/unsigned-unaligned.apk" "$WORK/unsigned.apk"
KEYSTORE="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [[ ! -f "$KEYSTORE" ]]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
APK="$ROOT/releases/Ultron-Ai-v0.1.0.apk"
"$ANDROID_BUILD_TOOLS/apksigner" sign --v4-signing-enabled false --ks "$KEYSTORE" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$APK" "$WORK/unsigned.apk"
"$ANDROID_BUILD_TOOLS/apksigner" verify --verbose "$APK"
echo "Built: $APK"
"$ANDROID_BUILD_TOOLS/aapt" dump badging "$APK" | head -n 12
