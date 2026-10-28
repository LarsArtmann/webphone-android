#!/usr/bin/env bash
# The verification gate, wrapped (SUPERB plan J06/M026).
#
# One command that refuses to start dirty (a concurrent Gradle daemon or
# a second checkout's build poisons timings and locks), then runs every
# gate the AGENTS "Build and verify" section demands, in order, loudly:
#
#   assembleDebug -> testDebugUnitTest -> ktlintCheck -> 16 KB zipalign
#   -> contrast-check -> dprint check
#
# Each Gradle step is time-bounded by `timeout` (the outer belt over the
# in-build test fence): a wedged run fails HERE instead of hiding.
#
# Usage: scripts/verify-gate.sh          (from anywhere; cd's to repo root)
#        SKIP_CONTRAST=1 scripts/verify-gate.sh   (no token changes made)
set -euo pipefail
cd "$(dirname "$0")/.."

step() { printf '\n=== %s ===\n' "$1"; }

# -- pre-flight: refuse to run beside a BUSY build ----------------------
# An idle Gradle daemon is fine (it gets reused); a live build is not.
# GradleWorkerMain processes exist only WHILE a build executes; the
# daemon's own --status says Busy for the same fact, belt and suspenders.
busy="$(pgrep -af 'GradleWorkerMain' || true)"
if [ -n "$busy" ]; then
  echo "REFUSING: a Gradle build is executing right now:" >&2
  printf '%s\n' "$busy" >&2
  echo "Wait for it (or stop it) first — two concurrent builds share one" >&2
  echo "daemon lock and produce lies, not results." >&2
  exit 1
fi
if timeout 120 nix develop -c ./gradlew --status 2>/dev/null | grep -E '\bBusy\b' >/dev/null; then
  echo "REFUSING: a Gradle daemon reports Busy (a build is mid-flight)." >&2
  echo "Wait for it to finish, then re-run this gate." >&2
  exit 1
fi

apk="app/build/outputs/apk/debug/app-debug.apk"

step "1/6 assembleDebug"
timeout 900 nix develop -c ./gradlew assembleDebug --console=plain

step "2/6 testDebugUnitTest (94 tests, wedge-fenced)"
timeout 1500 nix develop -c ./gradlew testDebugUnitTest --console=plain

step "3/6 ktlintCheck"
timeout 600 nix develop -c ./gradlew ktlintCheck --console=plain

step "4/6 16 KB page alignment (build-tools 36 zipalign)"
# ANDROID_HOME exists only INSIDE the nix devShell — resolve it through
# the same environment the Gradle steps run in.
sdk_home="$(nix develop -c bash -c 'echo "$ANDROID_HOME"')"
zipalign="$sdk_home/build-tools/36.0.0/zipalign"
if [ ! -x "$zipalign" ]; then
  echo "zipalign not found at $zipalign — is ANDROID_HOME set and the" >&2
  echo "nix devShell active (nix develop)? The -P 16 check needs 36.0.0." >&2
  exit 1
fi
test -f "$apk" || { echo "missing $apk — assembleDebug silently skipped?"; exit 1; }
"$zipalign" -c -P 16 4 "$apk"

step "5/6 contrast-check (both modes, WCAG gate)"
if [ "${SKIP_CONTRAST:-0}" = "1" ]; then
  echo "skipped (SKIP_CONTRAST=1) — only legitimate when no token changed"
else
  python3 scripts/contrast-check.py
fi

step "6/6 dprint check (markdown/yaml/json formatting)"
nix run nixpkgs#dprint -- check

step "GATE GREEN"
echo "assembleDebug + tests + ktlint + zipalign + contrast + dprint: all green."
