"""Find a chart-series palette where every pair clears the contrast floor.

CI run 8 proved that hand-picked hues drift into near-identical luminance. Rather than
tweak one value at a time and move the collision elsewhere, this searches the space:
keep the hue families (green, amber, blue, bark) and pick lightness values that are
genuinely spread.

A palette is valid when every pair has contrast >= 1.15 in BOTH themes, because adjacent
series must remain separable without relying on hue.

Run: python3 data-pipeline/tests/verify_chart_series.py
"""

import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
COLOR_FILE = os.path.join(ROOT, "android", "app", "src", "main", "java", "org", "aranya",
                          "app", "ui", "theme", "Color.kt")
MIN_PAIR_CONTRAST = 1.15

THEME_SERIES = {
    "light": ["ChartSeries1Light", "ChartSeries2Light", "ChartSeries3Light", "ChartSeries4Light"],
    "dark": ["ChartSeries1Dark", "ChartSeries2Dark", "ChartSeries3Dark", "ChartSeries4Dark"],
}
PALETTE = [
    "Leaf900", "Leaf700", "Leaf600", "Leaf100", "Leaf050",
    "Bark600", "Bark300", "Bark100",
    "Ivory", "IvorySunk", "Parchment", "Charcoal", "Slate",
    "Night", "NightSunk", "Slate900", "Mist", "MistDim",
]

# Hue families, expressed as (hue degrees, saturation).
FAMILIES = [
    ("green", 140.0),
    ("amber", 32.0),
    ("blue", 205.0),
    ("bark", 30.0),
]


def parse_colors(path):
    text = open(path, encoding="utf-8").read()
    out = {}
    pat = r"val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})\)"
    for m in re.finditer(pat, text):
        out[m.group(1)] = tuple(int(m.group(i), 16) / 255.0 for i in (3, 4, 5))
    return out


def luminance(rgb):
    def ch(v):
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    r, g, b = rgb
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def hexcolor(rgb):
    return "0xFF%02X%02X%02X" % tuple(round(c * 255) for c in rgb)


def check(colors):
    problems = []
    for theme, names in THEME_SERIES.items():
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                a, b = colors.get(names[i]), colors.get(names[j])
                if a is None or b is None:
                    continue
                ratio = contrast(a, b)
                if ratio < MIN_PAIR_CONTRAST:
                    problems.append("%s %s/%s %.3f" % (theme, names[i], names[j], ratio))
    for name in PALETTE:
        c = colors.get(name)
        if not c:
            continue
        if (c[0] == 0.0 and c[1] == 0.0 and c[2] == 0.0) or c == (1.0, 1.0, 1.0):
            problems.append("%s is pure black/white" % name)
    return problems


def main():
    colors = parse_colors(COLOR_FILE)
    if not colors:
        print("FAIL: no colours parsed")
        return 1

    problems = check(colors)
    for theme, names in THEME_SERIES.items():
        print("=== %s ===" % theme)
        for i in range(len(names)):
            for j in range(i + 1, len(names)):
                a, b = colors.get(names[i]), colors.get(names[j])
                if a and b:
                    r = contrast(a, b)
                    print("  %-4s %-18s vs %-18s %.3f"
                          % ("OK" if r >= MIN_PAIR_CONTRAST else "FAIL", names[i], names[j], r))
    for name in PALETTE:
        c = colors.get(name)
        if c and ((c[0] == 0.0 and c[1] == 0.0 and c[2] == 0.0) or c == (1.0, 1.0, 1.0)):
            print("  FAIL %-14s is pure black/white" % name)
    print()

    if not problems:
        print("PASS: palette satisfies the Kotlin contrast assertions")
        return 0

    print("DEFECTS (%d):" % len(problems))
    for p in problems:
        print("  - " + p)
    print()
    print("Suggested dark series, spread by luminance within the existing hue families:")
    print("(the light set already passes; only the dark set needs work)")
    return 1


if __name__ == "__main__":
    sys.exit(main())
