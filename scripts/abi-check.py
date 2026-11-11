#!/usr/bin/env python3
"""ABI completeness gate for the universal APK.

The release lane ships ONE APK to every device class (no splits, no
abiFilters — Obtainium needs no filter because the artifact is
universal): phones are arm64-v8a, emulators and Chromebook-class
hardware are x86/x86_64, older devices armeabi-v7a. The stream-webrtc
AAR carries all four; a dependency swap, a stray abiFilters, or an
R8/packaging regression would silently shrink that set, and the loss
only surfaces as a crash on SOMEONE ELSE'S device (an x86_64
Chromebook installing an arm-only APK gets an unusable app, not a
build error). This gate turns that into a red build at the exact
artifact that ships: it opens the APK, lists the lib/<abi>/ dirs that
actually made it in, and refuses anything short of the full set.

Run by verify-gate.sh (debug APK), release.sh (signed APK), and CI
(both artifacts). Stdlib only — same constraint as contrast-check.py.

Usage: abi-check.py <apk>
Exit: 0 all ABIs present, 1 at least one missing, 2 usage error.
"""

import sys
import zipfile

REQUIRED_ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: abi-check.py <apk>", file=sys.stderr)
        return 2

    apk_path = sys.argv[1]
    with zipfile.ZipFile(apk_path) as apk:
        present = {
            entry.split("/")[1]
            for entry in apk.namelist()
            if entry.startswith("lib/") and entry.count("/") >= 2
        }

    missing = [abi for abi in REQUIRED_ABIS if abi not in present]
    if missing:
        print(
            f"ABI GATE RED: {apk_path} is missing native libs for "
            f"{', '.join(missing)} (found only: {', '.join(sorted(present)) or 'none'}). "
            "The APK must stay universal — check the webrtc dependency "
            "and any abiFilters/packaging changes.",
            file=sys.stderr,
        )
        return 1

    print(f"ABI gate green: {', '.join(REQUIRED_ABIS)} all present in {apk_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
