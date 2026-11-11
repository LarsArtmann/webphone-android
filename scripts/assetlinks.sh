#!/usr/bin/env bash
# Emit a serve-ready /.well-known/assetlinks.json for the webphone repo
# (R04/N012): the fingerprint of the keystore THIS machine actually
# signs the debug APK with, the package the manifest declares, and the
# relations the two consumers need.
#
# TWO relations, TWO serving duties (both in ONE file is fine — a
# statement only applies on the host that serves it):
#
#   1. delegate_permission/common.handle_all_urls — App Links (R18).
#      Serve on the stack origin (pbx.artmann.tech); flips autoVerify
#      so implicit https deep links stop bouncing to Chrome.
#
#   2. delegate_permission/common.get_login_creds — WebAuthn/passkeys
#      in the app's WebView (the 2026-11-11 "OAuth broken" fix, app
#      half: WebSurface.optInToPasskeys). Credential Manager only lets
#      the shell run a passkey ceremony for an RP host that serves
#      THIS relation naming the app + cert fingerprint. The IdP host
#      auth.larsartmann.cloud (Pocket ID; passkey-ONLY sign-in) MUST
#      serve it or the in-shell login still dead-ends at the RP check
#      (verified 404 there, 2026-11-11). Serve on auth.larsartmann.cloud
#      AND pbx.artmann.tech (the island may mint web passkeys too).
#
# IMPORTANT: this emits the DEBUG keystore by default. The PHONE runs
# the RELEASE-signed build — serve the statement generated with the
# release keystore passed explicitly:
#   scripts/assetlinks.sh ~/.config/webphone-android/release.keystore
#
# Usage:
#   scripts/assetlinks.sh                     # auto-discover (see below)
#   scripts/assetlinks.sh path/to/keystore    # explicit keystore (release)
#
# Output goes to STDOUT — pipe it into the server repo's static file
# (e.g. internal/web/static/.well-known/assetlinks.json). The server
# must serve it over HTTPS from the stack origin root as
# application/json; verification then flips (`adb shell pm get-app-links
# software.larsartmann.webphone` → "pbx.artmann.tech: verified") and
# https deep links stop bouncing through the resolver (see the
# R18/R02 train findings).
#
# KEYSTORE DISCOVERY — the trap this script exists to prevent (found
# 2026-10-27): the JVM this project builds with resolves the AGP debug
# keystore under the XDG config home (~/.config/.android/debug.keystore),
# NOT the classic ~/.android/debug.keystore. Both may exist; only one
# signs the APK. Discovery order:
#   1. $ANDROID_USER_HOME/debug.keystore
#   2. $XDG_CONFIG_HOME/.android/debug.keystore (default ~/.config/.android/…)
#   3. ~/.android/debug.keystore
# and when a built debug APK exists, the script CROSS-CHECKS the chosen
# keystore against the APK's real signing cert (apksigner) and refuses
# to emit a fingerprint that would NOT verify — the APK is the truth,
# not the keystore you happened to find.
set -euo pipefail

PACKAGE="software.larsartmann.webphone"
APK="${APK:-app/build/outputs/apk/debug/app-debug.apk}"

if [ $# -ge 1 ]; then
    KEYSTORE="$1"
else
    # AGP's debug-keystore search, in the order that bites on this stack.
    candidates=(
        "${ANDROID_USER_HOME:-}/debug.keystore"
        "${XDG_CONFIG_HOME:-$HOME/.config}/.android/debug.keystore"
        "$HOME/.android/debug.keystore"
    )
    KEYSTORE=""
    for c in "${candidates[@]}"; do
        if [ -n "$(dirname "$c" 2>/dev/null)" ] && [ -f "$c" ]; then
            KEYSTORE="$c"
            break
        fi
    done
    if [ -z "$KEYSTORE" ]; then
        echo "no debug keystore found (tried: ${candidates[*]})" >&2
        exit 1
    fi
fi

ALIAS="${ANDROID_KEY_ALIAS:-androiddebugkey}"
STOREPASS="${ANDROID_STORE_PASS:-android}"

if [ ! -f "$KEYSTORE" ]; then
    echo "keystore not found: $KEYSTORE" >&2
    exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
    echo "keytool not on PATH — run inside the dev environment (nix develop)" >&2
    exit 1
fi

fingerprint_of() {
    keytool -list -v -keystore "$1" -alias "$ALIAS" -storepass "$STOREPASS" 2>/dev/null |
        grep -m1 'SHA256:' |
        sed 's/^[[:space:]]*SHA256:[[:space:]]*//' |
        awk '{print $1}'
}

FINGERPRINT="$(fingerprint_of "$KEYSTORE")"
if [ -z "$FINGERPRINT" ]; then
    echo "could not read a SHA256 fingerprint from $KEYSTORE (alias $ALIAS)" >&2
    exit 1
fi

# Cross-check against the artifact the device actually runs. apksigner
# is NOT on the devShell PATH (the zipalign trap) — resolve it from the
# SDK's build-tools.
APKSIGNER="$(command -v apksigner || true)"
if [ -z "$APKSIGNER" ] && [ -n "${ANDROID_HOME:-}" ]; then
    APKSIGNER="$(ls -1 "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
fi
if [ -f "$APK" ] && [ -n "$APKSIGNER" ]; then
    APK_FP="$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null |
        grep -m1 'SHA-256 digest' |
        awk '{print tolower($NF)}' |
        sed 's/\(..\)/\1:/g; s/:$//' |
        tr '[:lower:]' '[:upper:]')"
    if [ -n "$APK_FP" ] && [ "$APK_FP" != "$FINGERPRINT" ]; then
        echo "REFUSING to emit: $KEYSTORE fingerprint $FINGERPRINT" >&2
        echo "does NOT sign the built APK (its cert is $APK_FP)." >&2
        echo "assetlinks.json with the wrong fingerprint bricks app-link verification." >&2
        exit 1
    fi
fi

cat <<EOF
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "$PACKAGE",
      "sha256_cert_fingerprints": [
        "$FINGERPRINT"
      ]
    }
  },
  {
    "relation": ["delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "$PACKAGE",
      "sha256_cert_fingerprints": [
        "$FINGERPRINT"
      ]
    }
  }
]
EOF
