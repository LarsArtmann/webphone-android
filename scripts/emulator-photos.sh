#!/usr/bin/env bash
# The screenshot pipeline, promoted from session lessons (2026-10-25):
# boots (or reuses) the train emulator, installs the APK, and captures
# the shell in BOTH uimodes — the light/dark pairs the design audit
# keeps wanting, in ONE clean run.
#
# Usage:
#   nix develop .#emulator -c bash scripts/emulator-photos.sh [APK]
#
# Output: photos/shell-light.png, photos/shell-dark.png (+ crash
# logcat). Requires /dev/kvm.
set -euo pipefail

AVD_NAME="webphone-train"
AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
mkdir -p "$AVD_HOME" photos

SDK="${ANDROID_HOME:?run inside nix develop .#emulator}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
[ -f "$APK" ] || {
    echo "APK missing: $APK (run assembleDebug first)" >&2
    exit 1
}

adb_() {
    adb -s "$SERIAL" "$@"
}

adb start-server >/dev/null 2>&1 || {
    echo "adb server unreachable — another adb version may own port 5037" >&2
    exit 1
}

if ! adb devices 2>/dev/null | grep -q "emulator"; then
    echo "== booting emulator =="
    emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect &
    sleep 3
fi

SERIAL="$(adb devices | awk '/emulator-/ {print $1}' | head -1)"
COUNT="$(adb devices | grep -c 'emulator-')"
[ -n "$SERIAL" ] && [ "$COUNT" = "1" ] || {
    echo "expected exactly one emulator, found: '$SERIAL' (count $COUNT)" >&2
    exit 1
}
echo "== using $SERIAL =="

adb_ root >/dev/null 2>&1 || true
adb_ wait-for-device
for _ in $(seq 1 180); do
    [ "$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 2
done
echo "== boot completed =="

adb_ install -r "$APK"
adb_ logcat -c

capture() {
    local name="$1"
    adb_ shell am force-stop software.larsartmann.webphone
    sleep 1
    adb_ shell am start -n software.larsartmann.webphone/.shell.MainActivity
    sleep 6
    adb_ exec-out screencap -p >"photos/$name.png"
    echo "== photo: photos/$name.png =="
}

echo "== light mode =="
adb_ shell "cmd uimode night no"
capture shell-light

echo "== dark mode =="
adb_ shell "cmd uimode night yes"
capture shell-dark

echo "== crash/warning logcat =="
adb_ logcat -d | grep -E "FATAL|AndroidRuntime|software.larsartmann.webphone" | grep -v "Blockstore\|AppsFilter\|ExtServices\|EGL_emulation\|gralloc" | head -60 || true
