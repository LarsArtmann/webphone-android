#!/usr/bin/env bash
# Interactive smoke loop: boot (or reuse) the train emulator, install,
# launch, capture screenshot + crash logcat. Emulator keeps running.
#
# adb discipline (the dual-server trap, 2026-10-25): ONE adb per
# session — every call is pinned to the resolved emulator serial, the
# script fails fast when ANOTHER adb server owns a busy port or more
# than one device answers, and `adb root` runs up front (API 36's
# google_apis image needs it for am start on non-exported activities).
set -euo pipefail
AVD_NAME="webphone-train"
AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
mkdir -p "$AVD_HOME"
SDK="${ANDROID_HOME:?run inside nix develop .#emulator}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
SHOT="${2:-webphone-train.png}"

adb_() {
	adb -s "$SERIAL" "$@"
}

# Fail fast on a foreign adb server (the version mismatch kills the
# server mid-flight and wait-for-device hangs forever).
if ! adb start-server >/dev/null 2>&1; then
	echo "adb server unreachable — another adb version may own port 5037" >&2
	exit 1
fi

if ! adb devices 2>/dev/null | grep -q "emulator"; then
	echo "== booting emulator =="
	emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
		-gpu swiftshader_indirect &
	sleep 3
fi

SERIAL="$(adb devices | awk '/emulator-/ {print $1}' | head -1)"
if [ -z "$SERIAL" ]; then
	echo "no emulator serial found" >&2
	exit 1
fi
COUNT="$(adb devices | grep -c 'emulator-')"
if [ "$COUNT" -gt 1 ]; then
	echo "more than one device online ($COUNT) — refuse to guess; run with exactly one emulator" >&2
	exit 1
fi
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
adb_ shell am start -W -n software.larsartmann.webphone/.shell.MainActivity || true
sleep 6
adb_ exec-out screencap -p >"$SHOT"
echo "== screenshot: $SHOT =="
echo "== crash/warning logcat =="
adb_ logcat -d | grep -E "FATAL|AndroidRuntime|software.larsartmann.webphone" | grep -v "Blockstore\|AppsFilter\|ExtServices\|EGL_emulation\|gralloc" | head -60 || true
