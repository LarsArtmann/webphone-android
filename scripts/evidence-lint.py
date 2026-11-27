#!/usr/bin/env python3
"""Evidence-link lint for TODO_LIST.md.

Every Ready/High/Medium/Low row cites where it came from. Citations rot
silently: a report gets archived, a plan id is mistyped, a filename is
shortened with an ellipsis. This lint refuses the two rots a machine can
see:

1. A cited report does not resolve. Bare `HH-MM` tokens (the short form,
   e.g. `23-02 §e3`) must be the time suffix of a report or plan under
   docs/status/ or docs/planning/ (live or archived); `docs/status/...`
   paths must prefix-match a real file (the `…` ellipsis style is
   allowed). Full timestamps are skipped: they also name OTHER repos'
   plans, which this repo cannot see.
2. A cited plan id does not exist. `Mnnn` / `Jnn` / `Fnnn` tokens must
   appear in a file under docs/planning/.

Exit 1 lists every bad cite.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TODO = ROOT / "TODO_LIST.md"
STATUS = ROOT / "docs" / "status"
PLANNING = ROOT / "docs" / "planning"

TIME_TOKEN = re.compile(r"(?<![\w-])(\d{2}-\d{2})(?![\w-])")
FULL_TIMESTAMP = re.compile(r"\d{4}-\d{2}-\d{2}_\d{2}-\d{2}")
STATUS_PATH = re.compile(r"docs/status/([\d-]+_\d\d-\d\d)[^\s|`]*")
PLAN_ID = re.compile(r"\b([MJ]\d{1,3}|F\d{1,3})\b")
VERSION_LIKE = re.compile(r"^\d+\.\d+$")  # a version like 0.64 is not a report time


def report_times() -> set[str]:
    """HH-MM suffixes of every report AND plan, live or archived."""
    times: set[str] = set()
    for path in list(STATUS.rglob("*.md")) + list(PLANNING.rglob("*.md")):
        match = re.match(r"\d{4}-\d{2}-\d{2}_(\d{2}-\d{2})", path.name)
        if match:
            times.add(match.group(1))
    return times


def report_prefixes() -> list[str]:
    names: list[str] = []
    for path in STATUS.rglob("*.md"):
        match = re.match(r"\d{4}-\d{2}-\d{2}_\d{2}-\d{2}", path.name)
        if match:
            names.append(match.group(0))
    return names


def plan_ids() -> set[str]:
    ids: set[str] = set()
    for path in PLANNING.rglob("*.md"):
        ids.update(match.rstrip(".,;:") for match in PLAN_ID.findall(path.read_text(encoding="utf-8")))
    return ids


def rows() -> list[tuple[int, str]]:
    found: list[tuple[int, str]] = []
    in_table = False
    for number, line in enumerate(TODO.read_text(encoding="utf-8").splitlines(), start=1):
        if line.startswith("| Task") or line.startswith("| ---"):
            in_table = True
            continue
        if in_table and line.startswith("|"):
            found.append((number, line))
        elif in_table and not line.startswith("|"):
            in_table = False
    return found


def main() -> int:
    times = report_times()
    prefixes = report_prefixes()
    ids = plan_ids()
    problems: list[str] = []
    checked = 0
    for number, row in rows():
        cells = row.split("|")[1:-1]
        task = cells[0].strip() if cells else ""
        evidence = cells[-1].strip() if len(cells) > 1 else ""
        cited = False
        bare = FULL_TIMESTAMP.sub(" ", evidence)
        for token in TIME_TOKEN.findall(bare):
            if VERSION_LIKE.match(token):
                continue
            if token not in times:
                problems.append(f"TODO_LIST.md:{number}: cites report '{token}', no such report under docs/status/")
            cited = True
        for prefix in STATUS_PATH.findall(evidence):
            if not any(name.startswith(prefix) for name in prefixes):
                problems.append(f"TODO_LIST.md:{number}: cites docs/status/{prefix}…, no file starts with it")
            cited = True
        for plan_id in PLAN_ID.findall(evidence):
            if plan_id not in ids:
                problems.append(f"TODO_LIST.md:{number}: cites plan id '{plan_id}', absent from docs/planning/")
            cited = True
        checked += 1
    print(f"evidence-lint: {checked} TODO rows checked, {len(problems)} broken cite(s)")
    for problem in problems:
        print(f"  {problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
