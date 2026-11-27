#!/usr/bin/env python3
"""Version-lag report: the visible half of the Android Lint gate.

Android Lint's three version checks (GradleDependency,
AndroidGradlePluginVersion, NewerVersionAvailable) and OldTargetApi are
demoted to informational in app/build.gradle.kts, so a new upstream
release can never fail the gate on someone else's release day.
Informational findings still land in the lint text report as "Hint:"
lines; this script reprints exactly those as the gate's version-lag
report. It replaces the signal Dependabot cannot deliver on this repo:
the Forgejo-to-GitHub push mirror deletes every branch Dependabot opens
(AGENTS.md, "Repository & mirrors").

Exit 0 when the report exists and holds no error and no warning (a lag
is information, not a failure). Exit 1 when the report is missing, has
no summary line, or holds an error or a warning: a gate that reports
nothing is not a gate.

Stdlib only; scripts/verify-gate.sh and CI run it right after
:app:lintDebug.

Usage: lint-lag-report.py [report]
       (default report: app/build/reports/lint-results-debug.txt)
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
DEFAULT_REPORT = REPO / "app/build/reports/lint-results-debug.txt"

# A finding line is the only unindented "location: Severity: message
# [CheckId]" line of its block; everything beneath it is explanation.
FINDING = re.compile(
    r"^(?P<location>.+?): (?P<severity>Fatal|Error|Warning|Hint|Information): (?P<message>.*) \[(?P<check>[A-Za-z0-9]+)\]$"
)
SUMMARY = re.compile(r"^(\d+) errors?, (\d+) warnings?(?:, (\d+) hints?)?$")

BLOCKING = ("Fatal", "Error", "Warning")


def main(argv: list[str]) -> int:
    report = Path(argv[1]) if len(argv) > 1 else DEFAULT_REPORT
    if not report.is_file():
        print(f"lint-lag-report: no lint text report at {report}", file=sys.stderr)
        print("Run :app:lintDebug first; it always writes the report.", file=sys.stderr)
        return 1

    lines = report.read_text(encoding="utf-8").splitlines()
    findings = []
    summary: tuple[int, int, int] | None = None
    for line in lines:
        if not line.strip():
            continue
        summary_match = SUMMARY.match(line)
        if summary_match:
            summary = (
                int(summary_match.group(1)),
                int(summary_match.group(2)),
                int(summary_match.group(3) or 0),
            )
            continue
        if line[0].isspace():
            continue
        # Everything else at column 0 is a finding line or the source
        # excerpt lint prints beneath one; only the former matches.
        finding = FINDING.match(line)
        if finding is not None:
            findings.append(finding)

    if summary is None:
        print(
            "lint-lag-report: the report carries no '<n> errors, <n> warnings' summary line",
            file=sys.stderr,
        )
        return 1

    errors, warnings, hints = summary
    print(f"Android Lint: {errors} errors, {warnings} warnings, {hints} hint(s)")
    if errors or warnings:
        print(
            "lint-lag-report: the report holds an error or a warning; the lint task should have failed on it",
            file=sys.stderr,
        )
        return 1

    lag = [f for f in findings if f["severity"] in ("Hint", "Information")]
    strays = [f for f in findings if f["severity"] in BLOCKING]
    if strays:
        print(
            "lint-lag-report: findings above hint severity with a zero summary; refusing to pass",
            file=sys.stderr,
        )
        return 1

    if not lag:
        print(
            "Version lag: none. Dependencies, the Gradle wrapper, AGP and targetSdk are at their newest releases."
        )
        return 0

    print(
        "Version lag (deliberate, non-fatal; see the lint block in app/build.gradle.kts):"
    )
    for finding in lag:
        location = finding["location"]
        try:
            location = str(Path(location).relative_to(REPO))
        except ValueError:
            pass
        print(f"  - {location} [{finding['check']}] {finding['message']}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
