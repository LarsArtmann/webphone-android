#!/usr/bin/env python3
"""WCAG contrast guard over the app's color-token pairs.

Reads the REAL token values from res/values/colors.xml (light) and
res/values-night/colors.xml (dark), then checks every pair in
scripts/contrast-pairs.json. A pair whose tokens exist in both modes is
checked in BOTH modes. Alpha-carrying backgrounds (the floating chrome
glass) are composited over every base listed in "over" and must pass on
the WORST case.

Minimums follow WCAG 2.x: 4.5:1 for text, 3:1 for large text and
non-text UI components (icon glyphs, status dots — 1.4.11).

Exit 0 = every pair passes; exit 1 = any failure or any unresolvable
token (typo guard). Stdlib only; CI runs it on ubuntu-latest.
"""

from __future__ import annotations

import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LIGHT_COLORS = REPO / "app/src/main/res/values/colors.xml"
NIGHT_COLORS = REPO / "app/src/main/res/values-night/colors.xml"
PAIRS = REPO / "scripts/contrast-pairs.json"


def parse_colors(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {el.get("name"): el.text.strip() for el in root.findall("color")}


def channels(hex_value: str) -> tuple[int, int, int, int]:
    """'#RRGGBB' or '#AARRGGBB' -> (a, r, g, b) as 0-255 ints."""
    digits = hex_value.lstrip("#")
    if len(digits) == 6:
        digits = "FF" + digits
    if len(digits) != 8:
        raise ValueError(f"unsupported hex form: {hex_value}")
    return tuple(int(digits[i : i + 2], 16) for i in range(0, 8, 2))  # type: ignore[return-value]


def relative_luminance(argb: tuple[int, int, int, int]) -> float:
    def lin(byte: int) -> float:
        c = byte / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    _, r, g, b = argb
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a: tuple[int, int, int, int], b: tuple[int, int, int, int]) -> float:
    la, lb = relative_luminance(a), relative_luminance(b)
    lighter, darker = max(la, lb), min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)


def composite(
    over: tuple[int, int, int, int], base: tuple[int, int, int, int]
) -> tuple[int, int, int, int]:
    """Alpha-blend `over` onto opaque `base` (straight alpha)."""
    ao, bo = over[0] / 255.0, base[0] / 255.0
    a_out = ao + bo * (1 - ao)
    if a_out == 0:
        return (0, 0, 0, 0)
    out = [round(a_out * 255)]
    for i in range(1, 4):
        out.append(round((over[i] * ao + base[i] * bo * (1 - ao)) / a_out))
    return tuple(out)  # type: ignore[return-value]


def resolve(
    token: str, mode_colors: dict[str, str]
) -> tuple[int, int, int, int] | None:
    if token.startswith("#"):
        return channels(token)
    raw = mode_colors.get(token)
    return channels(raw) if raw is not None else None


def main() -> int:
    light = parse_colors(LIGHT_COLORS)
    dark = parse_colors(NIGHT_COLORS)
    manifest = json.loads(PAIRS.read_text())
    rows: list[tuple[str, str, float, float, bool]] = []
    failures = 0

    for pair in manifest["pairs"]:
        fg_name, min_ratio = pair["fg"], float(pair["min"])
        bg_names = pair["bg"] if isinstance(pair["bg"], list) else [pair["bg"]]
        over_names = pair.get("over", [])
        for mode, mode_colors in (("light", light), ("dark", dark)):
            fg = resolve(fg_name, mode_colors)
            if fg is None:
                continue  # token not themed in this mode
            bases: list[tuple[int, int, int, int]] = []
            for bg_name in bg_names:
                bg = resolve(bg_name, mode_colors)
                if bg is None:
                    continue
                if bg[0] != 255 and not over_names:
                    raise SystemExit(
                        f"ERROR: {bg_name} carries alpha but pair has no 'over' bases"
                    )
                if over_names:
                    for over_name in over_names:
                        base = resolve(over_name, mode_colors)
                        if base is None:
                            continue
                        bases.append(composite(bg, base))
                else:
                    bases.append(bg)
            if not bases:
                continue
            # Translucent fg: WCAG compares the BLENDED fg against the base.
            effective = [(composite(fg, b) if fg[0] != 255 else fg, b) for b in bases]
            worst = min(contrast(f, b) for f, b in effective)
            ok = worst >= min_ratio
            failures += 0 if ok else 1
            label = f"{fg_name} on {'/'.join(bg_names)}"
            if over_names:
                label += f" over {'+'.join(over_names)}"
            rows.append((mode, label, worst, min_ratio, ok))

    defined = set(light) | set(dark)
    for pair in manifest["pairs"]:
        names = [pair["fg"]] + (
            pair["bg"] if isinstance(pair["bg"], list) else [pair["bg"]]
        )
        names += pair.get("over", [])
        for name in names:
            if not name.startswith("#") and name not in defined:
                print(f"ERROR: token {name} resolves nowhere (typo?)")
                return 1

    w_mode, w_pair = 5, max(len(r[1]) for r in rows) + 2
    for mode, label, ratio, min_ratio, ok in rows:
        verdict = "PASS" if ok else "FAIL"
        print(
            f"{mode:<{w_mode}}  {label:<{w_pair}} {ratio:5.2f}:1  (min {min_ratio:.1f})  {verdict}"
        )
    print(f"\n{len(rows)} checks, {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
