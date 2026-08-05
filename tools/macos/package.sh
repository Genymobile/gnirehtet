#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS_DIR="$ROOT_DIR/tools/macos"
OUT_DIR="$ROOT_DIR/build/macos-kit/Gnirehtet-Android16-Mac-Kit"
APP_DIR="$OUT_DIR/Gnirehtet Controller.app"

cd "$ROOT_DIR"

./gradlew :app:assembleDebug :relay-java:assembleDebug

APK_PATH="$(find "$ROOT_DIR/app/build/outputs/apk/debug" -type f -name '*.apk' | head -1)"
JAR_PATH="$ROOT_DIR/relay-java/build/libs/gnirehtet.jar"

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "Could not find debug APK under app/build/outputs/apk/debug" >&2
  exit 1
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Could not find relay jar at $JAR_PATH" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$APP_DIR/Contents/MacOS" "$APP_DIR/Contents/Resources"

cp "$TOOLS_DIR/AppWrapper/Info.plist" "$APP_DIR/Contents/Info.plist"
cp "$TOOLS_DIR/AppWrapper/gnirehtet-controller-launcher" \
  "$APP_DIR/Contents/MacOS/gnirehtet-controller-launcher"
cp "$TOOLS_DIR/Gnirehtet Controller.command" \
  "$APP_DIR/Contents/Resources/Gnirehtet Controller.command"
cp "$TOOLS_DIR/gnirehtet-monitor.sh" \
  "$APP_DIR/Contents/Resources/gnirehtet-monitor.sh"
cp "$APK_PATH" "$APP_DIR/Contents/Resources/gnirehtet-android16-debug.apk"
cp "$JAR_PATH" "$APP_DIR/Contents/Resources/gnirehtet.jar"

chmod +x "$APP_DIR/Contents/MacOS/gnirehtet-controller-launcher"
chmod +x "$APP_DIR/Contents/Resources/Gnirehtet Controller.command"
chmod +x "$APP_DIR/Contents/Resources/gnirehtet-monitor.sh"

cp "$TOOLS_DIR/Gnirehtet Controller.command" "$OUT_DIR/Gnirehtet Controller.command"
cp "$TOOLS_DIR/gnirehtet-monitor.sh" "$OUT_DIR/gnirehtet-monitor.sh"
cp "$APK_PATH" "$OUT_DIR/gnirehtet-android16-debug.apk"
cp "$JAR_PATH" "$OUT_DIR/gnirehtet.jar"
cp "$TOOLS_DIR/README.txt" "$OUT_DIR/README.txt"

chmod +x "$OUT_DIR/Gnirehtet Controller.command"
chmod +x "$OUT_DIR/gnirehtet-monitor.sh"

echo "macOS kit written to:"
echo "$OUT_DIR"
