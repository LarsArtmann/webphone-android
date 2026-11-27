#!/usr/bin/env python3
"""Test-count honesty: source `@Test` annotations vs executed JUnit cases.

The gate may only quote a count that the JUnit XML proves. This script
refuses two lies: a `@Test` that never executed (mis-filtered, renamed,
wedged before its turn), and a quoted total that no XML holds. Multi-SDK
Robolectric classes run one case per `@Config(sdk = [...])` entry, so the
executed total is legitimately HIGHER than the annotation count; it can
never be lower, and every annotation must map to at least one case.

Exit 0 on an honest suite, 1 on any gap. Prints both counts on success.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_ROOT = ROOT / "app" / "src" / "test"
RESULTS_ROOT = ROOT / "app" / "build" / "test-results" / "testDebugUnitTest"
TEST_ANNOTATION = re.compile(r"^\s*@Test\b", re.MULTILINE)
FUN_NAME = re.compile(r"^\s*(?:private\s+|internal\s+)?fun\s+`?([A-Za-z0-9_ ]+?)`?\s*\(", re.MULTILINE)


def source_tests() -> dict[str, list[str]]:
    """Map each test source file to the names of its @Test functions."""
    tests: dict[str, list[str]] = {}
    for path in sorted(SOURCE_ROOT.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        names: list[str] = []
        for match in TEST_ANNOTATION.finditer(text):
            fun = FUN_NAME.search(text, match.end())
            if fun is None:
                raise SystemExit(f"{path}: @Test with no following fun — parse the file by hand")
            names.append(fun.group(1).strip())
        if names:
            tests[path.relative_to(ROOT).as_posix()] = names
    return tests


def executed_cases() -> dict[str, int]:
    """Map each executed test method name to its case count across SDKs."""
    counts: dict[str, int] = {}
    for report in sorted(RESULTS_ROOT.rglob("TEST-*.xml")):
        for case in ET.parse(report).getroot().iter("testcase"):
            name = case.get("name") or ""
            if name.startswith("[") or not name:
                continue
            counts[name] = counts.get(name, 0) + 1
    return counts


def main() -> int:
    if not RESULTS_ROOT.is_dir():
        print("test-count-check: no JUnit XML under app/build/test-results — did the suite run?", file=sys.stderr)
        return 1
    tests = source_tests()
    executed = executed_cases()
    missing = {
        f"{path}: {name}": None for path, names in tests.items() for name in names if name not in executed
    }
    annotated = sum(len(names) for names in tests.values())
    total = sum(executed.values())
    print(f"tests: {annotated} @Test in source, {total} JUnit cases executed (multi-SDK classes run once per SDK)")
    if missing:
        print("test-count-check: @Test functions with NO executed case:", file=sys.stderr)
        for key in missing:
            print(f"  {key}", file=sys.stderr)
        return 1
    if total < annotated:
        print(f"test-count-check: {annotated} @Test but only {total} cases executed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
