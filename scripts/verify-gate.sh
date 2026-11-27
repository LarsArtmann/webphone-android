#!/usr/bin/env bash
# The verification gate, wrapped (SUPERB plan J06/M026).
#
# One command that refuses to start dirty (a concurrent Gradle daemon or
# a second checkout's build poisons timings and locks), then runs every
# gate the AGENTS "Build and verify" section demands, in order, loudly:
#
#   assembleDebug -> testDebugUnitTest -> ktlintCheck -> Android Lint
#   -> 16 KB zipalign -> abi-check -> contrast-check -> dprint check
#
# Android Lint (since 2026-11-25): errors AND warnings fail the gate.
# The version checks are demoted to informational in app/build.gradle.kts
# and the step prints them as a version-lag report instead, so an
# upstream release never turns this gate red (Dependabot cannot carry
# the signal here, the push mirror deletes its branches).
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

step "1/8 assembleDebug"
timeout 900 nix develop -c ./gradlew assembleDebug --console=plain

step "2/8 testDebugUnitTest (wedge-fenced)"
timeout 1500 nix develop -c ./gradlew testDebugUnitTest --console=plain
# Test-count honesty: every @Test in source must appear as an executed
# JUnit case, and the XML is the only count the gate may quote.
python3 scripts/test-count-check.py

step "3/8 ktlintCheck + KGP parity"
timeout 600 nix develop -c ./gradlew ktlintCheck kgpParityCheck --console=plain

step "4/8 Android Lint (errors and warnings fatal) + version-lag report"
# Lint's own task fails on any error and, through warningsAsErrors in
# app/build.gradle.kts, on any warning. The lag report then reprints the
# informational version findings and refuses a report that still holds
# an error or a warning (belt over abortOnError).
timeout 900 nix develop -c ./gradlew :app:lintDebug --console=plain
python3 scripts/lint-lag-report.py

step "5/8 16 KB page alignment (build-tools 36 zipalign)"
# ANDROID_HOME exists only INSIDE the nix devShell — resolve it through
# the same environment the Gradle steps run in.
sdk_home="$(nix develop -c printenv ANDROID_HOME)"
zipalign="$sdk_home/build-tools/36.0.0/zipalign"
if [ ! -x "$zipalign" ]; then
    echo "zipalign not found at $zipalign — is ANDROID_HOME set and the" >&2
    echo "nix devShell active (nix develop)? The -P 16 check needs 36.0.0." >&2
    exit 1
fi
test -f "$apk" || {
    echo "missing $apk — assembleDebug silently skipped?"
    exit 1
}
"$zipalign" -c -P 16 4 "$apk"

step "6/8 ABI completeness (the APK stays universal)"
# One APK serves every device class; a dependency swap or stray
# abiFilters would shrink lib/ silently — refuse that here, at the
# exact artifact that ships.
python3 scripts/abi-check.py "$apk"

step "7/8 contrast-check (both modes, WCAG gate)"
if [ "${SKIP_CONTRAST:-0}" = "1" ]; then
    echo "skipped (SKIP_CONTRAST=1) — only legitimate when no token changed"
else
    python3 scripts/contrast-check.py
fi

step "8/8 dprint check (markdown/yaml/json formatting)"
nix run nixpkgs#dprint -- check

step "9/9 script lint (bash -n, py_compile, shellcheck)"
# The release lane and the gate itself are shell: a syntax slip must
# fail HERE, not at the first `bash scripts/release.sh` on a phone day.
for script in scripts/*.sh gradlew; do bash -n "$script"; done
python3 -m py_compile scripts/*.py
if command -v shellcheck >/dev/null 2>&1; then
    shellcheck scripts/*.sh
else
    echo "WARNING: shellcheck not on PATH — shell lint skipped (the nix devShell carries it)"
fi
# The release lane's forge client, against an in-process mock forge.
python3 scripts/test-forgejo-release.py
# Evidence links: every report/plan a TODO_LIST row cites must exist.
python3 scripts/evidence-lint.py

sha="$(git rev-parse --short HEAD)"
dirty="$(git status --porcelain | wc -l)"
printf 'last gate: GREEN at %s on %s (%s uncommitted path(s) on top of it)\n' \
    "$sha" "$(date '+%Y-%m-%d %H:%M %Z')" "$dirty" >.gate-last-green

step "GATE GREEN"
echo "assembleDebug + tests + ktlint + lint + zipalign + abi + contrast + dprint: all green."
