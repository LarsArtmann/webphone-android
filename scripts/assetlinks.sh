#!/usr/bin/env bash
# Emit a serve-ready /.well-known/assetlinks.json for the webphone repo
# (R04/N012): the fingerprint of THIS machine's keystore, the package
# the manifest declares, and the relation the intent filter needs.
#
# Usage:
#   scripts/assetlinks.sh                     # debug keystore (default)
#   scripts/assetlinks.sh path/to/keystore    # any keystore (release)
#
# The output goes to STDOUT — pipe it into the server repo's static
# file (e.g. internal/web/static/.well-known/assetlinks.json). The
# server must serve it over HTTPS from the stack origin root with
# content-type application/json; verification then flips
# (`adb shell pm get-app-links software.larsartmann.webphone` →
# "pbx.artmann.tech: verified") and https deep links stop bouncing
# through the resolver (see the R18/R02 train findings).
#
# The debug keystore is PER-MACHINE: a device train on another machine
# needs ITS fingerprint listed too (multiple entries are fine and
# expected until a release signing config exists — the fingerprint
# list is an allow-list, not an identity).
set -euo pipefail

KEYSTORE="${1:-${ANDROID_DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}}"
ALIAS="${ANDROID_KEY_ALIAS:-androiddebugkey}"
STOREPASS="${ANDROID_STORE_PASS:-android}"
PACKAGE="software.larsartmann.webphone"

if [ ! -f "$KEYSTORE" ]; then
	echo "keystore not found: $KEYSTORE" >&2
	echo "create one with: keytool -genkeypair -v -keystore '$KEYSTORE' -alias '$ALIAS' -keyalg RSA -keysize 2048 -validity 10000 -storepass '$STOREPASS'" >&2
	exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
	echo "keytool not on PATH — run inside the dev environment (nix develop)" >&2
	exit 1
fi

# The fingerprint line looks like:
#   SHA256: AA:BB:...:FF (the trailing name is keytool-version dependent)
FINGERPRINT="$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$STOREPASS" 2>/dev/null \
	| grep -m1 'SHA256:' \
	| sed 's/^[[:space:]]*SHA256:[[:space:]]*//' \
	| awk '{print $1}')"

if [ -z "$FINGERPRINT" ]; then
	echo "could not read a SHA256 fingerprint from $KEYSTORE (alias $ALIAS)" >&2
	exit 1
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
  }
]
EOF
