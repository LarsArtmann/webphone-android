#!/usr/bin/env bash
# The self-serve device train: boot an API 36 emulator headlessly,
# install the debug APK, launch the shell, save a screenshot.
#
# Usage:
#   nix develop -c ./gradlew assembleDebug   # the APK must exist first
#   nix develop .#emulator -c bash scripts/emulator-train.sh
#
# The AVD lives in .avd/ (repo-local, gitignored). Requires /dev/kvm.
set -euo pipefail

AVD_NAME="webphone-train"
AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
mkdir -p "$AVD_HOME"

SDK="${ANDROID_HOME:?ANDROID_HOME must point at the androidenv SDK (run inside nix develop .#emulator)}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

IMG_DIR="$SDK/system-images/android-36/google_apis/x86_64"
if [ ! -d "$IMG_DIR" ]; then
	echo "system image missing: $IMG_DIR" >&2
	exit 1
fi
[ -f "$APK" ] || {
	echo "APK missing: $APK (run assembleDebug first)" >&2
	exit 1
}

if [ ! -f "$AVD_HOME/$AVD_NAME.avd/config.ini" ]; then
	echo "no | creating AVD $AVD_NAME (system-images;android-36;google_apis;x86_64)"
	echo no | avdmanager create avd \
		-n "$AVD_NAME" \
		-k "system-images;android-36;google_apis;x86_64" \
		-d pixel_6
fi

echo "== booting emulator (headless) =="
emulator -avd "$AVD_NAME" \
	-no-window -no-audio -no-boot-anim -no-snapshot \
	-gpu swiftshader_indirect &
EMU_PID=$!
trap 'kill "$EMU_PID" 2>/dev/null || true' EXIT

adb wait-for-device
echo "== waiting for boot completion =="
for _ in $(seq 1 180); do
	boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
	[ "$boot" = "1" ] && break
	sleep 2
done
[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] ||
	{
		echo "emulator did not finish booting" >&2
		exit 1
	}

echo "== installing $APK =="
adb install -r "$APK"

echo "== launching the shell =="
adb shell am start -n software.larsartmann.webphone/.shell.MainActivity
sleep 8

SHOT="${2:-webphone-train.png}"
adb exec-out screencap -p >"$SHOT"
echo "== screenshot: $SHOT =="
adb logcat -d -t 200 | grep -Ei "webphone|SipEngine|SessionApi|RingRouter|NtfySubscription|FATAL" || true
