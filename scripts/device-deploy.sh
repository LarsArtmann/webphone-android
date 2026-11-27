#!/usr/bin/env bash
# Deploy the debug APK to an attached device, the proven way (the
# 2026-11-11 Edge 60 deploy, report 11-43 §a):
#
#   gradle guard -> signer cross-check -> (gate) -> adb install -r ->
#   dumpsys version verify -> wake + launch + pidof + crash scan + screencap
#
# The signer cross-check is the load-bearing step: a device that holds
# a release-keyed build refuses a debug-signed update
# (INSTALL_FAILED_UPDATE_INCOMPATIBLE), and Android will not change a
# package's key in place. On a mismatch this script STOPS and says so;
# uninstalling is a user decision (it deletes the session and settings).
#
# Usage:
#   bash scripts/device-deploy.sh [--serial S] [--skip-gate] [--apk FILE]
#     --serial     one device when several are attached (refuses to guess)
#     --skip-gate  deploy the already-built APK without re-running the gate
#     --apk        default app/build/outputs/apk/debug/app-debug.apk
# Run inside `nix develop` (apksigner lives in the SDK there).
set -euo pipefail
cd "$(dirname "$0")/.."

PKG=software.larsartmann.webphone
SERIAL=""
APK="app/build/outputs/apk/debug/app-debug.apk"
RUN_GATE=1
while [ "$#" -gt 0 ]; do
    case "$1" in
    --serial)
        SERIAL="$2"
        shift 2
        ;;
    --skip-gate)
        RUN_GATE=0
        shift
        ;;
    --apk)
        APK="$2"
        shift 2
        ;;
    *)
        echo "unknown argument '$1' (try the header comment)" >&2
        exit 2
        ;;
    esac
done

if pgrep -f 'Gradle[W]orkerMain' >/dev/null; then
    echo "REFUSING: a Gradle build is executing; two builds share one daemon lock" >&2
    exit 1
fi
command -v adb >/dev/null || {
    echo "adb not found — run inside 'nix develop'" >&2
    exit 1
}
: "${ANDROID_HOME:?run inside nix develop (ANDROID_HOME unset)}"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"

if [ -z "$SERIAL" ]; then
    count="$(timeout 30 adb devices | grep -c -E 'device$' || true)"
    [ "$count" = "1" ] || {
        echo "expected exactly one device, found $count — pass --serial (the script refuses to guess)" >&2
        exit 1
    }
fi
adb_() { timeout 180 adb ${SERIAL:+-s "$SERIAL"} "$@"; }
adb_ wait-for-device
echo "== device: $(adb_ shell getprop ro.product.model | tr -d '\r') ($SERIAL) ==""

[ -f "$APK" ] || {
    echo "APK missing: $APK (run assembleDebug first)" >&2
    exit 1
}

echo "== signer cross-check =="
apk_cert="$("$APKSIGNER" verify --print-certs "$APK" | awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print $2 }')"
installed="$(adb_ shell pm path "$PKG" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | head -1)"
if [ -n "$installed" ]; then
    adb_ pull "$installed" /tmp/webphone-installed-base.apk >/dev/null
    device_cert="$("$APKSIGNER" verify --print-certs /tmp/webphone-installed-base.apk |
        awk -F': ' '/Signer #1 certificate SHA-256 digest/ { print $2 }')"
    if [ "$apk_cert" != "$device_cert" ]; then
        echo "SIGNER MISMATCH: the device holds a build signed by" >&2
        echo "  $device_cert" >&2
        echo "and the APK is signed by" >&2
        echo "  $apk_cert" >&2
        echo "Android refuses a key change in place. To switch, uninstall first (this deletes" >&2
        echo "the app's session and settings):  adb ${SERIAL:+-s "$SERIAL"} uninstall $PKG" >&2
        exit 1
    fi
    echo "same signer as the installed build: ok"
else
    echo "not installed yet: fresh install"
fi

if [ "$RUN_GATE" = "1" ]; then
    bash scripts/verify-gate.sh
fi

expected="$(tr -d '[:space:]' <version.txt)"
echo "== installing $APK =="
adb_ install -r "$APK"

echo "== version verify (dumpsys) =="
dump="$(adb_ shell dumpsys package "$PKG" | tr -d '\r')"
echo "$dump" | grep -q "versionName=$expected" || {
    echo "installed versionName is not $expected:" >&2
    echo "$dump" | grep -E 'versionName|versionCode' >&2
    exit 1
}
echo "$dump" | grep -E 'versionName|versionCode' | head -2

echo "== wake + launch + liveness =="
adb_ shell input keyevent KEYCODE_WAKEUP
adb_ shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 6
pid="$(adb_ shell pidof "$PKG" | tr -d '\r')"
[ -n "$pid" ] || {
    echo "the app is not running after launch (pidof empty) — crash?" >&2
    exit 1
}
echo "pid $pid alive"

echo "== crash scan =="
if adb_ logcat -d -t 500 | grep -E "FATAL EXCEPTION|AndroidRuntime.*$PKG" | grep -v Blockstore; then
    echo "a crash signature is in logcat (above)" >&2
    exit 1
fi
echo "no crash signature"

mkdir -p work
shot="work/deploy-$(date '+%Y%m%d-%H%M').png"
adb_ exec-out screencap -p >"$shot"
echo "== deployed $expected; screenshot: $shot =="
