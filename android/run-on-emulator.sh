#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -z "${ANDROID_HOME:-}" ]]; then
  if [[ -d "/c/Users/Usher/AppData/Local/Android/Sdk" ]]; then
    export ANDROID_HOME="/c/Users/Usher/AppData/Local/Android/Sdk"
  elif [[ -n "${LOCALAPPDATA:-}" && -d "$LOCALAPPDATA/Android/Sdk" ]]; then
    export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
  fi
fi

if [[ -z "${ANDROID_HOME:-}" || ! -d "$ANDROID_HOME/platform-tools" ]]; then
  echo "ANDROID_HOME is not set or invalid." >&2
  exit 1
fi

export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

if ! adb devices | grep -qE '^emulator-[0-9]+[[:space:]]+device$'; then
  AVD="$(emulator -list-avds | head -1)"
  if [[ -z "$AVD" ]]; then
    echo "No emulator running and no AVD found." >&2
    exit 1
  fi
  echo "Starting emulator: $AVD"
  emulator -avd "$AVD" -no-snapshot-load >/dev/null 2>&1 &
  adb wait-for-device
  while [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]]; do
    sleep 2
  done
fi

./gradlew installDebug
adb shell am start -n com.example.xilo/.MainActivity
echo "App installed and launched on emulator."
