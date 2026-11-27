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
# The two commands MUST be inverses. The restore runs on EVERY exit
# (trap), so an interrupted run cannot leave a broken tree behind; the
# script then re-runs the pin and refuses a GREEN-on-broken or
# RED-on-restored verdict. The tree's content fingerprint (the named
# paths, or the whole diff against HEAD) is compared before and after,
# so a restore that does not put the bytes back stops the proof.
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
    echo "usage: meta-prove.sh --tests <filter> --break-cmd <cmd> --restore-cmd <cmd> [--paths <files>]" >&2
    exit 2
}

cd "$(dirname "$0")/.."
if pgrep -f 'Gradle[W]orkerMain' >/dev/null; then
    echo "another Gradle process is alive; not starting a concurrent build" >&2
    exit 1
fi

snapshot() {
    if [ -n "$paths" ]; then
        # shellcheck disable=SC2086  # several paths, split on purpose
        cat $paths | sha256sum
    else
        git diff HEAD | sha256sum
    fi
}
restore() { eval "$restore_cmd"; }
trap restore EXIT INT TERM

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
trap - EXIT INT TERM
if [ "$(snapshot)" != "$before" ]; then
    echo "RESTORE INCOMPLETE: the tree differs after --restore-cmd; fix by hand" >&2
    exit 1
fi
if run_pin; then
    echo "GREEN on restored: ok — the pin is proven ($tests)"
else
    echo "RED on restored: the pin does not pass with the fix in place" >&2
    exit 1
fi
