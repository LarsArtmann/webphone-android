#!/usr/bin/env bash
# Pin both devShells in the nix store with GC roots.
#
# A store GC otherwise collects the Android SDK and JDK that only
# `nix develop` referenced, and the next session pays minutes of
# re-downloads (2026-11-10). Building each shell as a derivation and
# keeping the output linked under `result-*` gives the collector a
# root: the output's runtime closure is exactly the shell's tools
# (JDK, SDK, emulator, system image).
#
# Run after `nix flake update` (the roots then track the new lock).
# The links are gitignored (`result*`); they are per-checkout roots,
# not repo content.
#
# Usage: bash scripts/pin-devshells.sh
set -euo pipefail
cd "$(dirname "$0")/.."

system="$(nix eval --raw --impure --expr 'builtins.currentSystem')"
for shell in default emulator; do
    link="result-${shell/default/devshell}"
    nix build --out-link "./$link" ".#devShells.$system.$shell"
    closure="$(nix path-info -r "./$link" | wc -l)"
    echo "$shell: pinned as ./$link ($closure store paths stay while the link exists)"
done
