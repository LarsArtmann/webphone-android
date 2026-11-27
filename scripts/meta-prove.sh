#!/usr/bin/env bash
# meta-prove.sh — prove a pin can fail: break the fix, watch the pin go
# RED, restore, watch it go GREEN. A pin that passes with its fix removed
# pins nothing (the vacuous-pin lesson, 2026-10-28), so every new pin is
# run through this before it is trusted.
#
#   bash scripts/meta-prove.sh --tests '*CallServiceTest' \
#       --break-cmd "sed -i '218s/.*/\/\/ broken/' app/src/main/.../CallEngine.kt" \
#       --restore-cmd "sed -i '218s/.*/            if (...) return/' app/src/main/.../CallEngine.kt"
#
# The two commands MUST be inverses. The break and the restore run in a
# THROWAWAY COPY of the working tree (never the checkout itself): the
# auto-commit daemon commits any modified file within minutes, so a
# proof in place could sweep the deliberately broken line into history
# (commit 7879ec6 is that accident). The commands therefore see the same
# relative paths, in the copy. The restore runs on EVERY exit (trap) and
# the copy is deleted afterwards; the named paths' content fingerprint is
# compared before and after, so a restore that does not put the bytes
# back stops the proof. Nothing in the checkout is ever modified.
#
set -euo pipefail

tests=""
break_cmd=""
restore_cmd=""
paths=""
while [ "$#" -gt 0 ]; do
    case "$1" in
    --tests)
        tests="$2"
        shift 2
        ;;
    --break-cmd)
        break_cmd="$2"
        shift 2
        ;;
    --restore-cmd)
        restore_cmd="$2"
        shift 2
        ;;
    --paths)
        paths="$2"
        shift 2
        ;;
    *)
        echo "unknown argument: $1" >&2
        exit 2
        ;;
    esac
done
[ -n "$tests" ] && [ -n "$break_cmd" ] && [ -n "$restore_cmd" ] || {
    echo "usage: meta-prove.sh --tests <filter> --break-cmd <cmd> --restore-cmd <cmd> --paths <files>" >&2
    exit 2
}

root="$(cd "$(dirname "$0")/.." && pwd)"
[ -n "$paths" ] || {
    echo "--paths is required: the proof fingerprints exactly the files the two commands touch" >&2
    exit 2
}

if pgrep -f 'Gradle[W]orkerMain' >/dev/null; then
    echo "another Gradle process is alive; not starting a concurrent build" >&2
    exit 1
fi

work="$(mktemp -d /tmp/webphone-meta-prove.XXXXXX)"
cleanup() {
    if command -v trash >/dev/null 2>&1; then
        trash "$work" >/dev/null 2>&1 || true
    else
        echo "no trash binary: the proof copy stays at $work (tmpfs; reboot clears it)"
    fi
}
trap cleanup EXIT INT TERM
rsync -a --exclude=.git --exclude=.gradle --exclude=.kotlin --exclude=.avd \
    --exclude=__pycache__ --exclude=build "$root/" "$work/"
cd "$work"

snapshot() {
    # shellcheck disable=SC2086  # several paths, split on purpose
    cat $paths | sha256sum
}
restore() { eval "$restore_cmd"; }

run_pin() {
    nix develop -c ./gradlew :app:testDebugUnitTest --tests "$tests" --console=plain -q >/dev/null 2>&1
}

before=$(snapshot)
eval "$break_cmd"
if run_pin; then
    echo "VACUOUS: the pin passes with the fix broken ($tests)" >&2
    exit 1
fi
echo "RED on broken: ok"

restore
if [ "$(snapshot)" != "$before" ]; then
    echo "RESTORE INCOMPLETE: the copy differs after --restore-cmd; the two commands are not inverses" >&2
    exit 1
fi
if run_pin; then
    echo "GREEN on restored: ok — the pin is proven ($tests)"
else
    echo "RED on restored: the pin does not pass with the fix in place" >&2
    exit 1
fi
