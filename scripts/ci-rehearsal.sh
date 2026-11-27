#!/usr/bin/env bash
# Rehearse the CI workflow locally, in a clean checkout.
#
# The workflow (`.github/workflows/ci.yml`) has never executed on
# GitHub (billing), so this script is the proof that its steps run:
# it clones the COMMITTED tree into a throwaway directory (no build
# outputs, no configuration cache, no signing properties), then runs
# every command the workflow runs, in its order. Uncommitted work is
# NOT rehearsed: commit first.
#
# CI's JDK/SDK come from GitHub actions; here the same tools come from
# the nix devShell, and dprint from nixpkgs (CI pins the same version).
#
# Usage: bash scripts/ci-rehearsal.sh [--keep]
#   --keep   leave the clone behind (printed) for inspection
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"

keep=0
[ "${1:-}" = "--keep" ] && keep=1
if pgrep -f 'Gradle[W]orkerMain' >/dev/null; then
    echo "ci-rehearsal: a Gradle build is running; not starting a concurrent one" >&2
    exit 1
fi

clone="$(mktemp -d /tmp/webphone-ci-rehearsal.XXXXXX)"
cleanup() {
    if [ "$keep" = "1" ]; then
        echo "ci-rehearsal: clone kept at $clone"
    elif command -v trash >/dev/null 2>&1; then
        trash "$clone" >/dev/null 2>&1 || true
    else
        echo "ci-rehearsal: no trash binary, clone stays at $clone"
    fi
}
trap cleanup EXIT

git clone --quiet "$root" "$clone"
cd "$clone"
echo "ci-rehearsal: HEAD $(git rev-parse --short HEAD), $(git status --porcelain | wc -l) dirty paths"

# CI has no release key: the release artifact must build UNSIGNED.
export WEBPHONE_RELEASE_PROPERTIES="$clone/no-signing-properties"
g() {
    echo "--- $*"
    timeout 1500 nix develop -c "$@"
}

g ./gradlew assembleDebug --console=plain
g ./gradlew testDebugUnitTest --console=plain
python3 scripts/test-count-check.py
g ./gradlew ktlintCheck kgpParityCheck --console=plain
g ./gradlew :app:lintDebug --console=plain
python3 scripts/lint-lag-report.py
zipalign="$(nix develop -c printenv ANDROID_HOME)/build-tools/36.0.0/zipalign"
"$zipalign" -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
python3 scripts/abi-check.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/contrast-check.py
g ./gradlew assembleRelease --console=plain
[ -f app/build/outputs/apk/release/app-release-unsigned.apk ] || {
    echo "ci-rehearsal: expected an UNSIGNED release APK (CI has no key)" >&2
    exit 1
}
"$zipalign" -c -P 16 4 app/build/outputs/apk/release/app-release-unsigned.apk
python3 scripts/abi-check.py app/build/outputs/apk/release/app-release-unsigned.apk
nix run nixpkgs#dprint -- check
for script in scripts/*.sh gradlew; do bash -n "$script"; done
python3 -m py_compile scripts/*.py
echo "CI REHEARSAL GREEN: every workflow step ran in a clean checkout"
