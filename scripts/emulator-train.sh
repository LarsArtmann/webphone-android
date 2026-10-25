#!/usr/bin/env bash
# The self-serve device train: boot an API 36 emulator headlessly,
# install the APK, launch the shell, save a screenshot.
#
# Usage:
#   nix develop -c ./gradlew assembleDebug   # the APK must exist first
#   nix develop .#emulator -c bash scripts/emulator-train.sh
#   nix develop .#emulator -c bash scripts/emulator-train.sh --release
#
# --release is the release SMOKE mode: the unsigned release artifact
# is aligned and signed with the DEBUG key (never a real signature —
# a real release signing config is a deliberate future step, see
# AGENTS.md) so R8/shrinker output gets booted on a device before any
# real release exists.
#
# The AVD lives in .avd/ (repo-local, gitignored). Requires /dev/kvm.
set -euo pipefail

AVD_NAME="webphone-train"
AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
mkdir -p "$AVD_HOME"

SDK="${ANDROID_HOME:?ANDROID_HOME must point at the androidenv SDK (run inside nix develop .#emulator)}"
MODE="debug"
if [ "${1:-}" = "--release" ]; then
	MODE="release"
	shift
fi
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
SHOT="${1:-webphone-train.png}"
if [ -n "${1:-}" ] && [ -f "$1" ]; then
	SHOT="${1%.apk}-train.png"
fi

IMG_DIR="$SDK/system-images/android-36/google_apis/x86_64"
if [ ! -d "$IMG_DIR" ]; then
	echo "system image missing: $IMG_DIR" >&2
	exit 1
fi

if [ "$MODE" = "release" ]; then
	UNSIGNED="app/build/outputs/apk/release/app-release-unsigned.apk"
	[ -f "$UNSIGNED" ] || {
		echo "release APK missing: $UNSIGNED (run ./gradlew assembleRelease first)" >&2
		exit 1
	}
	ALIGNED="app/build/outputs/apk/release/app-release-smoke-aligned.apk"
	SIGNED="app/build/outputs/apk/release/app-release-smoke.apk"
	"$SDK/build-tools/35.0.0/zipalign" -f -P 16 4 "$UNSIGNED" "$ALIGNED"
	KS="${HOME}/.android/debug.keystore"
	if [ ! -f "$KS" ]; then
		echo "== generating a local debug keystore (smoke-signing only) =="
		keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
			-alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
			-keyalg RSA -keysize 2048 -validity 10000
	fi
	"$SDK/build-tools/35.0.0/apksigner" sign --ks "$KS" \
		--ks-pass pass:android --key-pass pass:android \
		--out "$SIGNED" "$ALIGNED"
	APK="$SIGNED"
	SHOT="webphone-release-smoke.png"
	echo "== release smoke APK: $APK =="
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
# The emulator daemonizes: killing the launcher PID can miss it. The
# serial-based `emu kill` is the reliable path once the serial exists.
trap 'kill "$EMU_PID" 2>/dev/null || true; [ -n "${SERIAL:-}" ] && adb -s "$SERIAL" emu kill 2>/dev/null || true' EXIT

# adb discipline (the dual-server trap): pin every call to this boot's
# serial; fail fast on a foreign server or a second device.
adb start-server >/dev/null 2>&1 || {
	echo "adb server unreachable — another adb version may own port 5037" >&2
	exit 1
}
# Resolve OUR emulator's serial FIRST, then wait pinned to it: a bare
# `adb wait-for-device` aborts ("more than one device/emulator") the
# moment any physical device shares the server (found by the
# --release smoke run, 2026-10-25 — the operator's phone was attached).
for _ in $(seq 1 60); do
	SERIAL="$(adb devices | awk '/emulator-/ {print $1}' | head -1)"
	[ -n "$SERIAL" ] && break
	sleep 2
done
[ -n "$SERIAL" ] || {
	echo "emulator serial never appeared" >&2
	exit 1
}
COUNT="$(adb devices | grep -c 'emulator-')"
[ "$COUNT" = "1" ] || {
	echo "expected exactly one emulator, found $COUNT — refuse to guess" >&2
	exit 1
}
adb_() {
	adb -s "$SERIAL" "$@"
}
adb_ root >/dev/null 2>&1 || true

echo "== waiting for boot completion ($SERIAL) =="
adb_ wait-for-device
for _ in $(seq 1 180); do
	boot="$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
	[ "$boot" = "1" ] && break
	sleep 2
done
[ "$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] ||
	{
		echo "emulator did not finish booting" >&2
		exit 1
	}

echo "== installing $APK =="
if [ "$MODE" = "release" ]; then
	# The smoke APK is signed with a freshly generated debug keystore —
	# any package already on the AVD (the gradle debug builds) has a
	# different signature and the install dies with
	# INSTALL_FAILED_UPDATE_INCOMPATIBLE. A smoke run needs no old data.
	adb_ uninstall software.larsartmann.webphone >/dev/null 2>&1 || true
fi
adb_ install -r "$APK"

echo "== launching the shell =="
adb_ shell am start -n software.larsartmann.webphone/.shell.MainActivity
sleep 8

adb_ exec-out screencap -p >"$SHOT"
echo "== screenshot: $SHOT =="
adb_ logcat -d -t 200 | grep -Ei "webphone|SipEngine|SessionApi|RingEvent|NtfySubscription|FATAL" || true
