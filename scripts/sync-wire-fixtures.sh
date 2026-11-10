#!/usr/bin/env bash
# sync-wire-fixtures.sh — re-vendor the webphone wire fixtures (SUPERB
# android-wire-contract-sync, a1.1).
#
# The server repo renders byte-pinned consumer fixtures from its own
# types (webphone: `webphone contracts` writes docs/contracts/wire/).
# This script copies them into the JVM suite's test resources, where
# WireContractTest decodes each through the app's REAL parsers — a
# renamed key on either side reds the test, never the first device
# boot.
#
# Usage:
#   scripts/sync-wire-fixtures.sh            # copy (idempotent)
#   scripts/sync-wire-fixtures.sh --check    # diff only; exit 1 on drift
#   WEBPHONE_DIR=/path/to/webphone scripts/sync-wire-fixtures.sh
#
# The CHECK mode is the drift gate: run it whenever webphone's
# contracts change (the webphone runbook's re-vendor leg carries the
# same ritual). A red --check means the vendored contract is stale —
# re-run without --check, then make the app-side parsers follow the
# NEW contract (or report the drift if the change was not deliberate).
set -euo pipefail
cd "$(dirname "$0")/.."

webphone_dir="${WEBPHONE_DIR:-../webphone}"
src="$webphone_dir/docs/contracts/wire"
dst="app/src/test/resources/wire"

if [ ! -d "$src" ]; then
    echo "ERROR: no fixture source at $src" >&2
    echo "Set WEBPHONE_DIR to a webphone checkout that ran 'webphone contracts'." >&2
    exit 1
fi

mode="${1:-copy}"
case "$mode" in
    --check)
        # diff against a fresh copy so the verdict covers MISSING and
        # EXTRA vendored files too, not just byte drift (a removed
        # server fixture must not linger here as a dead contract).
        tmp="$(mktemp -d)"
        trap 'rm -rf "$tmp"' EXIT
        mkdir -p "$tmp/wire"
        cp "$src"/*.json "$tmp/wire/"
        if [ ! -d "$dst" ]; then
            echo "DRIFT: $dst does not exist yet — run scripts/sync-wire-fixtures.sh" >&2
            exit 1
        fi
        if ! diff -ru "$dst" "$tmp/wire"; then
            echo "DRIFT: vendored wire fixtures differ from $src" >&2
            echo "Re-run without --check to re-vendor, then follow the new contract." >&2
            exit 1
        fi
        echo "wire fixtures fresh ($(ls "$dst" | wc -l) files match $src)"
        ;;
    copy)
        mkdir -p "$dst"
        # Trash-and-copy keeps the destination EXACTLY the source: a
        # renamed/removed server fixture never lingers as a dead
        # contract (find -delete, not rm -r, per the trash rule the
        # sweep scripts use for generated trees).
        find "$dst" -name '*.json' -delete
        cp "$src"/*.json "$dst/"
        echo "vendored $(ls "$dst" | wc -l) wire fixtures from $src into $dst"
        ;;
    *)
        echo "usage: $0 [--check]" >&2
        exit 2
        ;;
esac
