#!/usr/bin/env bash
# Run pure-Java native logic tests using the public JSON-java source tree.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
JSON_JAVA_SRC="${JSON_JAVA_SRC:?Set JSON_JAVA_SRC to JSON-java/src/main/java}"
WORK="${TMPDIR:-/tmp}/ultron-native-tests-$$"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK"
find "$JSON_JAVA_SRC/org/json" -name '*.java' | sort > "$WORK/sources.txt"
printf '%s\n' "$ROOT/app/src/main/java/com/ultronai/assistant/ActionSafety.java" "$ROOT/app/src/main/java/com/ultronai/assistant/ModelClient.java" "$ROOT/tests/java/com/ultronai/assistant/NativeLogicTest.java" >> "$WORK/sources.txt"
"$JAVA_HOME/bin/javac" --add-modules jdk.httpserver -encoding UTF-8 -d "$WORK" @"$WORK/sources.txt"
"$JAVA_HOME/bin/java" --add-modules jdk.httpserver -cp "$WORK" com.ultronai.assistant.NativeLogicTest
