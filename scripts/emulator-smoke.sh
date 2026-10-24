#!/usr/bin/env bash
# Interactive smoke loop: boot (or reuse) the train emulator, install,
# launch, capture screenshot + crash logcat. Emulator keeps running.
set -euo pipefail
AVD_NAME="webphone-train"
AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
mkdir -p "$AVD_HOME"
SDK="${ANDROID_HOME:?run inside nix develop .#emulator}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

if ! adb devices 2>/dev/null | grep -q "emulator"; then
	echo "== booting emulator =="
	emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
		-gpu swiftshader_indirect &
	sleep 3
fi

adb wait-for-device
for _ in $(seq 1 180); do
	[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
	sleep 2
done
echo "== boot completed =="

adb install -r "$APK"
adb logcat -c
adb shell am start -W -n software.larsartmann.webphone/.shell.MainActivity || true
sleep 6
adb exec-out screencap -p >"${2:-webphone-train.png}"
echo "== screenshot: ${2:-webphone-train.png} =="
echo "== crash/warning logcat =="
adb logcat -d | grep -E "FATAL|AndroidRuntime|software.larsartmann.webphone" | grep -v "Blockstore\|AppsFilter\|ExtServices\|EGL_emulation\|gralloc" | head -60 || true
