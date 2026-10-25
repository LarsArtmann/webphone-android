#!/usr/bin/env bash
# The WebView-train visual + lifecycle verification pass (P06 + P07).
#
# Boots (or reuses) the train emulator and drives, start to finish:
#   - W101/W102: web surfaces under light AND dark ui-mode
#   - W103: offline overlay (airplane-mode cold load) + Retry recovery
#   - W107: renderer kill -> the shell survives, the WebView rebuilds
#   - F024: native dialer light + dark
#   - F035/F036: the ntfy FGS kill-cycle (sticky restart resubscribes)
#
# Everything lands in OUT_DIR (default work/verify): PNGs, logcat
# evidence, and a summary the caller greps. The emulator is killed at
# the end — this script OWNS the emulator's lifetime because host-side
# detached emulators get reaped within minutes (2026-10-25 flapping).
#
# adb discipline: ONE adb, pinned to the single emulator serial; the
# script refuses when more than one emulator answers. Physical phones
# on the same server are never addressed.
set -euo pipefail

AVD_NAME="webphone-train"
AVD_HOME="${ANDROID_AVD_HOME:-$PWD/.avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
SDK="${ANDROID_HOME:?run inside nix develop .#emulator}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${2:-work/verify}"
PKG=software.larsartmann.webphone
mkdir -p "$OUT"

adb_() { adb -s "$SERIAL" "$@"; }

adb start-server >/dev/null
if ! adb devices 2>/dev/null | grep -q "emulator-"; then
	echo "== booting emulator (owned by this script) =="
	emulator -avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot \
		-gpu swiftshader_indirect >"$OUT/emulator.log" 2>&1 &
fi
for _ in $(seq 1 60); do
	SERIAL="$(adb devices 2>/dev/null | awk '/emulator-/ {print $1}' | head -1)"
	[ -n "$SERIAL" ] && break
	sleep 2
done
[ -n "${SERIAL:-}" ] || { echo "no emulator serial" >&2; exit 1; }
[ "$(adb devices | grep -c 'emulator-')" -eq 1 ] || { echo "multiple emulators" >&2; exit 1; }
echo "== using $SERIAL =="

adb_ root >/dev/null 2>&1 || true
adb_ wait-for-device
for _ in $(seq 1 180); do
	[ "$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
	sleep 2
done
echo "== boot completed =="

shot() { adb_ exec-out screencap -p >"$OUT/$1.png"; echo "shot $1"; }

echo "== install + launch =="
# A previous session's install may carry a different debug signature —
# replace, never fight INSTALL_FAILED_UPDATE_INCOMPATIBLE.
adb_ uninstall "$PKG" >/dev/null 2>&1 || true
adb_ install -r "$APK" >/dev/null
adb_ logcat -c
adb_ shell cmd uimode night no >/dev/null
adb_ shell am start -W -n "$PKG/.shell.MainActivity" >/dev/null
sleep 8
shot w00-baseline-light

echo "== W101 dark =="
adb_ shell cmd uimode night yes >/dev/null
sleep 4
shot w101-web-dark

echo "== W102 light =="
adb_ shell cmd uimode night no >/dev/null
sleep 4
shot w102-web-light

echo "== F024 native dialer dark + light =="
adb_ shell cmd uimode night yes >/dev/null
sleep 2
adb_ shell am start -W -n "$PKG/.call.DialpadActivity" >/dev/null
sleep 3
shot f024-dialpad-dark
adb_ shell cmd uimode night no >/dev/null
sleep 3
shot f024-dialpad-light
adb_ shell input keyevent KEYCODE_HOME >/dev/null
sleep 1
adb_ shell am start -W -n "$PKG/.shell.MainActivity" >/dev/null
sleep 4

echo "== W103 offline overlay + retry recovery =="
adb_ shell cmd connectivity airplane-mode enable >/dev/null
sleep 2
adb_ shell am force-stop "$PKG"
sleep 1
adb_ shell am start -W -n "$PKG/.shell.MainActivity" >/dev/null
sleep 8
shot w103a-offline-overlay
adb_ shell cmd connectivity airplane-mode disable >/dev/null
sleep 5
# Retry button sits centered under the overlay copy (1080x2400 screen).
adb_ shell input tap 540 1380
sleep 8
shot w103b-after-retry

echo "== W107 renderer kill =="
RENDERER_PID="$(adb_ shell ps -A | grep -m1 'sandboxed_process' | awk '{print $2}' | tr -d '\r')"
if [ -n "$RENDERER_PID" ]; then
	echo "killing renderer pid $RENDERER_PID"
	adb_ shell kill "$RENDERER_PID"
	sleep 6
	APP_PID="$(adb_ shell pidof "$PKG" | tr -d '\r')"
	[ -n "$APP_PID" ] && echo "app SURVIVED renderer death (pid $APP_PID)" \
		|| echo "APP DIED with the renderer"
	shot w107-after-renderer-kill
else
	echo "no sandboxed_process found — page may not have loaded"
fi

echo "== F035/F036 ntfy kill-cycle =="
adb_ shell am start-foreground-service -n "$PKG/.push.NtfySubscriptionService" >/dev/null 2>&1 || true
sleep 3
adb_ shell dumpsys activity services "$PKG" >"$OUT/ntfy-services-before.txt" 2>/dev/null || true
adb_ shell input keyevent KEYCODE_HOME >/dev/null
sleep 2
adb_ shell am kill "$PKG"
sleep 10
adb_ shell pidof "$PKG" >/dev/null 2>&1 && echo "process restarted after am kill" \
	|| echo "process NOT running after am kill"
adb_ shell dumpsys activity services "$PKG" >"$OUT/ntfy-services-after.txt" 2>/dev/null || true
adb_ shell am start -W -n "$PKG/.shell.MainActivity" >/dev/null || true
sleep 3

echo "== logcat evidence =="
adb_ logcat -d >"$OUT/logcat.txt"
grep -E "WebSurface|Ntfy|ntfy|software.larsartmann.webphone.*(FATAL|AndroidRuntime)" \
	"$OUT/logcat.txt" | grep -v "BufferQueue\|chatty" | tail -60 >"$OUT/logcat-filtered.txt" || true
head -40 "$OUT/logcat-filtered.txt" || true

echo "== teardown =="
adb_ emu kill >/dev/null 2>&1 || true
sleep 3
echo "== DONE — evidence in $OUT =="
